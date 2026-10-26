package yz.tuzak.aday;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Her dusunmeden once 5-6 aday nokta uretir. Sadece yuklu chunk'lara bakar, dunya uretmez.
 * Ana thread'de calismali (Bukkit blok API'si); is kucuk ve sinirli.
 */
public final class AdayUretici {

    private static final String[] PUSULA = {"K", "KD", "D", "GD", "G", "GB", "B", "KB"};
    private static final BlockFace[] YATAY = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final FileConfiguration c;
    private final Set<Material> cevherler = new HashSet<>();

    public AdayUretici(FileConfiguration c) {
        this.c = c;
        for (String ad : c.getStringList("gozlem.degerli_cevherler")) {
            Material m = Material.matchMaterial(ad);
            if (m != null) cevherler.add(m);
        }
    }

    /**
     * @param gidis        gidis yonu vektoru (dx, dz); null ise oyuncunun baktigi yon
     * @param aktifTuzaklar aktif tuzak merkezleri (yakinlik filtresi)
     * @param yerler       yatak, sandik, portal gibi geri donulecek yerler
     */
    public List<Aday> uret(Player p, double[] gidis, List<Location> aktifTuzaklar, List<OnemliYer> yerler) {
        Location oyuncu = p.getLocation();
        World w = oyuncu.getWorld();
        double yon = gidis != null && (gidis[0] != 0 || gidis[1] != 0)
                ? Math.atan2(gidis[1], gidis[0])
                : Math.toRadians(oyuncu.getYaw() + 90);
        double[] ileri = {Math.cos(yon), Math.sin(yon)};

        List<Aday> ham = new ArrayList<>();
        yuruyusYonu(p, w, ileri, ham);
        yapilar(p, w, ham);
        cevherDamarlari(p, w, ham);
        for (OnemliYer y : yerler) {
            if (!y.konum().getWorld().equals(w)) continue;
            Aday a = olustur(p, y.konum().clone(), y.tur(), null, false);
            if (a != null) ham.add(a);
        }

        double min = c.getDouble("min_mesafe");
        double maks = c.getDouble("maks_mesafe");
        double tuzakMin = c.getDouble("aday.aktif_tuzak_min_mesafe");
        double ayrim = c.getDouble("aday.ayrim_mesafe");
        List<Aday> uygun = new ArrayList<>();
        for (Aday a : ham) {
            if (a.mesafe < min || a.mesafe > maks) continue;
            if (!w.isChunkLoaded(a.konum.getBlockX() >> 4, a.konum.getBlockZ() >> 4)) continue;
            if (Arazi.gorunuyor(p, a.konum, c.getDouble("aday.gorus_acisi_derece"))) continue;
            boolean yakin = false;
            for (Location t : aktifTuzaklar) {
                if (t.getWorld().equals(w) && t.distance(a.konum) < tuzakMin) {
                    yakin = true;
                    break;
                }
            }
            if (yakin) continue;
            a.olasilik = skor(a, ileri, oyuncu);
            uygun.add(a);
        }

        uygun.sort(Comparator.comparingDouble((Aday a) -> a.olasilik).reversed());
        int adet = c.getInt("aday.adet");
        int maksTur = c.getInt("aday.maks_ayni_tur");
        List<Aday> secilen = new ArrayList<>();
        Map<String, Integer> turSayac = new HashMap<>();
        for (Aday a : uygun) {
            if (secilen.size() >= adet) break;
            if (turSayac.getOrDefault(a.tur, 0) >= maksTur) continue;
            boolean cakisma = false;
            for (Aday s : secilen) {
                if (s.konum.distance(a.konum) < ayrim) {
                    cakisma = true;
                    break;
                }
            }
            if (cakisma) continue;
            turSayac.merge(a.tur, 1, Integer::sum);
            secilen.add(a);
        }
        for (int i = 0; i < secilen.size(); i++) secilen.get(i).etiket = String.valueOf((char) ('A' + i));
        return secilen;
    }

    // ---- kaynaklar ----

    private void yuruyusYonu(Player p, World w, double[] ileri, List<Aday> cikti) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double min = c.getDouble("min_mesafe");
        double maks = c.getDouble("maks_mesafe");
        double koni = Math.toRadians(c.getDouble("aday.yuruyus_konisi_derece"));
        double temel = Math.atan2(ileri[1], ileri[0]);
        Location o = p.getLocation();
        int kolonUst = c.getInt("aday.kolon_ust");
        int kolonAlt = c.getInt("aday.kolon_alt");
        int pencere = c.getInt("aday.kolon_pencere");
        for (int i = 0; i < c.getInt("aday.deneme"); i++) {
            double aci = temel + (r.nextDouble() * 2 - 1) * koni;
            double d = min + r.nextDouble() * (maks - min);
            int x = (int) Math.floor(o.getX() + Math.cos(aci) * d);
            int z = (int) Math.floor(o.getZ() + Math.sin(aci) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int hedefY;
            if (w.getEnvironment() == World.Environment.NETHER) {
                hedefY = w.getMinHeight() + 32 + r.nextInt(80);
            } else if (r.nextBoolean()) {
                hedefY = w.getHighestBlockYAt(x, z);
            } else {
                hedefY = o.getBlockY() - r.nextInt(kolonAlt) + r.nextInt(kolonUst);
            }
            Location yer = yuruneBul(w, x, z, hedefY + pencere, hedefY - pencere);
            if (yer == null) continue;
            Aday a = olustur(p, yer, "yuruyus_yonu", null, false);
            if (a != null) cikti.add(a);
        }
    }

    private void yapilar(Player p, World w, List<Aday> cikti) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location o = p.getLocation();
        int yaricap = c.getInt("aday.yapi_tarama_yaricap_chunk");
        int cx0 = o.getBlockX() >> 4;
        int cz0 = o.getBlockZ() >> 4;
        Set<String> gorulen = new HashSet<>();
        for (int dx = -yaricap; dx <= yaricap; dx++) {
            for (int dz = -yaricap; dz <= yaricap; dz++) {
                if (!w.isChunkLoaded(cx0 + dx, cz0 + dz)) continue;
                Chunk ch = w.getChunkAt(cx0 + dx, cz0 + dz);
                for (GeneratedStructure gs : ch.getStructures()) {
                    BoundingBox bb = gs.getBoundingBox();
                    String ad = String.valueOf(Registry.STRUCTURE.getKey(gs.getStructure()));
                    if (!gorulen.add(ad + "@" + (int) bb.getMinX() + "," + (int) bb.getMinZ())) continue;
                    for (int i = 0; i < c.getInt("aday.yapi_ornek"); i++) {
                        int x = (int) (bb.getMinX() + r.nextDouble() * bb.getWidthX());
                        int z = (int) (bb.getMinZ() + r.nextDouble() * bb.getWidthZ());
                        if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                        Location yer = yuruneBul(w, x, z, (int) bb.getMaxY(), (int) bb.getMinY());
                        if (yer == null) continue;
                        Aday a = olustur(p, yer, "yapi", ad.replace("minecraft:", ""), true);
                        if (a != null) cikti.add(a);
                        break;
                    }
                }
            }
        }
    }

    private void cevherDamarlari(Player p, World w, List<Aday> cikti) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location o = p.getLocation();
        double maks = c.getDouble("maks_mesafe");
        for (int i = 0; i < c.getInt("aday.cevher_ornek"); i++) {
            int x = (int) (o.getX() + (r.nextDouble() * 2 - 1) * maks);
            int z = (int) (o.getZ() + (r.nextDouble() * 2 - 1) * maks);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = w.getMinHeight() + r.nextInt(Math.max(1, o.getBlockY() + 20 - w.getMinHeight()));
            Block b = w.getBlockAt(x, y, z);
            if (!cevherler.contains(b.getType())) continue;
            for (BlockFace yuz : YATAY) {
                Block yan = b.getRelative(yuz);
                if (yan.isPassable() && !yan.isLiquid() && yan.getRelative(BlockFace.UP).isPassable()) {
                    Aday a = olustur(p, yan.getLocation().add(0.5, 0, 0.5), "cevher_damari", b.getType().name(), false);
                    if (a != null) cikti.add(a);
                    break;
                }
            }
        }
    }

    // ---- yardimcilar ----

    private Location yuruneBul(World w, int x, int z, int yUst, int yAlt) {
        return Arazi.yuruneBul(w, x, z, yUst, yAlt);
    }

    private Aday olustur(Player p, Location yer, String tur, String detay, boolean yapiIci) {
        World w = yer.getWorld();
        Location o = p.getLocation();
        if (!w.isChunkLoaded(yer.getBlockX() >> 4, yer.getBlockZ() >> 4)) return null;
        Block ayak = yer.getBlock();
        Block alti = ayak.getRelative(BlockFace.DOWN);
        String zemin = alti.getType().name();
        String duvar = "yok";
        for (BlockFace yuz : YATAY) {
            Block yan = ayak.getRelative(yuz).getRelative(BlockFace.UP);
            if (yan.isSolid()) {
                duvar = yan.getType().name();
                break;
            }
        }
        String tavan = "acik";
        for (int i = 2; i <= 7; i++) {
            Block ust = ayak.getRelative(0, i, 0);
            if (ust.isSolid()) {
                tavan = ust.getType().name();
                break;
            }
        }
        String ortam;
        if (yapiIci) ortam = "yapi_ici";
        else if (!tavan.equals("acik") || ayak.getLightFromSky() == 0) ortam = "magara";
        else ortam = "acik_alan";
        double dx = yer.getX() - o.getX();
        double dz = yer.getZ() - o.getZ();
        double mesafe = Math.sqrt(dx * dx + (yer.getY() - o.getY()) * (yer.getY() - o.getY()) + dz * dz);
        double aci = Math.toDegrees(Math.atan2(dx, -dz));
        String yon = PUSULA[(int) Math.floorMod(Math.round(aci / 45.0), 8L)];
        return new Aday(tur, detay, yer, mesafe, yon, zemin, duvar, tavan, ortam);
    }

    private double skor(Aday a, double[] ileri, Location oyuncu) {
        double min = c.getDouble("min_mesafe");
        double maks = c.getDouble("maks_mesafe");
        // yon uyumu: gidis yonu ile adaya olan yon arasindaki kosinus
        double dx = a.konum.getX() - oyuncu.getX();
        double dz = a.konum.getZ() - oyuncu.getZ();
        double n = Math.hypot(dx, dz);
        double yonSkoru = n < 1e-6 ? 0.5 : ((dx / n) * ileri[0] + (dz / n) * ileri[1] + 1) / 2;
        double mesafeSkoru = 1 - (a.mesafe - min) / Math.max(1, maks - min) * 0.5;
        double turSkoru = c.getDouble("aday.agirlik." + a.tur, 0.5);
        double toplam = c.getDouble("aday.skor_yon") * yonSkoru
                + c.getDouble("aday.skor_mesafe") * mesafeSkoru
                + c.getDouble("aday.skor_tur") * turSkoru;
        return Math.round(toplam * 100) / 100.0;
    }
}
