package yz.tuzak.varlik;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import yz.tuzak.YzPlugin;

/**
 * Gokyuzu figuru. Blockbench modeli hazir olana kadar BlockDisplay'lerden yapilmis detayli bir silüet:
 * puskullu cuppe, kapuson, karanlik yuz, kirmizi gozler ve sirtis, uzun pencereli kollar, devre cizgileri
 * ve cyan glitch pikselleri. Konumlandirma, animasyon tetikleri ve ortam davranislari gercektir.
 * Hicbir animasyon tuzak kurulumuyla senkron degildir (Kural 8): isaret ve glitch rastgele oynar.
 */
public final class Varlik {

    private static final String ETIKET = "yz_varlik";

    // hareketli parca gruplari
    private static final int STATIK = 0;
    private static final int KOL_SAG = 1;
    private static final int KOL_SOL = 2;
    private static final int PIKSEL = 4;
    private static final int PUSKUL = 10; // 10 + indeks

    private static final int KOYU_ISIK = 3;

    private record Parca(BlockDisplay display, Vector3f merkez, Vector3f boyut, Quaternionf donus,
                         int grup, Vector3f pivot) {}

    private final YzPlugin plugin;
    private final List<Parca> parcalar = new ArrayList<>();
    private final List<BlockDisplay> parlayanlar = new ArrayList<>();
    private ItemDisplay govdeResim;
    private ItemDisplay gozResim;
    private boolean resimModu;
    private float resimOlcek;
    private BukkitTask gorev;
    private Player hedef;
    private World dunya;
    private float S;
    private double yawFiltre;
    private boolean yawHazir;
    private long tik;

    // animasyon durumu (tik cinsinden bitis zamani)
    private long egilBitis;
    private long glitchBitis;
    private long isaretBitis;
    private float isaretYaw;
    private boolean konusuyor;
    private double konusSeviye;
    private long sonrakiIsaret;
    private long sonrakiGlitch;

    public Varlik(YzPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sunucu acilisinda onceki oturumdan kalan figur varsa temizler. */
    public void artiklariTemizle() {
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity e : w.getEntities()) if (e.getScoreboardTags().contains(ETIKET)) e.remove();
        }
    }

    public boolean aktif() {
        return gorev != null;
    }

    public void baslat(Player p) {
        durdur();
        hedef = p;
        yawHazir = false;
        tik = 0;
        egilBitis = glitchBitis = isaretBitis = 0;
        sonrakiIsaret = rastgeleAralik("varlik.isaret_aralik_sn");
        sonrakiGlitch = rastgeleAralik("varlik.glitch_aralik_sn");
        olustur(p.getWorld(), p.getLocation());
        gorev = plugin.getServer().getScheduler().runTaskTimer(plugin, this::guncelle, 1L,
                plugin.getConfig().getLong("varlik.guncelleme_tik"));
    }

    public void durdur() {
        if (gorev != null) gorev.cancel();
        gorev = null;
        temizle();
        hedef = null;
    }

    // ---------- animasyon girdileri ----------

    /** Can azaldiginda ve olumde Ali'ye dogru egilir. */
    public void egil() {
        if (!aktif()) return;
        egilBitis = tik + plugin.getConfig().getLong("varlik.egil_tik");
    }

    /** Ses calarken konus animasyonu; gozlerin parlakligi sesin siddetiyle oynar. */
    public void konus(boolean basla) {
        konusuyor = basla;
        if (!basla) konusSeviye = 0;
    }

    /** Ses siddeti 0..1 (gozlerin parlakligi). */
    public void konusSeviye(double s) {
        konusSeviye = Math.max(0, Math.min(1, s));
    }

    public void glitch() {
        if (aktif()) glitchBitis = tik + plugin.getConfig().getLong("varlik.glitch_tik");
    }

    public void isaret() {
        if (!aktif()) return;
        isaretBitis = tik + plugin.getConfig().getLong("varlik.isaret_tik");
        isaretYaw = ThreadLocalRandom.current().nextFloat() * 360f - 180f;
    }

    /** /yz varlik ad test komutu. */
    public boolean oynat(String ad) {
        switch (ad) {
            case "egil" -> egil();
            case "glitch" -> glitch();
            case "isaret" -> isaret();
            case "konus" -> konus(true);
            case "bekle", "izle" -> konus(false);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------- gorsel ----------

    private void olustur(World w, Location etrafi) {
        dunya = w;
        parcalar.clear();
        parlayanlar.clear();
        resimModu = "resim".equals(plugin.getConfig().getString("varlik.mod", "resim"));
        S = (float) plugin.getConfig().getDouble("varlik.olcek");
        if (resimModu) {
            resimOlustur();
            return;
        }
        Material K = Material.BLACK_CONCRETE;
        Material M = Material.PURPLE_CONCRETE;
        Material KIRMIZI = Material.RED_CONCRETE;
        Material CYAN = Material.CYAN_CONCRETE;
        Random r = new Random(7);

        // cuppe: yukari daralan katmanlar, on yuzey duz (devre cizgileri icin)
        for (int i = 0; i < 5; i++) {
            float w1 = 0.50f - 0.05f * i;
            ekle(K, 0, 0.17f + 0.14f * i, 0, w1, 0.15f, 0.24f, 0, 0, 0, STATIK, 0, 0, 0, KOYU_ISIK);
        }
        ekle(M, 0, 0.10f, 0, 0.55f, 0.05f, 0.28f, 0, 0, 0, STATIK, 0, 0, 0, KOYU_ISIK);
        // sirt pelerini: asagida disari acilir
        ekle(K, 0, 0.45f, -0.17f, 0.48f, 0.80f, 0.03f, 0.14f, 0, 0, STATIK, 0, 0, 0, KOYU_ISIK);
        // omuzlar
        ekle(K, 0, 0.84f, 0, 0.64f, 0.07f, 0.22f, 0, 0, 0, STATIK, 0, 0, 0, KOYU_ISIK);

        // yirtik cuppe puskulleri: farkli boylarda, ruzgarda sallanir
        for (int j = 0; j < 11; j++) {
            float x = -0.25f + 0.05f * j;
            float boy = 0.14f + 0.30f * r.nextFloat();
            float z = (j % 2 == 0) ? 0.12f : -0.12f;
            float egim = (r.nextFloat() - 0.5f) * 0.2f;
            ekle(j % 3 == 0 ? M : K, x, 0.10f - boy / 2, z, 0.045f, boy, 0.02f, 0, 0, egim,
                    PUSKUL + j, x, 0.10f, z, KOYU_ISIK);
        }

        // kapuson: yan duvarlar, tepe ve sivri uc
        ekle(K, 0, 0.955f, 0.01f, 0.30f, 0.14f, 0.30f, 0, 0, 0, STATIK, 0, 0, 0, KOYU_ISIK);
        ekle(K, -0.165f, 0.94f, 0, 0.05f, 0.27f, 0.31f, 0, 0, 0.06f, STATIK, 0, 0, 0, KOYU_ISIK);
        ekle(K, 0.165f, 0.94f, 0, 0.05f, 0.27f, 0.31f, 0, 0, -0.06f, STATIK, 0, 0, 0, KOYU_ISIK);
        ekle(K, 0, 1.08f, -0.03f, 0.24f, 0.11f, 0.26f, -0.25f, 0, 0, STATIK, 0, 0, 0, KOYU_ISIK);
        ekle(K, 0, 1.19f, -0.10f, 0.10f, 0.16f, 0.10f, -0.55f, 0, 0, STATIK, 0, 0, 0, KOYU_ISIK);
        // karanlik yuz: cevreden daha karanlik
        ekle(K, 0, 0.93f, 0.155f, 0.22f, 0.17f, 0.02f, 0, 0, 0, STATIK, 0, 0, 0, 0);
        // kirmizi gozler: egik, ince
        parlayanlar.add(ekle(KIRMIZI, -0.058f, 0.955f, 0.168f, 0.075f, 0.022f, 0.012f, 0, 0, 0.45f, STATIK, 0, 0, 0, 15).display());
        parlayanlar.add(ekle(KIRMIZI, 0.058f, 0.955f, 0.168f, 0.075f, 0.022f, 0.012f, 0, 0, -0.45f, STATIK, 0, 0, 0, 15).display());
        // sirtis: kirmizi, kenarlar yukarida
        for (int i = -3; i <= 3; i++) {
            float x = i * 0.028f;
            float y = 0.885f + 0.012f * Math.abs(i);
            parlayanlar.add(ekle(KIRMIZI, x, y, 0.168f, 0.020f, 0.008f, 0.01f, 0, 0, 0, STATIK, 0, 0, 0, 12).display());
        }

        // devre cizgileri (on yuzde, kirmizi)
        float zc = 0.124f;
        cizgi(-0.08f, 0.15f, 0.012f, 0.45f, zc);
        cizgi(-0.16f, 0.60f, 0.092f, 0.012f, zc);
        cizgi(-0.16f, 0.60f, 0.012f, 0.14f, zc);
        cizgi(0.10f, 0.20f, 0.012f, 0.30f, zc);
        cizgi(0.10f, 0.50f, 0.085f, 0.012f, zc);
        cizgi(0.18f, 0.50f, 0.012f, 0.16f, zc);
        cizgi(-0.02f, 0.30f, 0.012f, 0.25f, zc);
        for (float[] n : new float[][]{{-0.08f, 0.15f}, {-0.16f, 0.74f}, {0.10f, 0.20f}, {0.18f, 0.66f}, {-0.02f, 0.55f}}) {
            cizgi(n[0] - 0.012f, n[1] - 0.012f, 0.036f, 0.036f, zc);
        }

        // kollar: uzun, ince, dirsekli; her elde dort pence
        kolEkle(1, KOL_SAG, K);
        kolEkle(-1, KOL_SOL, K);

        // cyan glitch pikselleri
        for (int i = 0; i < 16; i++) {
            parlayanlar.add(ekle(CYAN, 0, 0.5f, 0, 0.035f, 0.035f, 0.035f, 0, 0, 0, PIKSEL, 0, 0, 0, 15).display());
        }
        piksellerYerlestir(1.0);
    }

    /** Yapay zeka cizimi: gövde ve parlayan gozler ayri katman (kaynak paketindeki yz:figur_govde / figur_goz). */
    private void resimOlustur() {
        float yukseklik = (float) plugin.getConfig().getDouble("varlik.resim_yukseklik");
        resimOlcek = yukseklik / 2f;
        S = yukseklik / 1.1f;
        govdeResim = resimSpawn("figur_govde", plugin.getConfig().getInt("varlik.resim_govde_isik"));
        gozResim = resimSpawn("figur_goz", 15);
    }

    private ItemDisplay resimSpawn(String model, int isik) {
        int yumusatma = (int) plugin.getConfig().getLong("varlik.yumusatma_tik");
        float menzil = (float) plugin.getConfig().getDouble("varlik.gorus_menzili");
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("yz", model));
        return dunya.spawn(hedef.getLocation(), ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setPersistent(false);
            e.addScoreboardTag(ETIKET);
            e.setViewRange(menzil);
            e.setTeleportDuration(yumusatma);
            e.setInterpolationDuration(yumusatma);
            e.setBrightness(new Display.Brightness(isik, isik));
        });
    }

    /** Egilme, sallanma, glitch ve konusma: resim katmanlarinin donusumunu gunceller. */
    private void resimGuncelle(float egimDerece, float yanEgim, boolean glitch) {
        if (govdeResim == null || !govdeResim.isValid()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float s = resimOlcek;
        float xOlcek = s;
        Vector3f oteleme = new Vector3f();
        if (glitch) {
            // rastgele degil, yumusak dalga: titreme hissi var ama sicrama yok
            xOlcek = s * (1f + 0.035f * (float) Math.sin(tik * 1.7));
            oteleme.set(0.9f * (float) Math.sin(tik * 2.3), 0.35f * (float) Math.sin(tik * 1.1), 0);
        }
        Quaternionf rot = new Quaternionf().rotateX((float) Math.toRadians(egimDerece)).rotateZ((float) Math.toRadians(yanEgim));
        // figur merkezi, donusten sonra da varlik konumunda kalsin
        Vector3f t = new Vector3f(0, 0.5f * s, 0).rotate(rot).add(oteleme);
        govdeResim.setTransformation(new Transformation(t, rot, new Vector3f(xOlcek, s, s), new Quaternionf()));
        govdeResim.setInterpolationDelay(0);
        float goz = konusuyor ? 1f + 0.06f * (float) Math.sin(tik * 0.8) : 1f;
        Vector3f tg = new Vector3f(t);
        gozResim.setTransformation(new Transformation(tg, rot, new Vector3f(xOlcek * goz, s * goz, s), new Quaternionf()));
        gozResim.setInterpolationDelay(0);
    }

    private void kolEkle(int yon, int grup, Material K) {
        float px = 0.31f * yon;
        float py = 0.80f;
        ekle(K, 0.36f * yon, 0.55f, 0.02f, 0.06f, 0.50f, 0.06f, 0, 0, -0.15f * yon, grup, px, py, 0, KOYU_ISIK);
        ekle(K, 0.40f * yon, 0.20f, 0.06f, 0.05f, 0.45f, 0.05f, -0.15f, 0, -0.05f * yon, grup, px, py, 0, KOYU_ISIK);
        ekle(K, 0.41f * yon, -0.05f, 0.08f, 0.07f, 0.07f, 0.04f, 0, 0, 0, grup, px, py, 0, KOYU_ISIK);
        for (int f = 0; f < 4; f++) {
            float o = (f - 1.5f) * 0.018f;
            ekle(K, 0.41f * yon + o, -0.19f, 0.08f, 0.012f, 0.20f, 0.012f, 0, 0, o * 6f * yon, grup, px, py, 0, KOYU_ISIK);
        }
    }

    private void cizgi(float x, float y, float en, float boy, float z) {
        parlayanlar.add(ekle(Material.RED_CONCRETE, x + en / 2, y + boy / 2, z, en, boy, 0.006f, 0, 0, 0, STATIK, 0, 0, 0, 12).display());
    }

    /**
     * Parca ekler. Koordinatlar figur olcegi birimindedir (S ile carpilir); (cx, cy, cz) parcanin merkezi.
     *
     * @param isik 0..15 sabit isik, negatif: dunyanin isigi
     */
    private Parca ekle(Material m, float cx, float cy, float cz, float sx, float sy, float sz,
                       float rx, float ry, float rz, int grup, float px, float py, float pz, int isik) {
        int yumusatma = (int) plugin.getConfig().getLong("varlik.yumusatma_tik");
        float menzil = (float) plugin.getConfig().getDouble("varlik.gorus_menzili");
        BlockDisplay d = dunya.spawn(hedef.getLocation(), BlockDisplay.class, e -> {
            e.setBlock(m.createBlockData());
            e.setPersistent(false);
            e.addScoreboardTag(ETIKET);
            e.setViewRange(menzil);
            e.setTeleportDuration(yumusatma);
            e.setInterpolationDuration(yumusatma);
            e.setBillboard(Display.Billboard.FIXED);
            if (isik >= 0) e.setBrightness(new Display.Brightness(isik, isik));
        });
        Parca p = new Parca(d, new Vector3f(cx * S, cy * S, cz * S), new Vector3f(sx * S, sy * S, sz * S),
                new Quaternionf().rotateXYZ(rx, ry, rz), grup, new Vector3f(px * S, py * S, pz * S));
        uygula(p, new Quaternionf());
        parcalar.add(p);
        return p;
    }

    /** Parcanin donusumunu grup donusuyle (pivot etrafinda) birlestirip display'e yazar. */
    private void uygula(Parca p, Quaternionf grupDonusu) {
        Vector3f merkez = new Vector3f(p.merkez());
        Quaternionf donus = new Quaternionf(p.donus());
        if (p.grup() != STATIK) {
            merkez.sub(p.pivot()).rotate(grupDonusu).add(p.pivot());
            donus = new Quaternionf(grupDonusu).mul(p.donus());
        }
        Vector3f yarim = new Vector3f(p.boyut()).mul(0.5f).rotate(donus);
        Vector3f ceviri = merkez.sub(yarim);
        p.display().setTransformation(new Transformation(ceviri, donus, p.boyut(), new Quaternionf()));
        p.display().setInterpolationDelay(0);
    }

    private void piksellerYerlestir(double gorunurluk) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Parca p : parcalar) {
            if (p.grup() != PIKSEL) continue;
            if (r.nextDouble() > gorunurluk) {
                p.display().setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
                p.display().setInterpolationDelay(0);
                continue;
            }
            Vector3f c = new Vector3f((r.nextFloat() - 0.5f) * 0.9f * S, r.nextFloat() * 1.15f * S, (r.nextFloat() - 0.4f) * 0.45f * S);
            p.display().setTransformation(new Transformation(c.sub(new Vector3f(p.boyut()).mul(0.5f)), new Quaternionf(), p.boyut(), new Quaternionf()));
            p.display().setInterpolationDelay(0);
        }
    }

    private void temizle() {
        for (Parca p : parcalar) if (p.display().isValid()) p.display().remove();
        parcalar.clear();
        parlayanlar.clear();
        if (govdeResim != null) govdeResim.remove();
        if (gozResim != null) gozResim.remove();
        govdeResim = null;
        gozResim = null;
    }

    // ---------- konum ve animasyon ----------

    private void guncelle() {
        if (hedef == null || !hedef.isOnline()) {
            durdur();
            return;
        }
        long adim = plugin.getConfig().getLong("varlik.guncelleme_tik");
        tik += adim;
        World w = hedef.getWorld();
        if (!w.equals(dunya)) {
            temizle();
            olustur(w, hedef.getLocation());
        }

        // yon: oyuncunun baktigi yonun yavas suzulmus hali
        double yaw = hedef.getLocation().getYaw();
        if (!yawHazir) {
            yawFiltre = yaw;
            yawHazir = true;
        }
        double fark = ((yaw - yawFiltre + 540) % 360) - 180;
        yawFiltre += fark * plugin.getConfig().getDouble("varlik.yon_yumusatma");

        Location konum = hedefKonum(w);
        rastgeleAnimasyonlar();

        // hafif one egik durus + egilme
        float pitch = (float) plugin.getConfig().getDouble("varlik.durus_egimi");
        double bob = Math.sin(tik / 20.0 * plugin.getConfig().getDouble("varlik.salinim_hizi"))
                * plugin.getConfig().getDouble("varlik.salinim_genlik");
        konum.add(0, bob, 0);
        if (tik < egilBitis) {
            double oran = Math.sin(Math.PI * (1.0 - (double) (egilBitis - tik) / plugin.getConfig().getLong("varlik.egil_tik")));
            pitch += (float) (oran * plugin.getConfig().getDouble("varlik.egil_aci"));
        }
        if (tik < glitchBitis) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double g = plugin.getConfig().getDouble("varlik.glitch_kayma");
            konum.add((r.nextDouble() - 0.5) * g, (r.nextDouble() - 0.5) * g, (r.nextDouble() - 0.5) * g);
        }

        // kafa (tum figur) her zaman Ali'ye doner
        Vector ali = hedef.getEyeLocation().toVector().subtract(konum.toVector());
        float bakisYaw = (float) Math.toDegrees(Math.atan2(-ali.getX(), ali.getZ()));
        konum.setYaw(bakisYaw);
        konum.setPitch(pitch);

        for (Parca p : parcalar) p.display().teleport(konum);
        if (resimModu && govdeResim != null) {
            govdeResim.teleport(konum);
            gozResim.teleport(konum);
            float egim = (float) plugin.getConfig().getDouble("varlik.durus_egimi") * 0.4f;
            if (tik < egilBitis) {
                double oran = Math.sin(Math.PI * (1.0 - (double) (egilBitis - tik) / plugin.getConfig().getLong("varlik.egil_tik")));
                egim += (float) (oran * plugin.getConfig().getDouble("varlik.egil_aci"));
            }
            float yan = tik < isaretBitis ? (float) (12 * Math.sin((isaretBitis - tik) * 0.15)) : 0f;
            resimGuncelle(egim, yan, tik < glitchBitis);
        }
        hareketliParcalar(bakisYaw);
        gozleriGuncelle();
    }

    /** Figurun olmasi gereken yer; ortama gore degisir. */
    private Location hedefKonum(World w) {
        Location o = hedef.getLocation();
        double rad = Math.toRadians(yawFiltre);
        Vector ileri = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        Location l;
        switch (w.getEnvironment()) {
            case NETHER -> {
                // tavan yuzunden normal konum olmaz: lav denizinin ustunde, uzakta, alcakta
                l = o.clone().add(ileri.multiply(plugin.getConfig().getDouble("varlik.nether.ileri")));
                l.setY(plugin.getConfig().getDouble("varlik.nether.y"));
            }
            case THE_END -> {
                l = o.clone().add(ileri.multiply(plugin.getConfig().getDouble("varlik.end.ileri")));
                l.setY(o.getY() + plugin.getConfig().getDouble("varlik.end.yukseklik"));
            }
            default -> l = o.clone().add(ileri.multiply(plugin.getConfig().getDouble("varlik.ileri")))
                    .add(0, plugin.getConfig().getDouble("varlik.yukseklik"), 0);
        }
        int ust = w.getMaxHeight() - 1;
        if (l.getY() > ust) l.setY(ust);
        return l;
    }

    private void rastgeleAnimasyonlar() {
        if (tik >= sonrakiIsaret) {
            isaret();
            sonrakiIsaret = rastgeleAralik("varlik.isaret_aralik_sn");
        }
        if (tik >= sonrakiGlitch) {
            glitch();
            sonrakiGlitch = rastgeleAralik("varlik.glitch_aralik_sn");
        }
    }

    /** Su andan itibaren [min, max] sn sonrasinin tik degeri. */
    private long rastgeleAralik(String anahtar) {
        List<Integer> a = plugin.getConfig().getIntegerList(anahtar);
        int min = a.get(0);
        int max = a.get(1);
        return tik + 20L * (min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min + 1)));
    }

    /** Kollar, cuppe puskulleri ve glitch pikselleri. Yalniz her 2. guncellemede (paket tasarrufu). */
    private void hareketliParcalar(float bakisYaw) {
        if ((tik / plugin.getConfig().getLong("varlik.guncelleme_tik")) % 2 != 0) return;
        boolean isarette = tik < isaretBitis;
        boolean glitchte = tik < glitchBitis;
        Quaternionf sag = new Quaternionf().rotateZ((float) (0.04 * Math.sin(tik * 0.07)));
        Quaternionf sol = new Quaternionf().rotateZ((float) (-0.04 * Math.sin(tik * 0.07 + 1.3)));
        if (isarette) {
            // sag kol yana kalkar ve secilen yone doner
            sag = new Quaternionf().rotateY((float) Math.toRadians(isaretYaw - bakisYaw)).rotateZ((float) Math.toRadians(110));
        }
        for (Parca p : parcalar) {
            if (p.grup() == KOL_SAG) uygula(p, sag);
            else if (p.grup() == KOL_SOL) uygula(p, sol);
            else if (p.grup() >= PUSKUL) {
                int j = p.grup() - PUSKUL;
                uygula(p, new Quaternionf().rotateZ((float) (0.14 * Math.sin(tik * 0.11 + j * 1.7))));
            }
        }
        piksellerYerlestir(glitchte ? 1.0 : plugin.getConfig().getDouble("varlik.piksel_gorunurluk"));
    }

    private void gozleriGuncelle() {
        int seviye = 13;
        if (konusuyor) {
            seviye = (int) Math.round(5 + 10 * Math.max(konusSeviye, 0.5 + 0.5 * Math.sin(tik * 0.8)));
        }
        Display.Brightness b = new Display.Brightness(Math.min(15, seviye), Math.min(15, seviye));
        if (gozResim != null) gozResim.setBrightness(b);
        for (BlockDisplay g : parlayanlar) {
            if (g.getBlock().getMaterial() == Material.RED_CONCRETE) g.setBrightness(b);
        }
    }
}
