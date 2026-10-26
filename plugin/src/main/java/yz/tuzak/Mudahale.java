package yz.tuzak;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Dogrudan mudahaleler: duvar, mob seli, itme. Tuzak hakkindan bagimsiz, kendi sinirlari var. */
public final class Mudahale {

    /** Ortak (agir) aralik kuralina tabi olmayan hafif cesitler. */
    private static final List<String> ORTAKSIZ = List.of("it", "isinla", "karanlik");

    public static final List<String> CESITLER = List.of("duvar", "mob_sel", "it", "cukur", "yagmur", "lav", "isinla", "karanlik", "kistir");

    private final YzPlugin plugin;
    private final Oyun oyun;
    private final Map<String, Integer> kullanilan = new HashMap<>();
    private final Map<String, Double> sonT = new HashMap<>();
    private double sonAgirT = -1e9;
    private boolean cukurKurulu;
    private double cukurKurulduT;
    private Location onceki;

    public Mudahale(YzPlugin plugin, Oyun oyun) {
        this.plugin = plugin;
        this.oyun = oyun;
    }

    public void sifirla() {
        kullanilan.clear();
        sonT.clear();
        sonAgirT = -1e9;
        cukurKurulu = false;
        onceki = null;
    }

    /** 1: ilk 5 olum, her sey serbest. 2: sonraki olumler, alta lav ve alta bosluk yasak. */
    public int faz() {
        return oyun.olumSayisi() < plugin.getConfig().getInt("mudahale.faz1_olum") ? 1 : 2;
    }

    private boolean fazYasak(String c) {
        return faz() == 2 && c.equals("lav");
    }

    private int maks(String c) {
        int ek = oyun.kurtulus() == null ? 0 : oyun.kurtulus().ekHak();
        return plugin.getConfig().getInt("mudahale." + c + ".maks") + ek;
    }

    private double beklemeSn(String c) {
        double kc = oyun.kurtulus() == null ? 1.0 : oyun.kurtulus().carpan(); // portala yaklastikca bekleme kisalir
        Double son = sonT.get(c);
        double kendi = son == null ? 0 : Math.max(0, plugin.getConfig().getDouble("mudahale." + c + ".bekleme_sn") * kc - (oyun.sure() - son));
        double ortak = ORTAKSIZ.contains(c) ? 0 : Math.max(0, plugin.getConfig().getDouble("mudahale.agir_aralik_sn") * kc - (oyun.sure() - sonAgirT));
        return Math.max(kendi, ortak);
    }

    /** LLM'e giden durum: her cesit icin kalan hak ve bekleme. */
    public Map<String, Object> ozet() {
        Map<String, Object> o = new LinkedHashMap<>();
        for (String c : CESITLER) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kalan", fazYasak(c) ? 0 : maks(c) - kullanilan.getOrDefault(c, 0));
            m.put("bekleme_sn", Math.round(beklemeSn(c)));
            if (c.equals("cukur")) m.put("kurulu", cukurKurulu);
            o.put(c, m);
        }
        return o;
    }

    /** @return hata kodu ya da null (uygulandi) */
    public String uygula(String cesit, Player p) {
        return uygula(cesit, p, false);
    }

    /** @param zorla true ise hak ve bekleme atlanir (elle test komutu) */
    public String uygula(String cesit, Player p, boolean zorla) {
        if (!CESITLER.contains(cesit)) return "cesit_yok";
        if (!zorla && fazYasak(cesit)) return "faz_yasak";
        if (!zorla && kullanilan.getOrDefault(cesit, 0) >= maks(cesit)) return "hak_yok";
        if (!zorla && beklemeSn(cesit) > 0) return "bekleme";
        String hata = switch (cesit) {
            case "duvar" -> duvar(p);
            case "mob_sel" -> mobSel(p);
            case "cukur" -> cukur(p);
            case "yagmur" -> yagmur(p);
            case "lav" -> lav(p);
            case "isinla" -> isinla(p);
            case "karanlik" -> karanlik(p);
            case "kistir" -> kistir(p);
            default -> it(p);
        };
        if (hata != null) return hata;
        kullanilan.merge(cesit, 1, Integer::sum);
        sonT.put(cesit, oyun.sure());
        if (!ORTAKSIZ.contains(cesit)) sonAgirT = oyun.sure();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("tip", "mudahale");
        d.put("cesit", cesit);
        Location l = p.getLocation();
        d.put("dunya", l.getWorld().getName());
        d.put("x", l.getBlockX());
        d.put("y", l.getBlockY());
        d.put("z", l.getBlockZ());
        oyun.log().yaz("tuzaklar.jsonl", "tuzak", d);
        return null;
    }

    private boolean canDusuk(Player p) {
        return p.getHealth() <= plugin.getConfig().getDouble("mudahale.can_esik");
    }

    // ---------- erisim (sikisma kontrolu) ----------

    /** Ali'nin ayak hucresinden gecilebilir hucrelere yayilan tarama; yaricap ya da tavan asilinca durur. */
    private static List<Block> erisilebilir(Location bas, int yaricap, int tavan) {
        World w = bas.getWorld();
        Block b0 = bas.getBlock();
        List<Block> sonuc = new ArrayList<>();
        Set<Long> gorulen = new HashSet<>();
        ArrayDeque<Block> kuyruk = new ArrayDeque<>();
        kuyruk.add(b0);
        gorulen.add(anahtar(b0));
        int[][] yon = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!kuyruk.isEmpty() && sonuc.size() < tavan) {
            Block b = kuyruk.poll();
            sonuc.add(b);
            for (int[] d : yon) {
                Block n = w.getBlockAt(b.getX() + d[0], b.getY() + d[1], b.getZ() + d[2]);
                if (!n.isPassable() || n.isLiquid()) continue;
                int dx = n.getX() - b0.getX(), dy = n.getY() - b0.getY(), dz = n.getZ() - b0.getZ();
                if (dx * dx + dy * dy + dz * dz > yaricap * yaricap) continue;
                if (gorulen.add(anahtar(n))) kuyruk.add(n);
            }
        }
        return sonuc;
    }

    private static long anahtar(Block b) {
        return ((long) (b.getX() & 0x3FFFFFF) << 38) | ((long) (b.getZ() & 0x3FFFFFF) << 12) | ((b.getY() + 2048) & 0xFFF);
    }

    private static boolean ayaktaDurulur(Block b) {
        Block alt = b.getRelative(0, -1, 0);
        return b.getRelative(0, 1, 0).isPassable() && !alt.isPassable() && !alt.isLiquid();
    }

    // ---------- duvar ----------

    private static boolean doldurulabilir(Block b) {
        Material m = b.getType();
        return m.isAir() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m) || b.isPassable() || b.isLiquid();
    }

    /** Bir sutunda duvarla doldurulacak hucreler: tepeden (taban+yuk) asagi, ayak seviyesinin altinda ilk kati zemine kadar. */
    private List<Block> kolon(World w, int bx, int bz, int taban, int yuk) {
        List<Block> sonuc = new ArrayList<>();
        if (oyun.kurtulus() != null && oyun.kurtulus().portalKoru(bx, bz)) return sonuc;
        int alt = Math.max(w.getMinHeight(), taban - 120);
        for (int y = Math.min(taban + yuk, w.getMaxHeight() - 1); y >= alt; y--) {
            Block b = w.getBlockAt(bx, y, bz);
            if (doldurulabilir(b)) sonuc.add(b);
            else if (y <= taban - 1) break;
        }
        return sonuc;
    }

    private String duvar(Player p) {
        double[] v = oyun.metrik().gidisVektoru();
        Vector yon = v != null && Math.hypot(v[0], v[1]) > 3 ? new Vector(v[0], 0, v[1]) : p.getLocation().getDirection().setY(0);
        if (yon.lengthSquared() < 1e-6) return "yon_yok";
        yon.normalize();
        Vector yan = new Vector(-yon.getZ(), 0, yon.getX());
        Location o = p.getLocation();
        World w = o.getWorld();
        int mesafe = plugin.getConfig().getInt("mudahale.duvar.mesafe");
        int yanMaks = plugin.getConfig().getInt("mudahale.duvar.yan_maks");
        int yuk = plugin.getConfig().getInt("mudahale.duvar.yukseklik");
        int yakin = Math.min(yanMaks, 14);
        int taban = o.getBlockY();
        double mx = o.getX() + yon.getX() * mesafe;
        double mz = o.getZ() + yon.getZ() * mesafe;

        // 1. yakin kisim anlik: tunel ve vadi tam kapanir
        List<Block> yerlesecek = new ArrayList<>();
        boolean[] uzayacak = new boolean[2];
        for (int k = 0; k < 2; k++) {
            int isaret = k == 0 ? 1 : -1;
            uzayacak[k] = true;
            for (int i = k == 0 ? 0 : 1; i <= yakin; i++) {
                List<Block> kol = kolon(w, (int) Math.floor(mx + yan.getX() * i * isaret), (int) Math.floor(mz + yan.getZ() * i * isaret), taban, yuk);
                if (kol.isEmpty()) {
                    uzayacak[k] = false;
                    break;
                }
                yerlesecek.addAll(kol);
            }
        }
        if (yerlesecek.size() < 8) return "yer_yok";
        List<BlockData> eski = new ArrayList<>();
        for (Block b : yerlesecek) eski.add(b.getBlockData());
        for (Block b : yerlesecek) b.setType(Material.OBSIDIAN, false);
        // Ali bir cebe kapanip kalmasin: duvardan sonra arkasinda yeterli alan olmali
        if (erisilebilir(o, 14, 260).size() < Math.max(30, plugin.getConfig().getInt("mudahale.min_erisim"))) {
            for (int i = 0; i < yerlesecek.size(); i++) yerlesecek.get(i).setBlockData(eski.get(i), false);
            return "sikisma_riski";
        }
        Location merkez = yerlesecek.get(yerlesecek.size() / 2).getLocation().add(0.5, 0.5, 0.5);
        w.playSound(merkez, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2f, 0.6f);
        w.spawnParticle(Particle.PORTAL, merkez, 120, 3, 2, 3, 0.3);

        // 2. uzak kisim: sutunlar chunk'a gore gruplanir, her chunk yuklenir yuklenmez (bilet tutarak) doldurulur
        if (yanMaks > yakin) {
            java.util.Map<Long, List<int[]>> gruplar = new java.util.LinkedHashMap<>();
            for (int i = yakin + 1; i <= yanMaks; i++) {
                for (int k = 0; k < 2; k++) {
                    if (!uzayacak[k]) continue;
                    int isaret = k == 0 ? 1 : -1;
                    int bx = (int) Math.floor(mx + yan.getX() * i * isaret);
                    int bz = (int) Math.floor(mz + yan.getZ() * i * isaret);
                    gruplar.computeIfAbsent(((long) (bx >> 4) << 32) ^ ((bz >> 4) & 0xFFFFFFFFL), x -> new ArrayList<>()).add(new int[]{bx, bz});
                }
            }
            final ArrayDeque<List<int[]>> kuyruk = new ArrayDeque<>(gruplar.values());
            final java.util.concurrent.atomic.AtomicBoolean mesgul = new java.util.concurrent.atomic.AtomicBoolean(false);
            plugin.getServer().getScheduler().runTaskTimer(plugin, gorev -> {
                if (kuyruk.isEmpty()) {
                    gorev.cancel();
                    return;
                }
                if (mesgul.get()) return;
                List<int[]> grup = kuyruk.poll();
                mesgul.set(true);
                w.getChunkAtAsync(grup.get(0)[0] >> 4, grup.get(0)[1] >> 4).whenComplete((chunk, hata) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (chunk != null) {
                                chunk.addPluginChunkTicket(plugin);
                                for (int[] c : grup) for (Block b : kolon(w, c[0], c[1], taban, yuk)) b.setType(Material.OBSIDIAN, false);
                                chunk.removePluginChunkTicket(plugin);
                            }
                            mesgul.set(false);
                        }));
            }, 1L, 1L);
        }
        return null;
    }

    private static boolean kazmaVarMi(Player p) {
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && (i.getType() == Material.DIAMOND_PICKAXE || i.getType() == Material.NETHERITE_PICKAXE)) return true;
        }
        return false;
    }

    // ---------- mob seli ----------

    private String mobSel(Player p) {
        World w = p.getWorld();
        List<EntityType> tur = switch (w.getEnvironment()) {
            case NETHER -> List.of(EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE);
            case THE_END -> null;
            default -> List.of(EntityType.CREEPER, EntityType.CREEPER, EntityType.CREEPER, EntityType.CREEPER, EntityType.CREEPER, EntityType.CREEPER,
                    EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);
        };
        if (tur == null) return "end_yasak";
        if (canDusuk(p)) return "can_dusuk";
        Location o = p.getLocation();
        // dar yerde ya da cepte az mob: erisilebilir alan kadar
        List<Block> alan = erisilebilir(o, 15, 900);
        int adet = Math.min(plugin.getConfig().getInt("mudahale.mob_sel.adet"), alan.size() / 25);
        if (adet < 3) return "dar_yer";
        int omur = plugin.getConfig().getInt("mudahale.mob_sel.omur_sn");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Block> adayYerler = new ArrayList<>();
        for (Block b : alan) {
            double d = Math.sqrt(b.getLocation().add(0.5, 0, 0.5).distanceSquared(o));
            if (d >= 6 && d <= 14 && ayaktaDurulur(b)) adayYerler.add(b);
        }
        Collections.shuffle(adayYerler, r);
        List<Entity> dogan = new ArrayList<>();
        for (Block b : adayYerler) {
            if (dogan.size() >= adet) break;
            Entity e = w.spawnEntity(b.getLocation().add(0.5, 0, 0.5), tur.get(r.nextInt(tur.size())));
            if (e instanceof Zombie z1) z1.setShouldBurnInDay(false);
            if (e instanceof Skeleton s1) s1.setShouldBurnInDay(false);
            if (e instanceof Mob m) m.setTarget(p);
            e.setPersistent(false);
            dogan.add(e);
        }
        if (dogan.size() < 3) {
            for (Entity e : dogan) e.remove();
            return "yer_yok";
        }
        w.playSound(o, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.7f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Entity e : dogan) if (e.isValid()) e.remove();
        }, omur * 20L);
        return null;
    }

    // ---------- cukur ----------

    /**
     * Cukuru kurar (silahlar). Ali bulundugu chunk'tan cikmasina tetik_mesafe blok kala o chunk'i
     * tamamen dibe kadar oyar. Kurulu kalma suresi omur_sn; tik() her saniye bakar.
     */
    private String cukur(Player p) {
        if (p.getWorld().getEnvironment() != World.Environment.NORMAL) return "sadece_overworld";
        cukurKurulu = true;
        cukurKurulduT = oyun.sure();
        return null;
    }

    /** Test komutu: bulundugu chunk'i hemen oyar. */
    public String cukurSimdi(Player p) {
        if (p.getWorld().getEnvironment() != World.Environment.NORMAL) return "sadece_overworld";
        org.bukkit.util.Vector bakis = p.getLocation().getDirection();
        return cukurAc(p, bakis.getX(), bakis.getZ(), false) ? null : "su_var";
    }

    /** Her saniye (DusunmeDongusu.tik): kurulu cukur icin chunk cikisini izler. */
    public void tik(Player p) {
        Location l = p.getLocation();
        double dx = 0, dz = 0;
        if (onceki != null && onceki.getWorld() == l.getWorld()) {
            dx = l.getX() - onceki.getX();
            dz = l.getZ() - onceki.getZ();
        }
        onceki = l.clone();
        if (!cukurKurulu) return;
        if (oyun.sure() - cukurKurulduT > plugin.getConfig().getDouble("mudahale.cukur.omur_sn")) {
            cukurKurulu = false;
            return;
        }
        double hiz = Math.hypot(dx, dz);
        if (hiz < 2.0 || l.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (l.getWorld().getHighestBlockYAt(l) > l.getBlockY() + 3) return;   // magara/maden/agac alti degil, acik hava
        // faz 1: yarim ihtimalle dogrudan altinda; faz 2: hep kosarken onunde, kacinabilecegi uzaklikta
        boolean alta = faz() == 1 && ThreadLocalRandom.current().nextBoolean();
        if (cukurAc(p, dx / hiz, dz / hiz, alta)) {
            cukurKurulu = false;
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("tip", "mudahale");
            d.put("cesit", "cukur_patladi");
            d.put("dunya", l.getWorld().getName());
            d.put("x", l.getBlockX());
            d.put("y", l.getBlockY());
            d.put("z", l.getBlockZ());
            oyun.log().yaz("tuzaklar.jsonl", "tuzak", d);
        }
    }

    /** Ali'nin onunde (8-22 blok ileri, 11 blok genis) dibe kadar bir cukur oyar; ileri yon (yx, yz) birim vektor. */
    private boolean cukurAc(Player p, double yx, double yz, boolean alta) {
        World w = p.getWorld();
        Location o = p.getLocation();
        double norm = Math.hypot(yx, yz);
        if (norm < 1e-6 && !alta) return false;
        if (norm >= 1e-6) {
            yx /= norm;
            yz /= norm;
        }
        List<int[]> sutunlar = new ArrayList<>();
        if (alta) {
            // faz 1: dogrudan ayaginin altindan, 6 blok yaricapli disk
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (dx * dx + dz * dz > 38) continue;
                    sutunlar.add(new int[]{o.getBlockX() + dx, o.getBlockZ() + dz, (int) Math.round(Math.sqrt(dx * dx + dz * dz))});
                }
            }
        } else {
            for (int f = 8; f < 22; f++) {
                for (int s = -5; s <= 5; s++) {
                    int x = (int) Math.floor(o.getX() + yx * f - yz * s);
                    int z = (int) Math.floor(o.getZ() + yz * f + yx * s);
                    sutunlar.add(new int[]{x, z, f});
                }
            }
        }
        int[] orta = sutunlar.get(sutunlar.size() / 2);
        if (w.getBlockAt(orta[0], w.getHighestBlockYAt(orta[0], orta[1]), orta[1]).isLiquid()) return false;
        if (oyun.kurtulus() != null) sutunlar.removeIf(c -> oyun.kurtulus().portalKoru(c[0], c[1]));
        if (sutunlar.isEmpty()) return false;
        sutunlar.sort(java.util.Comparator.comparingInt(c -> c[2]));
        int sutunTik = plugin.getConfig().getInt("mudahale.cukur.sutun_tik");
        int dip = w.getMinHeight() + (plugin.getConfig().getBoolean("mudahale.cukur.bedrock_kaz") ? 0 : 5);
        final ArrayDeque<int[]> kuyruk = new ArrayDeque<>(sutunlar);
        plugin.getServer().getScheduler().runTaskTimer(plugin, gorev -> {
            for (int n = 0; n < sutunTik && !kuyruk.isEmpty(); n++) {
                int[] c = kuyruk.poll();
                for (int y = w.getHighestBlockYAt(c[0], c[1]); y >= dip; y--) {
                    Block b = w.getBlockAt(c[0], y, c[1]);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
            }
            if (kuyruk.isEmpty()) gorev.cancel();
        }, 1L, 1L);
        w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        w.playSound(o, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.6f);
        w.spawnParticle(Particle.EXPLOSION, o, 10, 5, 1, 5, 0);
        return true;
    }

    // ---------- yagmur ----------

    /** Basinin ustunden orsler, yakininda yildirimlar. Kapali yerde (tavan alcak) cikarmaz. */
    private String yagmur(Player p) {
        World w = p.getWorld();
        if (w.getEnvironment() == World.Environment.THE_END) return "end_yasak";
        Location o = p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int adet = plugin.getConfig().getInt("mudahale.yagmur.ors");
        List<Location> noktalar = new ArrayList<>();
        for (int i = 0; i < adet * 3 && noktalar.size() < adet; i++) {
            double aci = r.nextDouble() * Math.PI * 2;
            double d = r.nextDouble() * 5;
            int bx = (int) Math.floor(o.getX() + Math.cos(aci) * d);
            int bz = (int) Math.floor(o.getZ() + Math.sin(aci) * d);
            int y = o.getBlockY() + 1;
            int bos = 0;
            while (bos < 22 && w.getBlockAt(bx, y + bos, bz).isPassable()) bos++;
            if (bos < 8) continue;
            noktalar.add(new Location(w, bx + 0.5, y + bos - 1, bz + 0.5));
        }
        if (noktalar.size() < 3) return "kapali_yer";
        for (int i = 0; i < noktalar.size(); i++) {
            Location l = noktalar.get(i);
            boolean tnt = i % 2 == 1;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (tnt) {
                    TNTPrimed t = w.spawn(l, TNTPrimed.class);
                    t.setFuseTicks(45 + ThreadLocalRandom.current().nextInt(25));
                    return;
                }
                FallingBlock fb = w.spawnFallingBlock(l, Material.ANVIL.createBlockData());
                fb.setHurtEntities(true);
                fb.setDropItem(false);
            }, i * 4L);
        }
        for (int i = 0; i < 3; i++) {
            double aci = r.nextDouble() * Math.PI * 2;
            double d = 4 + r.nextDouble() * 5;
            Location y = new Location(w, o.getX() + Math.cos(aci) * d, o.getY(), o.getZ() + Math.sin(aci) * d);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> w.strikeLightning(y), 10L + i * 12L);
        }
        w.playSound(o, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 0.6f);
        return null;
    }

    // ---------- lav ----------

    /** Ayaginin alti birkac saniyeligine lav olur, sonra eski haline doner. */
    private String lav(Player p) {
        World w = p.getWorld();
        if (w.getEnvironment() == World.Environment.NETHER) return "nether_yasak";
        Location o = p.getLocation();
        Block taban = w.getBlockAt(o.getBlockX(), o.getBlockY() - 1, o.getBlockZ());
        if (taban.isPassable() || taban.isLiquid()) return "havada";
        int yaricap = plugin.getConfig().getInt("mudahale.lav.yaricap");
        int omur = plugin.getConfig().getInt("mudahale.lav.omur_sn");
        List<Block> blok = new ArrayList<>();
        List<BlockData> eski = new ArrayList<>();
        for (int dx = -yaricap; dx <= yaricap; dx++) {
            for (int dz = -yaricap; dz <= yaricap; dz++) {
                if (dx * dx + dz * dz > yaricap * yaricap + 1) continue;
                for (int dy = -1; dy >= -2; dy--) {
                    Block b = w.getBlockAt(o.getBlockX() + dx, o.getBlockY() + dy, o.getBlockZ() + dz);
                    if (b.isPassable() || b.isLiquid() || b.getType() == Material.BEDROCK) continue;
                    blok.add(b);
                    eski.add(b.getBlockData());
                }
            }
        }
        if (blok.size() < 4) return "yer_yok";
        for (Block b : blok) b.setType(Material.LAVA, false);
        w.playSound(o, Sound.BLOCK_LAVA_POP, 3f, 0.5f);
        w.playSound(o, Sound.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
        w.spawnParticle(Particle.LAVA, o, 40, 1.5, 0.3, 1.5, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < blok.size(); i++) blok.get(i).setBlockData(eski.get(i), false);
        }, omur * 20L);
        return null;
    }

    // ---------- isinla ----------

    private static boolean guvenliZemin(Block zemin) {
        Material m = zemin.getType();
        return zemin.isSolid() && !zemin.isLiquid() && m != Material.MAGMA_BLOCK && m != Material.CACTUS
                && m != Material.CAMPFIRE && m != Material.SOUL_CAMPFIRE && m != Material.POWDER_SNOW;
    }

    /** Yuzeyde, 20-40 blok uzaga rastgele bir yone ishinlar: yon kaybi. */
    private String isinla(Player p) {
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) return "sadece_overworld";
        Location o = p.getLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int min = plugin.getConfig().getInt("mudahale.isinla.min_mesafe");
        int maks = plugin.getConfig().getInt("mudahale.isinla.maks_mesafe");
        for (int deneme = 0; deneme < 40; deneme++) {
            double aci = r.nextDouble() * Math.PI * 2;
            double d = min + r.nextDouble() * (maks - min);
            int bx = (int) Math.floor(o.getX() + Math.cos(aci) * d);
            int bz = (int) Math.floor(o.getZ() + Math.sin(aci) * d);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            int y = w.getHighestBlockYAt(bx, bz);
            Block zemin = w.getBlockAt(bx, y, bz);
            if (!guvenliZemin(zemin)) continue;
            Location hedef = new Location(w, bx + 0.5, y + 1, bz + 0.5, o.getYaw(), o.getPitch());
            w.spawnParticle(Particle.PORTAL, o.clone().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.8);
            w.playSound(o, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.5f);
            p.teleport(hedef);
            w.spawnParticle(Particle.PORTAL, hedef.clone().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.8);
            w.playSound(hedef, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.5f);
            return null;
        }
        return "yer_yok";
    }

    // ---------- karanlik ----------

    /** Gece olur, firtina baslar, Ali'ye darkness efekti verilir. */
    private String karanlik(Player p) {
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) return "sadece_overworld";
        int sure = plugin.getConfig().getInt("mudahale.karanlik.sure_sn");
        long saat = w.getTime();
        if (saat < 13000 || saat > 23000) w.setTime(14000);
        w.setStorm(true);
        w.setThundering(true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, sure * 20, 0, false, false, false));
        w.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 3f, 0.5f);
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.6f);
        return null;
    }

    // ---------- kistir ----------

    /**
     * Madende/magarada: gerisindeki tuneli obsidyenle kapatir, onden bir dalga mob gonderir.
     * Duvar sizdirmaz degil, Ali kazarak cikabilir; arkada 40 hucreden az alan kalirsa geri alinir.
     */
    private String kistir(Player p) {
        World w = p.getWorld();
        Location o = p.getLocation();
        if (w.getHighestBlockYAt(o) <= o.getBlockY() + 6) return "yuzeyde";
        if (w.getEnvironment() == World.Environment.THE_END) return "end_yasak";
        if (canDusuk(p)) return "can_dusuk";
        // ileri yon: son rota, yoksa bakis
        double[] v = oyun.metrik().gidisVektoru();
        Vector ileri = v != null && Math.hypot(v[0], v[1]) > 2 ? new Vector(v[0], 0, v[1]) : o.getDirection().setY(0);
        if (ileri.lengthSquared() < 1e-6) return "yon_yok";
        ileri.normalize();
        Vector geri = ileri.clone().multiply(-1);
        Vector yan = new Vector(-geri.getZ(), 0, geri.getX());
        int taban = o.getBlockY();
        int kalinlik = plugin.getConfig().getInt("mudahale.kistir.kalinlik");
        int mesafe = plugin.getConfig().getInt("mudahale.kistir.mesafe");
        List<Block> tikac = new ArrayList<>();
        for (int s = mesafe; s < mesafe + kalinlik; s++) {
            for (int t = -4; t <= 4; t++) {
                for (int h = -2; h <= 4; h++) {
                    int bx = (int) Math.floor(o.getX() + geri.getX() * s + yan.getX() * t);
                    int bz = (int) Math.floor(o.getZ() + geri.getZ() * s + yan.getZ() * t);
                    Block b = w.getBlockAt(bx, taban + h, bz);
                    if ((b.isPassable() || b.isLiquid()) && !tikac.contains(b)) tikac.add(b);
                }
            }
        }
        if (tikac.size() < 3) return "tunel_yok";
        List<BlockData> eski = new ArrayList<>();
        for (Block b : tikac) eski.add(b.getBlockData());
        for (Block b : tikac) b.setType(Material.OBSIDIAN, false);
        List<Block> alan = erisilebilir(o, 15, 900);
        if (alan.size() < Math.max(40, plugin.getConfig().getInt("mudahale.min_erisim"))) {
            for (int i = 0; i < tikac.size(); i++) tikac.get(i).setBlockData(eski.get(i), false);
            return "sikisma_riski";
        }
        // mob dalgasi sadece onden, tikacin oldugu tarafa degil
        int adet = Math.min(plugin.getConfig().getInt("mudahale.kistir.adet"), alan.size() / 20);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<EntityType> tur = List.of(EntityType.CREEPER, EntityType.CREEPER, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);
        List<Block> adayYerler = new ArrayList<>();
        for (Block b : alan) {
            Vector f = new Vector(b.getX() + 0.5 - o.getX(), 0, b.getZ() + 0.5 - o.getZ());
            double d = f.length();
            if (d >= 5 && d <= 14 && f.dot(ileri) > 0 && ayaktaDurulur(b)) adayYerler.add(b);
        }
        Collections.shuffle(adayYerler, r);
        int omur = plugin.getConfig().getInt("mudahale.kistir.omur_sn");
        List<Entity> dogan = new ArrayList<>();
        for (Block b : adayYerler) {
            if (dogan.size() >= adet) break;
            Entity e = w.spawnEntity(b.getLocation().add(0.5, 0, 0.5), tur.get(r.nextInt(tur.size())));
            if (e instanceof Mob m) m.setTarget(p);
            e.setPersistent(false);
            dogan.add(e);
        }
        Location merkez = tikac.get(tikac.size() / 2).getLocation().add(0.5, 0.5, 0.5);
        w.playSound(merkez, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.4f);
        w.playSound(o, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.7f);
        w.spawnParticle(Particle.EXPLOSION, merkez, 4, 1.5, 1.5, 1.5, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Entity e : dogan) if (e.isValid()) e.remove();
        }, omur * 20L);
        return null;
    }

    // ---------- itme ----------

    /** (x, z) sutununda ayak seviyesinden asagi bos derinlik; en cok 'tavan'. */
    private static int bosDerinlik(World w, int bx, int bz, int ayak, int tavan) {
        int n = 0;
        for (int y = ayak - 1; y >= ayak - tavan; y--) {
            if (!w.getBlockAt(bx, y, bz).isPassable()) break;
            n++;
        }
        return n;
    }

    /** Arkadan vurup dusurme: once onunde (bakis yonunun etrafinda), sonra her yonde en yakin ucurumu arar. */
    private String it(Player p) {
        if (canDusuk(p)) return "can_dusuk";
        Location o = p.getLocation();
        World w = o.getWorld();
        int ayak = o.getBlockY();
        Vector bakis = o.getDirection().setY(0);
        double bakisAci = bakis.lengthSquared() < 1e-6 ? 0 : Math.atan2(bakis.getZ(), bakis.getX());
        Vector yon = null;
        double mesafe = 0;
        // 1. on koni (+-60 derece), 2. her yon; en yakin ucurum
        for (int tur = 0; tur < 2 && yon == null; tur++) {
            double enYakin = 1e9;
            int adim = tur == 0 ? 15 : 30;
            int aralik = tur == 0 ? 60 : 180;
            for (int a = -aralik; a <= aralik; a += adim) {
                double aci = bakisAci + Math.toRadians(a);
                Vector d = new Vector(Math.cos(aci), 0, Math.sin(aci));
                for (int dist = 1; dist <= 7; dist++) {
                    int bx = (int) Math.floor(o.getX() + d.getX() * dist);
                    int bz = (int) Math.floor(o.getZ() + d.getZ() * dist);
                    if (!w.getBlockAt(bx, ayak, bz).isPassable() || !w.getBlockAt(bx, ayak + 1, bz).isPassable()) break;
                    if (bosDerinlik(w, bx, bz, ayak, 12) >= 4) {
                        if (dist < enYakin) {
                            enYakin = dist;
                            yon = d.clone();
                            mesafe = dist;
                        }
                        break;
                    }
                }
            }
        }
        Vector hiz;
        if (yon != null) {
            double v = Math.max(0.6, Math.min(1.3, (mesafe + 1.5) / 7.3));
            hiz = yon.multiply(v).setY(0.42);
        } else {
            Vector ileri = o.getDirection().setY(0);
            hiz = (ileri.lengthSquared() < 1e-6 ? new Vector(0, 0, 0) : ileri.normalize().multiply(0.25)).setY(1.35);
        }
        Location efekt = o.clone().add(0, 1, 0);
        w.playSound(o, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 2f, 0.6f);
        w.playSound(o, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.5f, 0.8f);
        w.spawnParticle(Particle.SWEEP_ATTACK, efekt, 1);
        w.spawnParticle(Particle.CRIT, efekt, 30, 0.4, 0.5, 0.4, 0.4);
        p.damage(1.0);
        p.setVelocity(hiz);
        return null;
    }
}
