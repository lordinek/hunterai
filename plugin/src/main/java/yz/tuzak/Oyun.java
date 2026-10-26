package yz.tuzak;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import java.util.ArrayList;
import java.util.List;
import yz.tuzak.aday.OnemliYer;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import yz.tuzak.beyin.DusunmeDongusu;
import yz.tuzak.can.DunyaYedegi;
import yz.tuzak.gozlem.Metrikler;
import yz.tuzak.tuzak.Tarif;
import yz.tuzak.tuzak.AktifTuzak;
import yz.tuzak.tuzak.TuzakIzleyici;
import yz.tuzak.log.OyunLogu;
import yz.tuzak.olay.OlayYoneticisi;

/** Tek bir oyunun yasam dongusu: log klasoru, gozlem gorevi, olum sayaci. */
public final class Oyun {

    private static final String PROFIL_ISKELETI = "{\n"
            + "  \"aliskanliklar\": [],\n"
            + "  \"zaaflar\": [],\n"
            + "  \"kacinma_yontemleri\": [],\n"
            + "  \"olumler\": [],\n"
            + "  \"fark_ettigi_tuzak_tipleri\": []\n"
            + "}\n";

    private final YzPlugin plugin;
    private OyunLogu log;
    private OlayYoneticisi olaylar;
    private Metrikler metrik;
    private Player hedef;
    private BukkitTask gorev;
    private int olumSayisi;
    private double sonMetrikLog;
    private double hayatBasT;
    private double sonYapiTarama;
    private final Set<String> gorulenYapilar = new HashSet<>();
    private final Set<String> gorulenDunyalar = new HashSet<>();
    private final List<OnemliYer> yerler = new ArrayList<>();
    private final List<AktifTuzak> tuzaklar = new ArrayList<>();
    private final Map<String, Long> yasaklar = new HashMap<>();
    private TuzakIzleyici izleyici;
    private DusunmeDongusu dongu;
    private Butce butce;
    private Kurtulus kurtulus;
    private Savas savas;
    private final Deque<Tarif> sonTarifler = new ArrayDeque<>();

    public Oyun(YzPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean aktif() {
        return hedef != null;
    }

    public boolean hedefMi(Player p) {
        return hedef != null && hedef.getUniqueId().equals(p.getUniqueId());
    }

    public Player hedef() {
        return hedef;
    }

    /** Hedef oyuncu cikip tekrar girdiginde eski Player nesnesi gecersiz olur; yenisiyle degistirilir. */
    public void hedefYenile(Player p) {
        if (hedef != null && hedef.getUniqueId().equals(p.getUniqueId())) hedef = p;
    }

    public OyunLogu log() {
        return log;
    }

    public OlayYoneticisi olaylar() {
        return olaylar;
    }

    public Metrikler metrik() {
        return metrik;
    }

    public List<OnemliYer> yerler() {
        return yerler;
    }

    /** Ayni turden 6 blok icinde kayit varsa eklemez. */
    public void yerEkle(String tur, Location konum) {
        for (OnemliYer y : yerler) {
            if (y.tur().equals(tur) && y.konum().getWorld().equals(konum.getWorld()) && y.konum().distance(konum) < 6) return;
        }
        yerler.add(new OnemliYer(tur, konum.clone()));
    }

    public void donguAyarla(DusunmeDongusu d) {
        this.dongu = d;
    }

    public DusunmeDongusu dongu() {
        return dongu;
    }

    public Butce butce() {
        return butce;
    }

    public void savasAyarla(Savas s) {
        this.savas = s;
    }

    public Savas savas() {
        return savas;
    }

    public void kurtulusAyarla(Kurtulus k) {
        this.kurtulus = k;
    }

    public Kurtulus kurtulus() {
        return kurtulus;
    }

    public String oyunId() {
        return log == null ? "" : log.klasor().getFileName().toString();
    }

    /** Son N tarifte kullanilan modul id'leri (cesitlilik kurali). */
    public java.util.Set<String> sonKullanilanModuller() {
        java.util.Set<String> s = new HashSet<>();
        for (Tarif t : sonTarifler) {
            if (t.tetikleyici() != null) s.add(t.tetikleyici());
            if (t.silah() != null) s.add(t.silah());
            if (t.fizik() != null) s.add(t.fizik());
        }
        return s;
    }

    public void tarifKullanildi(Tarif t) {
        sonTarifler.addLast(t);
        while (sonTarifler.size() > plugin.getConfig().getInt("dusunme.son_tarif_hafiza")) sonTarifler.pollFirst();
    }

    /** Konusma istegi: butce izin verirse sahnelenir (altyazi, ses, animasyon). */
    public boolean konus(String metin) {
        if (!butce.konusabilir()) return false;
        butce.konustu();
        plugin.konusma().soyle(metin, true);
        return true;
    }

    public void izleyiciAyarla(TuzakIzleyici i) {
        this.izleyici = i;
    }

    public List<AktifTuzak> tuzaklar() {
        return tuzaklar;
    }

    /** Kurulan tuzagi listeye ekler ve tuzaklar.jsonl'e yazar. */
    public void tuzakEkle(AktifTuzak t, String tahmin, String dusunce) {
        t.tahmin = tahmin;
        t.dusunce = dusunce;
        tuzaklar.add(t);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("tip", "kurulum");
        d.put("tuzak_id", t.id);
        d.put("tarif", t.tarif.ozet());
        d.put("dunya", t.konum.getWorld().getName());
        d.put("x", t.konum.getBlockX());
        d.put("y", t.konum.getBlockY());
        d.put("z", t.konum.getBlockZ());
        d.put("tahmin", tahmin);
        d.put("dusunce", dusunce);
        log.yaz("tuzaklar.jsonl", "tuzak", d);
    }

    public void tuzakLogu(String tip, AktifTuzak t, Map<String, Object> ek) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("tip", tip);
        d.put("tuzak_id", t.id);
        d.put("tarif", t.tarif.ozet());
        d.putAll(ek);
        log.yaz("tuzaklar.jsonl", "tuzak", d);
    }

    /** Fark edilen tuzagin moduller yasak_suresi_dk boyunca katalogdan cikarilir. */
    public void yasakla(String modulId) {
        if (modulId == null) return;
        long sure = plugin.getConfig().getLong("yasak_suresi_dk") * 60_000L;
        yasaklar.put(modulId, System.currentTimeMillis() + sure);
    }

    public boolean yasakli(String modulId) {
        Long bitis = yasaklar.get(modulId);
        return bitis != null && bitis > System.currentTimeMillis();
    }

    public int olumSayisi() {
        return olumSayisi;
    }

    public int kalanCan() {
        int hak = plugin.getConfig().getInt("can_hakki");
        if (hak <= 0) return 99; // 0: can siniri yok
        return Math.max(0, hak - olumSayisi);
    }

    public double sure() {
        return log == null ? 0 : log.saniye();
    }

    /** @return hata mesaji ya da null */
    public String baslat(Player p) {
        if (aktif()) return "Oyun zaten aktif.";
        try {
            Path veri = klasor("veri_klasoru");
            // yeni oyun: spoiler kilidi yeniden kapanir
            Files.deleteIfExists(veri.resolve("spoiler_acik"));
            plugin.getConfig().set("spoiler_kilidi", true);
            Path oyunKlasoru = veri.resolve("oyunlar")
                    .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
            log = new OyunLogu(oyunKlasoru);
            Path profil = veri.resolve("profil.json");
            if (!Files.exists(profil)) Files.writeString(profil, PROFIL_ISKELETI, StandardCharsets.UTF_8);
            log.dosyaYaz("profil_baslangic.json", Files.readString(profil, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "Log klasoru olusturulamadi.";
        }
        hedef = p;
        olumSayisi = 0;
        hayatBasT = 0;
        plugin.olumAkisi().sifirla();
        sonMetrikLog = 0;
        sonYapiTarama = 0;
        gorulenYapilar.clear();
        gorulenDunyalar.clear();
        yerler.clear();
        tuzaklar.clear();
        yasaklar.clear();
        sonTarifler.clear();
        butce = new Butce(plugin);
        if (dongu != null) dongu.sifirla();
        gorulenDunyalar.add(p.getWorld().getEnvironment().name());
        olaylar = new OlayYoneticisi(log, plugin.getConfig().getInt("gozlem.olay_debounce_sn"));
        metrik = new Metrikler(plugin.getConfig(), log, olaylar);
        olaylar.dinleyiciAyarla((tip, detay, gecti) -> {
            butce.olay(tip, detay);
            if (dongu != null) dongu.olay(tip, detay, gecti);
        });
        long periyot = Math.max(1, plugin.getConfig().getInt("gozlem.konum_aralik_sn")) * 20L;
        gorev = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tik, 20L, periyot);
        Map<String, Object> detay = new LinkedHashMap<>();
        detay.put("surum", plugin.getServer().getMinecraftVersion());
        olaylar.kaydet("oyun_basladi", detay);
        plugin.varlik().baslat(p);
        if (kurtulus != null) kurtulus.kur(p);
        String tanitim = plugin.getConfig().getString("konusma.tanitim_metni");
        if (tanitim != null && !tanitim.isBlank()) konus(tanitim);
        return null;
    }

    public void durdur() {
        if (!aktif()) return;
        gorev.cancel();
        if (kurtulus != null) kurtulus.sifirla();
        if (savas != null) savas.sifirla();
        plugin.varlik().durdur();
        metrik.logla(hedef);
        olaylar.kaydet("oyun_bitti", Map.of("olum_sayisi", olumSayisi));
        try {
            Path profil = klasor("veri_klasoru").resolve("profil.json");
            if (Files.exists(profil)) log.dosyaYaz("profil_bitis.json", Files.readString(profil, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        if (plugin.getConfig().getBoolean("kapanis.dunya_yedegi")) DunyaYedegi.yedekle(plugin, log.klasor().resolve("dunya_yedek"));
        log.kapat();
        hedef = null;
        log = null;
        olaylar = null;
        metrik = null;
    }

    private Path klasor(String anahtar) throws IOException {
        Path p = Path.of(plugin.getConfig().getString(anahtar)).toAbsolutePath().normalize();
        Files.createDirectories(p);
        return p;
    }

    /** Beyaz flas + senkron kaydi. */
    public String isaret() {
        if (!aktif()) return "Oyun aktif degil.";
        Map<String, Object> alan = new LinkedHashMap<>();
        alan.put("gercek_saat", System.currentTimeMillis());
        log.yaz("isaretler.jsonl", "isaret", alan);
        beyazFlas(hedef);
        return null;
    }

    /** Tum ekrani kaplayan beyaz flas: sadece bu oyuncuya gorunen, ters olcekli (ic yuzu gorunen) beyaz blok. */
    private void beyazFlas(Player p) {
        Location goz = p.getEyeLocation();
        org.bukkit.entity.BlockDisplay d = p.getWorld().spawn(goz, org.bukkit.entity.BlockDisplay.class, e -> {
            e.setBlock(org.bukkit.Material.WHITE_CONCRETE.createBlockData());
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            e.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(1f, 1f, 1f),
                    new org.joml.Quaternionf(), new org.joml.Vector3f(-2f, -2f, -2f), new org.joml.Quaternionf()));
        });
        p.showEntity(plugin, d);
        p.spawnParticle(Particle.FLASH, goz, 3, 0, 0, 0, 0, Color.WHITE);
        plugin.getServer().getScheduler().runTaskLater(plugin, d::remove, plugin.getConfig().getLong("isaret.flas_tik"));
    }

    public void olum(Player p, String sebep, String saldiran) {
        olumSayisi++;
        Location l = p.getLocation();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("olum_no", olumSayisi);
        d.put("kalan_can", kalanCan());
        d.put("sebep", sebep);
        d.put("saldiran", saldiran);
        d.put("dunya", l.getWorld().getName());
        d.put("x", Math.round(l.getX()));
        d.put("y", Math.round(l.getY()));
        d.put("z", Math.round(l.getZ()));
        String logId = olaylar.kaydet("olum", d);
        metrik.logla(p);
        plugin.olumAkisi().olum(p, sebep, logId, olumSayisi, hayatBasT, log.saniye());
    }

    public double hayatBasT() {
        return hayatBasT;
    }

    /** Yeniden dogus: yeni hayat baslar. */
    public void yeniHayat() {
        hayatBasT = log.saniye();
    }

    /** Konusma butcesinden bagimsiz bir kerelik konusma (ejderha yenilgi kabulu). */
    public void konusButceDisi(String metin) {
        plugin.konusma().soyle(metin, false);
    }

    /** Bu ortam (overworld/nether/end) ilk kez mi giriliyor? */
    public boolean ilkGiris(String ortam) {
        return gorulenDunyalar.add(ortam);
    }

    private void tik() {
        if (!aktif() || !hedef.isOnline()) return;
        metrik.saniyeTik(hedef);
        double kc = kurtulus == null ? 1.0 : kurtulus.carpan();
        butce.tik(plugin.getConfig().getInt("gozlem.konum_aralik_sn") / kc);
        if (kurtulus != null) kurtulus.tik(hedef);
        boolean savasta = savas != null && savas.aktif();
        if (dongu != null && !savasta && (kurtulus == null || !kurtulus.bitti())) dongu.tik();
        if (izleyici != null) izleyici.izle(hedef);
        Location l = hedef.getLocation();
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("dunya", l.getWorld().getName());
        k.put("x", Math.round(l.getX() * 100) / 100.0);
        k.put("y", Math.round(l.getY() * 100) / 100.0);
        k.put("z", Math.round(l.getZ() * 100) / 100.0);
        k.put("yaw", Math.round(l.getYaw() * 10) / 10.0);
        k.put("pitch", Math.round(l.getPitch() * 10) / 10.0);
        k.put("can", Math.round(hedef.getHealth() * 10) / 10.0);
        k.put("aclik", hedef.getFoodLevel());
        log.yaz("konum.jsonl", "konum", k);

        double t = log.saniye();
        if (t - sonMetrikLog >= plugin.getConfig().getDouble("gozlem.metrik_log_sn")) {
            sonMetrikLog = t;
            metrik.logla(hedef);
        }
        if (t - sonYapiTarama >= plugin.getConfig().getDouble("gozlem.yapi_tarama_sn")) {
            sonYapiTarama = t;
            yapiTara(l);
        }
    }

    private void yapiTara(Location l) {
        for (GeneratedStructure gs : l.getChunk().getStructures()) {
            BoundingBox bb = gs.getBoundingBox();
            String ad = String.valueOf(org.bukkit.Registry.STRUCTURE.getKey(gs.getStructure()));
            String anahtar = ad + "@" + (int) bb.getMinX() + "," + (int) bb.getMinZ();
            if (!gorulenYapilar.add(anahtar)) continue;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("yapi", ad);
            d.put("mesafe", Math.round(Math.hypot(bb.getCenterX() - l.getX(), bb.getCenterZ() - l.getZ())));
            olaylar.kaydet("yapi_yakin", d);
        }
    }
}
