package yz.tuzak.beyin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import yz.tuzak.Mudahale;
import yz.tuzak.Oyun;
import yz.tuzak.YzPlugin;
import yz.tuzak.aday.Aday;
import yz.tuzak.aday.AdayUretici;
import yz.tuzak.tuzak.AktifTuzak;
import yz.tuzak.tuzak.IzinliTarifler;
import yz.tuzak.tuzak.Modul;
import yz.tuzak.tuzak.ModulKaydi;
import yz.tuzak.tuzak.Tarif;
import yz.tuzak.tuzak.Yerlestirici;

/**
 * Yapay zekanin karar dongusu: duzenli ve olay bazli. LLM cagrisi asenkron, plugin hic beklemez.
 * Kararlar ve girdiler sadece log dosyasina gider; konsola/chat'e hicbir sey basilmaz.
 */
public final class DusunmeDongusu {

    /** O anki durum raporundan turetilen, karar uygulanirken gereken veriler. */
    private static final class Baglam {
        Map<String, Object> durum;
        final Map<String, Aday> adaylar = new HashMap<>();
        final Set<String> izinli = new HashSet<>();
        final Set<String> yemler = new HashSet<>();
        String neden;
    }

    private final YzPlugin plugin;
    private final Oyun oyun;
    private final BeyinIstemci beyin;
    private final ModulKaydi moduller;
    private final Yerlestirici yerlestirici;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    private final Mudahale mudahale;
    private boolean bekleyen;
    private double sonDusunmeT = -1e9;

    public DusunmeDongusu(YzPlugin plugin, Oyun oyun, BeyinIstemci beyin, ModulKaydi moduller, Yerlestirici yerlestirici) {
        this.plugin = plugin;
        this.oyun = oyun;
        this.beyin = beyin;
        this.moduller = moduller;
        this.yerlestirici = yerlestirici;
        this.mudahale = new Mudahale(plugin, oyun);
    }

    public Mudahale mudahale() {
        return mudahale;
    }

    public void sifirla() {
        bekleyen = false;
        sonDusunmeT = 0;
        mudahale.sifirla();
    }

    /** Her saniye. */
    public void tik() {
        if (oyun.aktif() && oyun.hedef() != null && oyun.hedef().isOnline()) mudahale.tik(oyun.hedef());
        double t = oyun.sure();
        if (t - sonDusunmeT >= plugin.getConfig().getDouble("dusunme_araligi_sn")) dusun("duzenli");
    }

    /** Olay kaydi geldiginde. */
    public void olay(String tip, Map<String, Object> detay, boolean debounceGecti) {
        if (!debounceGecti || !oyun.aktif()) return;
        if (!plugin.getConfig().getStringList("dusunme.tetikleyici_olaylar").contains(tip)) return;
        if (tip.equals("yapi_yakin") && !yapiIlgili(String.valueOf(detay.get("yapi")))) return;
        if (oyun.sure() - sonDusunmeT < plugin.getConfig().getDouble("dusunme.min_aralik_sn")) return;
        dusun("olay:" + tip);
    }

    private boolean yapiIlgili(String ad) {
        for (String k : plugin.getConfig().getStringList("dusunme.yapi_tetikleyici")) if (ad.contains(k)) return true;
        return false;
    }

    // ---------- istek ----------

    private void dusun(String neden) {
        if (bekleyen || !oyun.aktif() || !oyun.hedef().isOnline()) return;
        bekleyen = true;
        sonDusunmeT = oyun.sure();
        Baglam b;
        try {
            b = baglamKur(neden);
        } catch (RuntimeException e) {
            bekleyen = false;
            logla(neden, 1, null, null, 0, "durum_hatasi:" + e.getClass().getSimpleName());
            return;
        }
        gonder(b, null, 1);
    }

    private void gonder(Baglam b, String hata, int deneme) {
        long bas = System.currentTimeMillis();
        beyin.karar(b.durum, hata).whenComplete((karar, istisna) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    long sure = System.currentTimeMillis() - bas;
                    if (!oyun.aktif()) {
                        bekleyen = false;
                        return;
                    }
                    if (istisna != null || karar == null) {
                        bekleyen = false;
                        logla(b.neden, deneme, b, null, sure, "beyin_ulasilamadi");
                        return;
                    }
                    uygula(b, karar, deneme, sure);
                }));
    }

    // ---------- uygulama ----------

    private void uygula(Baglam b, JsonObject karar, int deneme, long sure) {
        String hata = null;
        String tuzakId = null;
        try {
            JsonObject konusma = karar.has("konusma") && karar.get("konusma").isJsonObject() ? karar.getAsJsonObject("konusma") : null;
            if (konusma != null && bool(konusma, "konus") && !str(konusma, "metin").isEmpty()) {
                oyun.konus(str(konusma, "metin"));
            }
            JsonObject hamle = karar.getAsJsonObject("hamle");
            if (hamle != null && "tuzak".equals(str(hamle, "tur"))) {
                Object sonuc = tuzakKur(b, karar, hamle);
                if (sonuc instanceof String s) hata = s;
                else tuzakId = ((AktifTuzak) sonuc).id;
            } else if (hamle != null && "mudahale".equals(str(hamle, "tur"))) {
                String cesit = str(hamle, "cesit");
                hata = mudahale.uygula(cesit, oyun.hedef());
                if (hata == null) tuzakId = "mudahale:" + cesit;
            }
        } catch (RuntimeException e) {
            hata = "uygulama_hatasi:" + e.getClass().getSimpleName();
        }
        logla(b.neden, deneme, b, karar, sure, hata == null ? (tuzakId != null ? (tuzakId.startsWith("mudahale:") ? tuzakId : "kuruldu:" + tuzakId) : "bekle") : "reddedildi:" + hata);
        if (hata != null && deneme == 1) {
            gonder(b, "Hamlen uygulanamadi (" + hata + "). Baska bir aday ya da tarif sec, ya da bekle.", 2);
            return;
        }
        bekleyen = false;
    }

    /** @return kurulan AktifTuzak ya da hata mesaji (String) */
    private Object tuzakKur(Baglam b, JsonObject karar, JsonObject hamle) {
        Player p = oyun.hedef();
        Aday a = b.adaylar.get(str(hamle, "aday"));
        if (a == null) return "aday_yok";
        JsonObject tr = hamle.getAsJsonObject("tarif");
        if (tr == null) return "tarif_yok";
        String yem = str(hamle, "yem");
        Tarif t = new Tarif(strN(tr, "tetikleyici"), strN(tr, "silah"), strN(tr, "fizik"), yem.isEmpty() || yem.equals("yok") ? null : yem);
        if (!t.gecerli() || !b.izinli.contains(anahtar(t))) return "tarif_katalogda_yok";
        if (t.yem() != null && !b.yemler.contains(t.yem())) return "yem_gecersiz";
        if (oyun.butce().hak() < 1) return "hak_yok";
        if (aktifSayi() >= oyun.butce().maksAktif()) return "aktif_siniri";

        Location l = p.getLocation();
        double dx = l.getX() - a.konum.getX();
        double dz = l.getZ() - a.konum.getZ();
        double n = Math.max(1e-6, Math.hypot(dx, dz));
        Yerlestirici.Sonuc s = yerlestirici.kur(t, a.konum, new double[]{dx / n, dz / n}, p);
        if (!s.tamam()) return "kurulamadi:" + s.neden();
        oyun.butce().harca();
        JsonObject tahmin = karar.has("tahmin") && karar.get("tahmin").isJsonObject() ? karar.getAsJsonObject("tahmin") : null;
        oyun.tuzakEkle(s.tuzak(), tahmin == null ? "" : str(tahmin, "ne"), str(karar, "dusunce"));
        oyun.tarifKullanildi(t);
        return s.tuzak();
    }

    private int aktifSayi() {
        int n = 0;
        for (AktifTuzak t : oyun.tuzaklar()) if (t.durum.equals("kurulu")) n++;
        return n;
    }

    private static String anahtar(Tarif t) {
        return t.fizik() != null ? t.fizik() : t.tetikleyici() + "+" + t.silah();
    }

    // ---------- durum raporu ----------

    private Baglam baglamKur(String neden) {
        Player p = oyun.hedef();
        Baglam b = new Baglam();
        b.neden = neden;
        World w = p.getWorld();

        List<Aday> adaylar = new AdayUretici(plugin.getConfig()).uret(p, oyun.metrik().gidisVektoru(),
                oyun.tuzaklar().stream().filter(t -> t.durum.equals("kurulu")).map(t -> t.konum).toList(), oyun.yerler());
        Set<String> araziler = new HashSet<>();
        List<Map<String, Object>> adayListesi = new ArrayList<>();
        for (Aday a : adaylar) {
            b.adaylar.put(a.etiket, a);
            adayListesi.add(a.llmOzeti());
            araziler.add(IzinliTarifler.arazi(a.konum.getWorld().getEnvironment(), a.ortam));
        }

        Map<String, Object> katalog = katalog(b, araziler);

        Map<String, Object> oyunBilgi = new LinkedHashMap<>();
        oyunBilgi.put("t", oyun.sure());
        oyunBilgi.put("asama", oyun.butce().asama());
        oyunBilgi.put("dunya", w.getEnvironment().name());
        oyunBilgi.put("saat", w.getTime());
        oyunBilgi.put("hava", w.isThundering() ? "firtina" : w.hasStorm() ? "yagmur" : "acik");
        oyunBilgi.put("kalan_can", oyun.kalanCan());
        oyunBilgi.put("tuzak_hakki", oyun.butce().hak());
        oyunBilgi.put("maks_aktif", oyun.butce().maksAktif());
        oyunBilgi.put("aktif_sayi", aktifSayi());
        oyunBilgi.put("mudahale", mudahale.ozet());
        oyunBilgi.put("faz", mudahale.faz());
        oyunBilgi.put("olum_sayisi", oyun.olumSayisi());
        if (oyun.kurtulus() != null) oyunBilgi.put("portal", oyun.kurtulus().ozet(p));
        oyunBilgi.put("konusma_hakki", oyun.butce().kalanKonusma());
        oyunBilgi.put("konusma_bekleme_sn", oyun.butce().konusmaBeklemeSn());

        Map<String, Object> ali = new LinkedHashMap<>();
        Location l = p.getLocation();
        Map<String, Object> konum = new LinkedHashMap<>();
        konum.put("dunya", w.getName());
        konum.put("x", l.getBlockX());
        konum.put("y", l.getBlockY());
        konum.put("z", l.getBlockZ());
        ali.put("konum", konum);
        ali.put("can", Math.round(p.getHealth() * 10) / 10.0);
        ali.put("aclik", p.getFoodLevel());
        ali.put("metrikler", oyun.metrik().ozet(p));

        List<Map<String, Object>> aktif = new ArrayList<>();
        for (AktifTuzak t : oyun.tuzaklar()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id);
            m.put("tarif", t.tarif.ozet());
            m.put("durum", t.durum);
            m.put("yas_sn", (System.currentTimeMillis() - t.zamanMs) / 1000);
            aktif.add(m);
        }

        Map<String, Object> durum = new LinkedHashMap<>();
        durum.put("oyun_id", oyun.oyunId());
        durum.put("oyun_klasoru", oyun.log().klasor().toString());
        durum.put("neden", neden);
        durum.put("oyun", oyunBilgi);
        durum.put("ali", ali);
        durum.put("adaylar", adayListesi);
        durum.put("katalog", katalog);
        durum.put("aktif_tuzaklar", aktif);
        durum.put("son_olaylar", oyun.olaylar().son(plugin.getConfig().getInt("dusunme.son_olay_adet")));
        b.durum = durum;
        return b;
    }

    /** Kullanilabilir tuzak katalogu: izinli, yasaksiz, son tarifleri tekrarlamayan, arazisi uyan. */
    private Map<String, Object> katalog(Baglam b, Set<String> adayAraziler) {
        Path klasor = Path.of(plugin.getConfig().getString("tuzaklar_klasoru")).toAbsolutePath().normalize();
        Set<String> sonKullanilan = oyun.sonKullanilanModuller();
        Map<String, Object> modulOzet = new LinkedHashMap<>();
        List<Map<String, Object>> tarifler = new ArrayList<>();
        for (IzinliTarifler.Izinli i : IzinliTarifler.oku(klasor)) {
            Tarif t = i.tarif();
            if (i.araziler().stream().noneMatch(adayAraziler::contains)) continue;
            List<String> idler = new ArrayList<>();
            for (String id : new String[]{t.tetikleyici(), t.silah(), t.fizik()}) if (id != null) idler.add(id);
            boolean uygun = true;
            for (String id : idler) if (oyun.yasakli(id) || sonKullanilan.contains(id)) uygun = false;
            if (!uygun) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tetikleyici", t.tetikleyici());
            m.put("silah", t.silah());
            m.put("fizik", t.fizik());
            tarifler.add(m);
            b.izinli.add(anahtar(t));
            for (String id : idler) modulOzet.put(id, modulBilgisi(moduller.al(id)));
        }
        List<String> yemler = new ArrayList<>();
        for (Modul y : moduller.tur(Modul.Tur.YEM)) {
            if (oyun.yasakli(y.id)) continue;
            yemler.add(y.id);
            b.yemler.add(y.id);
            modulOzet.put(y.id, modulBilgisi(y));
        }
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("moduller", modulOzet);
        k.put("tarifler", tarifler);
        k.put("yemler", yemler);
        return k;
    }

    private static Map<String, Object> modulBilgisi(Modul m) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("tur", m.tur.name().toLowerCase());
        o.put("baglam", new ArrayList<>(m.baglam));
        o.put("yerlesim", new ArrayList<>(m.yerlesim));
        o.put("fark_edilme", m.farkEdilme);
        o.put("kacinma", m.kacinma);
        return o;
    }

    // ---------- log ----------

    private void logla(String neden, int deneme, Baglam b, JsonObject karar, long sureMs, String uygulama) {
        if (oyun.log() == null) return;
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("neden", neden);
        k.put("deneme", deneme);
        if (b != null) {
            Map<String, Object> girdi = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> oyunBilgi = (Map<String, Object>) b.durum.get("oyun");
            girdi.put("oyun", oyunBilgi);
            girdi.put("aday_sayisi", b.adaylar.size());
            girdi.put("tarif_sayisi", b.izinli.size());
            k.put("girdi_ozeti", girdi);
        }
        if (karar != null) {
            k.put("cikti", gson.fromJson(karar, Map.class));
            k.put("model", str(karar, "model"));
        }
        k.put("sure_ms", sureMs);
        k.put("uygulama", uygulama);
        oyun.log().yaz("kararlar.jsonl", "karar", k);
    }

    // ---------- json yardimcilari ----------

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String strN(JsonObject o, String k) {
        String s = str(o, k);
        return s.isEmpty() ? null : s;
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }
}
