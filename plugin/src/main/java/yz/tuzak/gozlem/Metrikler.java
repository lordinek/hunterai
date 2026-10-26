package yz.tuzak.gozlem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import yz.tuzak.log.OyunLogu;
import yz.tuzak.olay.OlayYoneticisi;

/** Davranis metrikleri: ucuz sayaclar ve kayan pencere. LLM'e ham sayilar gider, yorum onun. */
public final class Metrikler {

    private record Ornek(double t, String dunya, double x, double y, double z,
                         boolean egilme, float pitch, double hiz) {}

    private record RotaNoktasi(double t, String dunya, double x, double y, double z) {}

    private record Kazma(double t, int x, int y, int z) {}

    private record BekleyenCevher(double t, double x, double y, double z) {}

    private static final String[] PUSULA = {"K", "KD", "D", "GD", "G", "GB", "B", "KB"};

    private final OyunLogu log;
    private final OlayYoneticisi olaylar;

    // ayarlar
    private final double pencereSn;
    private final double rotaNoktaSn;
    private final double rotaPencereSn;
    private final double yerePitch;
    private final double hareketEsigi;
    private final double dusukCan;
    private final int dusukCanGozlemSn;
    private final double cevherMesafe;
    private final double geriMesafe;
    private final double geriMinYas;
    private final double sandikYaricap;
    private final double sandikPencere;
    private final Set<Material> kritik = new HashSet<>();
    private final Set<Material> cevherler = new HashSet<>();
    private final Set<Material> sandiklar = new HashSet<>();

    // kayan pencere
    private final Deque<Ornek> pencere = new ArrayDeque<>();
    private final Deque<RotaNoktasi> rota = new ArrayDeque<>();
    private final Deque<Kazma> sonKirmalar = new ArrayDeque<>();
    private final Map<String, Deque<Double>> pencereli = new HashMap<>();
    private final Map<String, Long> toplam = new LinkedHashMap<>();

    // toplam saniye sayaclari
    private long sn, hareketSn, egilmeYuruSn, yereSn, geceSn, geceHareketSn;
    private double duraksamaSuresi, enUzunDuraksama;
    private Ornek onceki;
    private double sonRotaT = -1e9;
    private double sonGeriDonusT = -1e9;
    private Kazma sonKazma;

    // cevher ve sandik
    private final Map<String, Double> cevherGorulme = new HashMap<>();
    private final List<BekleyenCevher> bekleyenCevher = new ArrayList<>();
    private final Map<String, Double> sandikGorulme = new HashMap<>();
    private final List<Map<String, Object>> sandikAcmalari = new ArrayList<>();

    // dusuk can gozlemi
    private double oncekiCan = 20;
    private double dusukCanBas = -1;
    private Location dusukCanKonum;
    private long yemekBas, saldiriBas, blokBas;

    // envanter
    private final Set<Material> gorulenEsya = new HashSet<>();
    private final Deque<String> sonCraftlar = new ArrayDeque<>();

    public Metrikler(FileConfiguration c, OyunLogu log, OlayYoneticisi olaylar) {
        this.log = log;
        this.olaylar = olaylar;
        pencereSn = c.getDouble("gozlem.pencere_sn");
        rotaNoktaSn = c.getDouble("gozlem.rota_nokta_sn");
        rotaPencereSn = c.getDouble("gozlem.rota_pencere_sn");
        yerePitch = c.getDouble("gozlem.yere_bakma_pitch");
        hareketEsigi = c.getDouble("gozlem.hareket_esigi");
        dusukCan = c.getDouble("gozlem.dusuk_can_esigi");
        dusukCanGozlemSn = c.getInt("gozlem.dusuk_can_gozlem_sn");
        cevherMesafe = c.getDouble("gozlem.cevher_bakis_mesafe");
        geriMesafe = c.getDouble("gozlem.geri_donus_mesafe");
        geriMinYas = c.getDouble("gozlem.geri_donus_min_yas_sn");
        sandikYaricap = c.getDouble("gozlem.sandik_kirma_yaricap");
        sandikPencere = c.getDouble("gozlem.sandik_kirma_pencere_sn");
        malzemeleriYukle(c.getStringList("gozlem.kritik_esyalar"), kritik);
        malzemeleriYukle(c.getStringList("gozlem.degerli_cevherler"), cevherler);
        malzemeleriYukle(c.getStringList("gozlem.sandik_tipleri"), sandiklar);
    }

    private static void malzemeleriYukle(List<String> adlar, Set<Material> hedef) {
        for (String ad : adlar) {
            Material m = Material.matchMaterial(ad);
            if (m != null) hedef.add(m);
        }
    }

    /** Saniyede bir cagrilir. */
    public void saniyeTik(Player p) {
        double t = log.saniye();
        Location l = p.getLocation();
        String dunya = l.getWorld().getName();
        double hiz = 0;
        double dt = 0;
        if (onceki != null && onceki.dunya().equals(dunya) && t > onceki.t()) {
            dt = t - onceki.t();
            hiz = Math.hypot(l.getX() - onceki.x(), l.getZ() - onceki.z()) / dt;
        }
        Ornek o = new Ornek(t, dunya, l.getX(), l.getY(), l.getZ(), p.isSneaking(), l.getPitch(), hiz);
        onceki = o;
        pencere.addLast(o);
        while (!pencere.isEmpty() && pencere.peekFirst().t() < t - pencereSn) pencere.pollFirst();

        sn++;
        boolean hareketli = hiz > hareketEsigi;
        if (hareketli) {
            hareketSn++;
            if (o.egilme()) egilmeYuruSn++;
            duraksamaSuresi = 0;
        } else {
            duraksamaSuresi += dt;
            if (duraksamaSuresi > enUzunDuraksama) enUzunDuraksama = duraksamaSuresi;
        }
        if (l.getPitch() > yerePitch) yereSn++;
        World w = l.getWorld();
        if (w.getEnvironment() == World.Environment.NORMAL) {
            long saat = w.getTime();
            if (saat >= 13000 && saat < 23000) {
                geceSn++;
                if (hareketli) geceHareketSn++;
            }
        }

        rotaGuncelle(t, dunya, l);
        envanterTara(p);
        bakisTara(p, t, l);
        dusukCanIzle(p, t, l);
    }

    private void rotaGuncelle(double t, String dunya, Location l) {
        if (t - sonRotaT < rotaNoktaSn) return;
        sonRotaT = t;
        if (t - sonGeriDonusT >= 60) {
            for (RotaNoktasi eski : rota) {
                if (t - eski.t() >= geriMinYas && eski.dunya().equals(dunya)
                        && Math.hypot(eski.x() - l.getX(), eski.z() - l.getZ()) <= geriMesafe) {
                    sayac("geri_donus");
                    sonGeriDonusT = t;
                    break;
                }
            }
        }
        rota.addLast(new RotaNoktasi(t, dunya, l.getX(), l.getY(), l.getZ()));
        double tutma = Math.max(rotaPencereSn, geriMinYas + 60);
        while (!rota.isEmpty() && rota.peekFirst().t() < t - tutma) rota.pollFirst();
    }

    private void envanterTara(Player p) {
        for (Material m : kritik) {
            if (!gorulenEsya.contains(m) && p.getInventory().contains(m)) {
                gorulenEsya.add(m);
                olaylar.kaydet("kritik_esya", Map.of("esya", m.name()));
            }
        }
    }

    private void bakisTara(Player p, double t, Location l) {
        RayTraceResult r = p.rayTraceBlocks(cevherMesafe);
        if (r != null && r.getHitBlock() != null) {
            Block b = r.getHitBlock();
            String anahtar = anahtar(b.getLocation());
            if (cevherler.contains(b.getType())) {
                Double son = cevherGorulme.get(anahtar);
                if (son == null || t - son > 60) {
                    sayac("cevher_gordu");
                    bekleyenCevher.add(new BekleyenCevher(t, b.getX(), b.getY(), b.getZ()));
                }
                cevherGorulme.put(anahtar, t);
            } else if (sandiklar.contains(b.getType())) {
                sandikGorulme.putIfAbsent(anahtar, t);
            }
        }
        bekleyenCevher.removeIf(c -> {
            double d = Math.sqrt(Math.pow(l.getX() - c.x(), 2) + Math.pow(l.getY() - c.y(), 2)
                    + Math.pow(l.getZ() - c.z(), 2));
            if (d <= 6) {
                sayac("cevhere_gitti");
                return true;
            }
            return t - c.t() > 30;
        });
    }

    private void dusukCanIzle(Player p, double t, Location l) {
        double can = p.getHealth();
        if (dusukCanBas < 0 && oncekiCan >= dusukCan && can < dusukCan && !p.isDead()) {
            dusukCanBas = t;
            dusukCanKonum = l.clone();
            yemekBas = toplam.getOrDefault("yemek", 0L);
            saldiriBas = toplam.getOrDefault("saldiri", 0L);
            blokBas = toplam.getOrDefault("blok_koyma", 0L);
            olaylar.kaydet("dusuk_can", Map.of("can", Math.round(can * 10) / 10.0));
        } else if (dusukCanBas >= 0 && t - dusukCanBas >= dusukCanGozlemSn) {
            List<String> tepki = new ArrayList<>();
            if (dusukCanKonum.getWorld().equals(l.getWorld()) && dusukCanKonum.distance(l) >= 15) tepki.add("kacti");
            if (toplam.getOrDefault("yemek", 0L) > yemekBas) tepki.add("yedi");
            if (toplam.getOrDefault("saldiri", 0L) > saldiriBas) tepki.add("saldirdi");
            if (toplam.getOrDefault("blok_koyma", 0L) - blokBas >= 3) tepki.add("saklandi");
            if (tepki.isEmpty()) tepki.add("belirsiz");
            for (String s : tepki) sayac("dusuk_can_" + s);
            Map<String, Object> detay = new LinkedHashMap<>();
            detay.put("tepki", tepki);
            detay.put("can_sonra", Math.round(can * 10) / 10.0);
            olaylar.kaydet("dusuk_can_tepki", detay);
            dusukCanBas = -1;
        }
        oncekiCan = can;
    }

    // ---- olay girdileri ----

    public void sayac(String ad) {
        toplam.merge(ad, 1L, Long::sum);
        pencereli.computeIfAbsent(ad, k -> new ArrayDeque<>()).addLast(log.saniye());
    }

    public void kazma(Player p, Block b) {
        double t = log.saniye();
        sonKirmalar.addLast(new Kazma(t, b.getX(), b.getY(), b.getZ()));
        while (!sonKirmalar.isEmpty() && sonKirmalar.peekFirst().t() < t - 60) sonKirmalar.pollFirst();
        ItemStack el = p.getInventory().getItemInMainHand();
        if (!el.getType().name().endsWith("_PICKAXE")) return;
        sayac("kazma_blok");
        if (sonKazma != null && t - sonKazma.t() <= 5) {
            int dy = b.getY() - sonKazma.y();
            boolean yatayHareket = b.getX() != sonKazma.x() || b.getZ() != sonKazma.z();
            if (dy < 0 && yatayHareket) sayac("kazma_merdiven");
            else if (dy < 0) sayac("kazma_asagi");
            else if (dy > 0) sayac("kazma_yukari");
            else sayac("kazma_yatay");
        }
        sonKazma = new Kazma(t, b.getX(), b.getY(), b.getZ());
    }

    public void sandikAcildi(Location loc) {
        double t = log.saniye();
        Double gorulme = sandikGorulme.get(anahtar(loc));
        double acmaSuresi = gorulme == null ? -1 : Math.round((t - gorulme) * 10) / 10.0;
        int kirma = 0;
        for (Kazma k : sonKirmalar) {
            double d = Math.sqrt(Math.pow(k.x() - loc.getX(), 2) + Math.pow(k.y() - loc.getY(), 2)
                    + Math.pow(k.z() - loc.getZ(), 2));
            if (t - k.t() <= sandikPencere && d <= sandikYaricap) kirma++;
        }
        sayac("sandik_acildi");
        Map<String, Object> kayit = new LinkedHashMap<>();
        kayit.put("gorulmeden_acmaya_sn", acmaSuresi);
        kayit.put("cevresini_kirma", kirma);
        sandikAcmalari.add(kayit);
        if (sandikAcmalari.size() > 20) sandikAcmalari.remove(0);
        olaylar.kaydet("sandik_acildi", kayit);
    }

    public void craft(Material m, int adet) {
        sayac("craft");
        sonCraftlar.addLast(m.name() + " x" + adet);
        while (sonCraftlar.size() > 10) sonCraftlar.pollFirst();
        Map<String, Object> detay = new LinkedHashMap<>();
        detay.put("esya", m.name());
        detay.put("adet", adet);
        olaylar.kaydet("craft", detay);
    }

    // ---- ozet ----

    public Map<String, Object> ozet(Player p) {
        double t = log.saniye();
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("oyun_suresi_sn", t);

        long pSn = 0, pHareket = 0, pEgilmeYuru = 0, pYere = 0;
        for (Ornek s : pencere) {
            pSn++;
            if (s.hiz() > hareketEsigi) {
                pHareket++;
                if (s.egilme()) pEgilmeYuru++;
            }
            if (s.pitch() > yerePitch) pYere++;
        }
        Map<String, Object> pen = new LinkedHashMap<>();
        pen.put("sure_sn", pSn);
        pen.put("egilme_yuruyus_orani", oran(pEgilmeYuru, pHareket));
        pen.put("yere_bakma_orani", oran(pYere, pSn));
        pen.put("hareket_orani", oran(pHareket, pSn));
        for (Map.Entry<String, Deque<Double>> e : pencereli.entrySet()) {
            Deque<Double> d = e.getValue();
            while (!d.isEmpty() && d.peekFirst() < t - pencereSn) d.pollFirst();
            pen.put(e.getKey(), d.size());
        }
        o.put("son_pencere", pen);

        Map<String, Object> tum = new LinkedHashMap<>();
        tum.put("sure_sn", sn);
        tum.put("egilme_yuruyus_orani", oran(egilmeYuruSn, hareketSn));
        tum.put("yere_bakma_orani", oran(yereSn, sn));
        tum.put("hareket_orani", oran(hareketSn, sn));
        tum.put("en_uzun_duraksama_sn", Math.round(enUzunDuraksama * 10) / 10.0);
        tum.put("gece_sn", geceSn);
        tum.put("gece_hareket_orani", oran(geceHareketSn, geceSn));
        tum.putAll(toplam);
        o.put("tum_oyun", tum);

        o.put("sandik_acmalari", new ArrayList<>(sandikAcmalari));
        o.put("rota", rotaListesi());
        o.put("gidis_yonu", gidisYonu());
        o.put("son_craftlar", new ArrayList<>(sonCraftlar));
        o.put("envanter", envanter(p));
        return o;
    }

    private List<Map<String, Object>> rotaListesi() {
        List<Map<String, Object>> l = new ArrayList<>();
        double t = log.saniye();
        for (RotaNoktasi r : rota) {
            if (t - r.t() > rotaPencereSn) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", r.t());
            m.put("dunya", r.dunya());
            m.put("x", Math.round(r.x()));
            m.put("y", Math.round(r.y()));
            m.put("z", Math.round(r.z()));
            l.add(m);
        }
        return l;
    }

    private Map<String, Object> gidisYonu() {
        Map<String, Object> m = new LinkedHashMap<>();
        double t = log.saniye();
        RotaNoktasi ilk = null, son = null;
        for (RotaNoktasi r : rota) {
            if (t - r.t() > rotaPencereSn) continue;
            if (ilk == null) ilk = r;
            son = r;
        }
        if (ilk == null || ilk == son || !ilk.dunya().equals(son.dunya())) {
            m.put("yon", "belirsiz");
            m.put("hiz_blok_sn", 0);
            return m;
        }
        double dx = son.x() - ilk.x(), dz = son.z() - ilk.z();
        double aci = Math.toDegrees(Math.atan2(dx, -dz));
        int idx = (int) Math.floorMod(Math.round(aci / 45.0), 8L);
        m.put("yon", PUSULA[idx]);
        m.put("hiz_blok_sn", Math.round(Math.hypot(dx, dz) / (son.t() - ilk.t()) * 100) / 100.0);
        m.put("dy", Math.round(son.y() - ilk.y()));
        return m;
    }

    /** Son rota penceresindeki (dx, dz) gidis vektoru; yoksa null. */
    public double[] gidisVektoru() {
        double t = log.saniye();
        RotaNoktasi ilk = null, son = null;
        for (RotaNoktasi r : rota) {
            if (t - r.t() > rotaPencereSn) continue;
            if (ilk == null) ilk = r;
            son = r;
        }
        if (ilk == null || ilk == son || !ilk.dunya().equals(son.dunya())) return null;
        return new double[]{son.x() - ilk.x(), son.z() - ilk.z()};
    }

    private Map<String, Object> envanter(Player p) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Material k : kritik) {
            int adet = 0;
            for (ItemStack s : p.getInventory().getContents()) {
                if (s != null && s.getType() == k) adet += s.getAmount();
            }
            if (adet > 0) m.put(k.name(), adet);
        }
        return m;
    }

    /** Periyodik metrik logu icin. */
    public void logla(Player p) {
        log.yaz("metrikler.jsonl", "metrik", ozet(p));
    }

    private static double oran(long a, long b) {
        return b == 0 ? 0 : Math.round(a * 1000.0 / b) / 1000.0;
    }

    private static String anahtar(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
