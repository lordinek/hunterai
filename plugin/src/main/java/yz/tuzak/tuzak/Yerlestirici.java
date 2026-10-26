package yz.tuzak.tuzak;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import yz.tuzak.aday.Arazi;

/**
 * Modulleri dunyaya yerlestirir: donus, gomulme kontrolu, gomulu kablo, kamuflaj,
 * butunluk ve gorus kontrolu; hata halinde tamamen geri alir.
 */
public final class Yerlestirici {

    public record Sonuc(boolean tamam, String neden, AktifTuzak tuzak, Donusum tetik, Donusum silah,
                        Set<Long> kablo) {
        static Sonuc hata(String neden) {
            return new Sonuc(false, neden, null, null, null, Set.of());
        }
    }

    private enum Faz { BOS, DESTEK, KABLO, SILAH, TETIK }

    private record Hucre(int x, int y, int z, Faz faz, BlockData veri, String icerik, boolean kritik,
                         boolean yuzey, boolean govde) {}

    private final FileConfiguration c;
    private final ModulKaydi kayit;
    private final Set<Material> teknik = new HashSet<>();
    private final Random rnd = new Random();

    public Yerlestirici(FileConfiguration c, ModulKaydi kayit) {
        this.c = c;
        this.kayit = kayit;
        for (String ad : c.getStringList("tuzak.teknik_bloklar")) {
            Material m = Material.matchMaterial(ad);
            if (m != null) teknik.add(m);
        }
    }

    /**
     * @param capa   tetikleyicinin / fizik tuzaginin ayak hucresi (Ali'nin basacagi yer)
     * @param gelis  tuzaktan Ali'nin geldigi tarafa birim vektor (x, z)
     * @param oyuncu null olabilir (test); doluysa mesafe ve gorus kurallari uygulanir
     */
    public Sonuc kur(Tarif tarif, Location capa, double[] gelis, Player oyuncu) {
        if (!tarif.gecerli()) return Sonuc.hata("tarif_gecersiz");
        World w = capa.getWorld();
        if (oyuncu != null) {
            if (oyuncu.getLocation().distance(capa) < c.getDouble("min_mesafe")) return Sonuc.hata("mesafe");
            if (Arazi.gorunuyor(oyuncu, capa, c.getDouble("aday.gorus_acisi_derece"))) return Sonuc.hata("gorus");
        }
        Modul ana = kayit.al(tarif.tetikleyici() != null ? tarif.tetikleyici() : tarif.fizik());
        Modul sil = kayit.al(tarif.silah());
        Modul yem = kayit.al(tarif.yem());
        if (ana == null || (tarif.silah() != null && sil == null) || (tarif.yem() != null && yem == null)) {
            return Sonuc.hata("modul_yok");
        }
        if (ana.yasakDunya.contains(w.getEnvironment()) || (sil != null && sil.yasakDunya.contains(w.getEnvironment()))) {
            return Sonuc.hata("dunya_yasak");
        }

        int ax = capa.getBlockX(), ay = capa.getBlockY(), az = capa.getBlockZ();
        Donusum d1 = Donusum.gelisten(ax, ay, az, gelis);
        List<Hucre> anaH = planla(ana, d1, ana.tur == Modul.Tur.TETIKLEYICI ? Faz.TETIK : Faz.SILAH);
        if (!yuklu(w, anaH)) return Sonuc.hata("chunk");
        Set<Long> kullanilan = anahtarlar(anaH);
        if (!gomulmeYeterli(w, anaH)) return Sonuc.hata("gomulme");
        if (!bosluklarKapali(w, anaH)) return Sonuc.hata("acik_bosluk");

        List<Hucre> tumu = new ArrayList<>(anaH);
        Donusum d2 = null;
        Set<Long> kablo = new HashSet<>();

        if (sil != null) {
            List<Hucre> silH = null;
            Kablo.Yol yol = null;
            int[] bas = d1.dunya(ana.cikis);
            int R = c.getInt("tuzak.etki_yaricap");
            int altDy = c.getInt("tuzak.etki_dy_alt");
            int ustDy = c.getInt("tuzak.etki_dy_ust");
            List<int[]> adaylar = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                for (int dx = -R; dx <= R; dx++) {
                    for (int dz = -R; dz <= R; dz++) {
                        for (int dy = -altDy; dy <= ustDy; dy++) adaylar.add(new int[]{k, dx, dy, dz});
                    }
                }
            }
            Collections.shuffle(adaylar, rnd);
            int m = c.getInt("maks_kablo");
            Izgara izgara = new Izgara(w, bas[0] - m, bas[1] - m, bas[2] - m, bas[0] + m, bas[1] + m, bas[2] + m);
            record Gecerli(Donusum d, List<Hucre> h, Set<Long> anahtar, int mesafe) {}
            List<Gecerli> gecerliler = new ArrayList<>();
            for (int[] a : adaylar) {
                Donusum yerel = new Donusum(a[0], 0, 0, 0);
                int[] r = yerel.dunya(sil.etki);
                Donusum dd = yerel.capayla(ax + a[1] - r[0], ay + a[2] - r[1], az + a[3] - r[2]);
                List<Hucre> h = planla(sil, dd, Faz.SILAH);
                if (!yuklu(w, h)) continue;
                Set<Long> anahtar = anahtarlar(h);
                if (kesisiyor(anahtar, kullanilan)) continue;
                if (!gomulmeYeterli(w, h)) continue;
                if (!bosluklarKapali(w, h)) continue;
                if (!etkiKosuluTamam(w, sil, dd, anahtar)) continue;
                int[] bit = dd.dunya(sil.giris);
                int mesafe = Math.abs(bas[0] - bit[0]) + Math.abs(bas[1] - bit[1]) + Math.abs(bas[2] - bit[2]);
                if (mesafe > c.getInt("maks_kablo")) continue;
                gecerliler.add(new Gecerli(dd, h, anahtar, mesafe));
            }
            // kisa kablolar daha olasi: once onlari dene
            gecerliler.sort((p1, p2) -> Integer.compare(p1.mesafe(), p2.mesafe()));
            int maksAstar = c.getInt("tuzak.silah_deneme");
            int astar = 0;
            for (Gecerli g : gecerliler) {
                if (astar++ >= maksAstar) break;
                Donusum dd = g.d();
                int[] bit = dd.dunya(sil.giris);
                int[] by = dd.yon(sil.girisYon);
                Set<Long> yasak = new HashSet<>(kullanilan);
                yasak.addAll(g.anahtar());
                Kablo.Yol y = Kablo.bul(izgara, bas, bit, new int[]{by[0], by[2]}, yasak,
                        c.getInt("maks_kablo"), c.getInt("tuzak.kablo_node_siniri"));
                if (y == null) continue;
                if (!tekrarlayiciYerlestir(y)) continue;
                silH = g.h();
                yol = y;
                d2 = dd;
                break;
            }
            if (silH == null) return Sonuc.hata("kablo");
            tumu.addAll(silH);
            tumu.addAll(kabloHucreleri(yol, kablo));
        }

        if (yem != null) {
            List<Hucre> yh = yemYerle(yem, w, d1, kullanilan, tumu);
            if (yh == null) return Sonuc.hata("yem_yeri");
            tumu.addAll(yh);
        }

        // uygulama
        Map<Long, BlockData> geri = new HashMap<>();
        Material camo = kamuflaj(w, ax, ay, az, anahtarlar(tumu));
        tumu.sort((p, q) -> p.faz().compareTo(q.faz()));
        uygula(w, tumu, geri, camo);

        String hata = butunluk(w, tumu);
        if (hata == null) hata = gorunurlukKontrol(w, tumu, ax, ay, az);
        if (hata != null) {
            geriAl(w, geri);
            return Sonuc.hata(hata);
        }
        BoundingBox kutu = null;
        for (Hucre h : tumu) {
            BoundingBox b = new BoundingBox(h.x(), h.y(), h.z(), h.x() + 1, h.y() + 1, h.z() + 1);
            kutu = kutu == null ? b : kutu.union(b);
        }
        String id = Long.toString(System.nanoTime(), 36);
        return new Sonuc(true, null, new AktifTuzak(id, tarif, capa.clone(), kutu), d1, d2, kablo);
    }

    // ---------- planlama ----------

    private List<Hucre> planla(Modul m, Donusum d, Faz normalFaz) {
        List<Hucre> l = new ArrayList<>();
        for (Modul.Blok b : m.bloklar) {
            int[] p = d.dunya(b.x(), b.y(), b.z());
            switch (b.rol()) {
                case BOS -> l.add(new Hucre(p[0], p[1], p[2], Faz.BOS, Material.AIR.createBlockData(), null, false, false, false));
                case GOVDE -> l.add(new Hucre(p[0], p[1], p[2], Faz.DESTEK, null, null, false, false, true));
                default -> l.add(new Hucre(p[0], p[1], p[2], normalFaz, d.veri(Bukkit.createBlockData(b.veri())),
                        b.icerik(), b.rol() == Modul.Rol.KRITIK, b.yuzey(), false));
            }
        }
        return l;
    }

    private static Set<Long> anahtarlar(List<Hucre> l) {
        Set<Long> s = new HashSet<>();
        for (Hucre h : l) s.add(Kablo.anahtar(h.x(), h.y(), h.z()));
        return s;
    }

    private static boolean kesisiyor(Set<Long> a, Set<Long> b) {
        for (Long k : a) if (b.contains(k)) return true;
        return false;
    }

    private static boolean yuklu(World w, List<Hucre> l) {
        for (Hucre h : l) if (!w.isChunkLoaded(h.x() >> 4, h.z() >> 4)) return false;
        return true;
    }

    /** Gomulu olmasi gereken hucrelerin yeterince kismi kati arazide mi? */
    private boolean gomulmeYeterli(World w, List<Hucre> l) {
        int toplam = 0, kati = 0;
        for (Hucre h : l) {
            if (h.yuzey()) continue;
            toplam++;
            if (Arazi.kati(w.getBlockAt(h.x(), h.y(), h.z()))) kati++;
        }
        return toplam == 0 || (double) kati / toplam >= c.getDouble("gomulme_orani");
    }

    /** ACIK_HAVA: etki hucresi ve ustundeki hucre arazide hava olmali (modulun kendi hucresi degilse). */
    private static boolean etkiKosuluTamam(World w, Modul sil, Donusum dd, Set<Long> plan) {
        if (sil.etkiKosulu != Modul.EtkiKosulu.ACIK_HAVA) return true;
        int[] e = dd.dunya(sil.etki);
        for (int dy = 0; dy <= 1; dy++) {
            int x = e[0], y = e[1] + dy, z = e[2];
            if (plan.contains(Kablo.anahtar(x, y, z))) continue;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
            Block b = w.getBlockAt(x, y, z);
            if (Arazi.kati(b) || b.isLiquid()) return false;
        }
        return true;
    }

    /** Oyulacak bos hucreler dis havaya acilmamali. */
    private static boolean bosluklarKapali(World w, List<Hucre> l) {
        Set<Long> plan = anahtarlar(l);
        int[][] komsu = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (Hucre h : l) {
            if (h.faz() != Faz.BOS) continue;
            for (int[] k : komsu) {
                int x = h.x() + k[0], y = h.y() + k[1], z = h.z() + k[2];
                if (plan.contains(Kablo.anahtar(x, y, z))) continue;
                if (!w.isChunkLoaded(x >> 4, z >> 4) || !Arazi.kati(w.getBlockAt(x, y, z))) return false;
            }
        }
        return true;
    }

    // ---------- kablo ----------

    private boolean tekrarlayiciYerlestir(Kablo.Yol y) {
        tekrarlayicilar.clear();
        List<int[]> h = y.hucreler();
        int aralik = c.getInt("tuzak.kablo_tekrarlayici_aralik");
        int son = -1;
        for (int i = 0; i < h.size(); i++) {
            if (i - son < aralik) continue;
            int j = -1;
            for (int t = i; t >= son + 2; t--) {
                if (duzCizgi(h, t)) {
                    j = t;
                    break;
                }
            }
            if (j < 0) return false;
            tekrarlayicilar.add(j);
            son = j;
        }
        return true;
    }

    private final Set<Integer> tekrarlayicilar = new HashSet<>();

    private static boolean duzCizgi(List<int[]> h, int j) {
        if (j < 1 || j > h.size() - 2) return false;
        int[] a = h.get(j - 1), b = h.get(j), n = h.get(j + 1);
        if (a[1] != b[1] || b[1] != n[1]) return false;
        return b[0] - a[0] == n[0] - b[0] && b[2] - a[2] == n[2] - b[2];
    }

    private List<Hucre> kabloHucreleri(Kablo.Yol y, Set<Long> kabloAnahtar) {
        List<Hucre> l = new ArrayList<>();
        List<int[]> h = y.hucreler();
        for (int[] p : h) {
            kabloAnahtar.add(Kablo.anahtar(p[0], p[1], p[2]));
            l.add(new Hucre(p[0], p[1], p[2], Faz.BOS, Material.AIR.createBlockData(), null, false, false, false));
        }
        for (Long k : y.ekHava()) kabloAnahtar.add(k);
        for (int[] e : ekKoordinatlar(h)) {
            l.add(new Hucre(e[0], e[1], e[2], Faz.BOS, Material.AIR.createBlockData(), null, false, false, false));
        }
        // sinyal kaynagindan uzaga degil, silahtan tetikleyiciye dogru dose: tetikleyiciye en son baglan
        for (int i = h.size() - 1; i >= 0; i--) {
            int[] p = h.get(i);
            BlockData d;
            if (tekrarlayicilar.contains(i)) {
                int[] n = h.get(i + 1);
                Repeater r = (Repeater) Material.REPEATER.createBlockData();
                r.setFacing(yuz(-(n[0] - p[0]), -(n[2] - p[2])));
                d = r;
            } else {
                d = Material.REDSTONE_WIRE.createBlockData();
            }
            l.add(new Hucre(p[0], p[1], p[2], Faz.KABLO, d, null, true, false, false));
        }
        return l;
    }

    private static List<int[]> ekKoordinatlar(List<int[]> h) {
        List<int[]> e = new ArrayList<>();
        for (int i = 1; i < h.size(); i++) {
            int[] c = h.get(i - 1), n = h.get(i);
            int dy = n[1] - c[1];
            if (dy == 1) e.add(new int[]{c[0], c[1] + 1, c[2]});
            else if (dy == -1) e.add(new int[]{n[0], n[1] + 1, n[2]});
        }
        return e;
    }

    private static BlockFace yuz(int dx, int dz) {
        if (dx > 0) return BlockFace.EAST;
        if (dx < 0) return BlockFace.WEST;
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    // ---------- yem ----------

    private List<Hucre> yemYerle(Modul yem, World w, Donusum d1, Set<Long> kullanilan, List<Hucre> mevcut) {
        double min = c.getDouble("tuzak.yem_min_mesafe");
        double maks = c.getDouble("tuzak.yem_maks_mesafe");
        for (int i = 0; i < 40; i++) {
            double aci = rnd.nextDouble() * Math.PI * 2;
            double d = min + rnd.nextDouble() * (maks - min);
            int x = (int) Math.floor(d1.ox() + Math.cos(aci) * d);
            int z = (int) Math.floor(d1.oz() + Math.sin(aci) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Location yer = Arazi.yuruneBul(w, x, z, d1.oy() + 6, d1.oy() - 6);
            if (yer == null) continue;
            Donusum dd = d1.capayla(yer.getBlockX(), yer.getBlockY(), yer.getBlockZ());
            List<Hucre> h = planla(yem, dd, Faz.SILAH);
            if (!yuklu(w, h) || kesisiyor(anahtarlar(h), kullanilan)) continue;
            return h;
        }
        return null;
    }

    // ---------- uygulama ----------

    private void uygula(World w, List<Hucre> sirali, Map<Long, BlockData> geri, Material camo) {
        for (Hucre h : sirali) {
            Block b = w.getBlockAt(h.x(), h.y(), h.z());
            long k = Kablo.anahtar(h.x(), h.y(), h.z());
            if (h.govde()) {
                if (Arazi.kati(b)) continue;
                geri.putIfAbsent(k, b.getBlockData());
                b.setBlockData(camo.createBlockData(), true);
                continue;
            }
            geri.putIfAbsent(k, b.getBlockData());
            b.setBlockData(h.veri(), true);
            if (h.icerik() != null) doldur(b, h.icerik());
        }
    }

    private static void doldur(Block b, String icerik) {
        String[] p = icerik.split(":");
        Material m = Material.matchMaterial(p[0]);
        if (m == null || !(b.getState() instanceof Container k)) return;
        k.getInventory().addItem(new ItemStack(m, p.length > 1 ? Integer.parseInt(p[1]) : 1));
    }

    private static void geriAl(World w, Map<Long, BlockData> geri) {
        // anahtardan koordinat cozulemez; geri alma icin hucre listesi ayrica tutulur
        for (Map.Entry<Long, BlockData> e : geri.entrySet()) {
            int[] p = coz(e.getKey());
            Block b = w.getBlockAt(p[0], p[1], p[2]);
            b.setBlockData(e.getValue(), false);
        }
    }

    private static int[] coz(long k) {
        int x = (int) (k >> 38);
        if (x >= (1 << 25)) x -= (1 << 26);
        int z = (int) ((k >> 12) & 0x3FFFFFF);
        if (z >= (1 << 25)) z -= (1 << 26);
        int y = (int) (k & 0xFFF) - 2048;
        return new int[]{x, y, z};
    }

    /** Cevredeki dogal kati bloklardan en cok olani; cevher, dusen blok ve teknik bloklar haric. */
    private Material kamuflaj(World w, int cx, int cy, int cz, Set<Long> plan) {
        Map<Material, Integer> say = new EnumMap<>(Material.class);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (plan.contains(Kablo.anahtar(cx + dx, cy + dy, cz + dz))) continue;
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!Arazi.kati(b)) continue;
                    Material m = eslestir(b.getType());
                    if (m != null) say.merge(m, 1, Integer::sum);
                }
            }
        }
        Material en = null;
        int enSayi = 0;
        for (Map.Entry<Material, Integer> e : say.entrySet()) {
            if (e.getValue() > enSayi) {
                en = e.getKey();
                enSayi = e.getValue();
            }
        }
        if (en != null) return en;
        return switch (w.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> cy < 0 ? Material.DEEPSLATE : Material.STONE;
        };
    }

    private Material eslestir(Material m) {
        String ad = m.name();
        if (ad.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS || ad.endsWith("_CONCRETE_POWDER")
                || m == Material.SAND || m == Material.RED_SAND || m == Material.GRAVEL
                || teknik.contains(m) || m.isInteractable() || m == Material.BEDROCK) return null;
        if (m == Material.GRASS_BLOCK || m == Material.PODZOL || m == Material.MYCELIUM || m == Material.DIRT_PATH
                || m == Material.FARMLAND || m == Material.ROOTED_DIRT) return Material.DIRT;
        if (!m.isOccluding()) return null;
        return m;
    }

    // ---------- dogrulama ----------

    private String butunluk(World w, List<Hucre> l) {
        for (Hucre h : l) {
            if (!h.kritik() || h.veri() == null) continue;
            Material gercek = w.getBlockAt(h.x(), h.y(), h.z()).getType();
            if (gercek != h.veri().getMaterial()) {
                return "butunluk";
            }
        }
        return null;
    }

    /** Ali'nin muhtemel gelis yonlerinden teknik blok gorunuyorsa kurulum reddedilir. */
    private String gorunurlukKontrol(World w, List<Hucre> l, int ax, int ay, int az) {
        Set<Long> havaPlan = new HashSet<>();
        for (Hucre h : l) if (h.faz() == Faz.BOS) havaPlan.add(Kablo.anahtar(h.x(), h.y(), h.z()));
        List<Hucre> hedefler = new ArrayList<>();
        int[][] komsu = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (Hucre h : l) {
            if (h.veri() == null || h.yuzey() || !teknik.contains(h.veri().getMaterial())) continue;
            boolean acik = false;
            for (int[] k : komsu) {
                int x = h.x() + k[0], y = h.y() + k[1], z = h.z() + k[2];
                if (havaPlan.contains(Kablo.anahtar(x, y, z))) continue;
                Block b = w.getBlockAt(x, y, z);
                if (!b.getType().isOccluding()) {
                    acik = true;
                    break;
                }
            }
            if (acik) hedefler.add(h);
        }
        if (hedefler.isEmpty()) return null;

        List<Location> gozler = new ArrayList<>();
        double goz = c.getDouble("tuzak.goz_yuksekligi");
        gozler.add(new Location(w, ax + 0.5, ay + goz, az + 0.5));
        int[][] yonler = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int mesafe : c.getIntegerList("tuzak.gorus_mesafeleri")) {
            for (int[] y : yonler) {
                Location s = Arazi.yuruneBul(w, ax + y[0] * mesafe, az + y[1] * mesafe, ay + 6, ay - 6);
                if (s != null) gozler.add(s.add(0, goz, 0));
            }
        }
        double o = c.getDouble("tuzak.yuz_ornek");
        double[][] nokta = {{0.5, 0.5, 0.5}, {0.5 + o, 0.5, 0.5}, {0.5 - o, 0.5, 0.5},
                {0.5, 0.5 + o, 0.5}, {0.5, 0.5 - o, 0.5}, {0.5, 0.5, 0.5 + o}, {0.5, 0.5, 0.5 - o}};
        for (Hucre h : hedefler) {
            for (Location g : gozler) {
                for (double[] n : nokta) {
                    Vector hedef = new Vector(h.x() + n[0], h.y() + n[1], h.z() + n[2]);
                    Vector yon = hedef.clone().subtract(g.toVector());
                    double mesafe = yon.length();
                    if (mesafe < 1e-6) continue;
                    RayTraceResult r = w.rayTraceBlocks(g, yon.normalize(), mesafe + 0.6,
                            FluidCollisionMode.NEVER, false);
                    if (r != null && r.getHitBlock() != null && r.getHitBlock().getX() == h.x()
                            && r.getHitBlock().getY() == h.y() && r.getHitBlock().getZ() == h.z()) {
                        return "gorunur";
                    }
                }
            }
        }
        return null;
    }
}
