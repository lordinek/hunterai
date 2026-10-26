package yz.tuzak;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Axis;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Oyunun hedefi: uzaktaki kurtulus portalina ulasip girmek. Yapay zeka engellemeye calisir.
 * Portal oyun basinda uzak bir noktada kurulur, Ali'ye pusula ve mesafe cubugu verilir,
 * yaklastikca yapay zekanin mudahale ve tuzak hizi artar. Portala girince bos bir dunyada final sahnesi.
 */
public final class Kurtulus implements Listener {

    private static final String[] YONLER = {"dogu", "guneydogu", "guney", "guneybati", "bati", "kuzeybati", "kuzey", "kuzeydogu"};
    private static final double[] ESIKLER = {600, 250, 100, 40};

    private final YzPlugin plugin;
    private final Oyun oyun;
    private Location portal;
    private double baslangicMesafe = 1;
    private boolean hazir;
    private boolean bitti;
    private BossBar bar;
    private int sonEsik;

    public Kurtulus(YzPlugin plugin, Oyun oyun) {
        this.plugin = plugin;
        this.oyun = oyun;
    }

    public boolean bitti() {
        return bitti;
    }

    /** Portal merkezine 25 blok yaricapta cukur/duvar gibi mudahaleler arazi bozmaz. */
    public boolean portalKoru(int x, int z) {
        return portal != null && !bitti && Math.hypot(x + 0.5 - portal.getX(), z + 0.5 - portal.getZ()) <= 25;
    }

    // ---------- kurulum ----------

    /** Oyun basinda: portal noktasini sec, yapiyi kur, pusula ve cubugu ver. */
    public void kur(Player p) {
        temizle();
        bitti = false;
        hazir = false;
        sonEsik = -1;
        var c = plugin.getConfig();
        double mesafe = c.getDouble("kurtulus.mesafe") + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * c.getDouble("kurtulus.sapma");
        double aci = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        World w = p.getWorld();
        int px = (int) Math.round(p.getLocation().getX() + Math.cos(aci) * mesafe);
        int pz = (int) Math.round(p.getLocation().getZ() + Math.sin(aci) * mesafe);
        portal = new Location(w, px + 1.0, 70, pz + 0.5);
        baslangicMesafe = Math.max(1, mesafe);
        boolean sabit = c.getBoolean("kurtulus.sabit_aktif");
        if (sabit) {
            // onceden kurulmus portal: yeniden insa edilmez, sadece hedef olur
            px = c.getInt("kurtulus.sabit_x");
            pz = c.getInt("kurtulus.sabit_z");
            portal = new Location(w, px + 1.0, c.getInt("kurtulus.sabit_y") + 3, pz + 0.5);
            baslangicMesafe = Math.max(1, Math.hypot(portal.getX() - p.getLocation().getX(), portal.getZ() - p.getLocation().getZ()));
        }

        eskiPusulalariSil(p);
        pusulaVer(p);
        bar = BossBar.bossBar(Component.text("Kurtuluş portalı"), 0f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        p.showBossBar(bar);
        if (sabit) {
            hazir = true;
            return;
        }

        // 3x3 chunk bileti: yapi yazilirken chunk'lar bellekte kalsin
        final int fpx = px, fpz = pz;
        int cx = fpx >> 4, cz = fpz >> 4;
        List<CompletableFuture<Chunk>> isler = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.addPluginChunkTicket(cx + dx, cz + dz, plugin);
                isler.add(w.getChunkAtAsync(cx + dx, cz + dz, true));
            }
        }
        CompletableFuture.allOf(isler.toArray(new CompletableFuture[0])).whenComplete((v, e) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        if (e == null) insa(w, fpx, fpz);
                        else plugin.getLogger().warning("Kurtulus portali chunk'lari yuklenemedi: " + e);
                    } finally {
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.removePluginChunkTicket(cx + dx, cz + dz, plugin);
                    }
                }));
    }

    private void insa(World w, int cx, int cz) {
        int r = plugin.getConfig().getInt("kurtulus.yaricap");
        int y = w.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int ust = Math.min(w.getMaxHeight() - 1, y + 14);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                int x = cx + dx, z = cz + dz;
                for (int yy = y + 1; yy <= ust; yy++) yaz(w, x, yy, z, Material.AIR);
                yaz(w, x, y, z, d > r - 1.2 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.STONE_BRICKS);
                for (int yy = y - 1; yy >= y - 12; yy--) {
                    Block b = w.getBlockAt(x, yy, z);
                    if (!b.isPassable() && !b.isLiquid()) break;
                    b.setType(Material.STONE, false);
                }
            }
        }
        // portal cercevesi (4x5) ve ici
        for (int fx = 0; fx < 4; fx++) {
            for (int fy = 0; fy < 5; fy++) {
                int x = cx - 1 + fx, yy = y + 1 + fy;
                boolean ic = fx >= 1 && fx <= 2 && fy >= 1 && fy <= 3;
                if (!ic) {
                    yaz(w, x, yy, cz, Material.OBSIDIAN);
                } else {
                    Block b = w.getBlockAt(x, yy, cz);
                    b.setType(Material.NETHER_PORTAL, false);
                    Orientable o = (Orientable) b.getBlockData();
                    o.setAxis(Axis.X);
                    b.setBlockData(o, false);
                }
            }
        }
        // koseler: agliyan obsidyen sutunlar
        int[][] koseler = {{-r + 2, -r + 2}, {r - 2, -r + 2}, {-r + 2, r - 2}, {r - 2, r - 2}};
        for (int[] k : koseler) {
            for (int h = 1; h <= 6; h++) yaz(w, cx + k[0], y + h, cz + k[1], h == 6 ? Material.GLOWSTONE : Material.CRYING_OBSIDIAN);
        }
        // isik kulesi: uzaktan gorunsun diye isaret fisegi
        int bx = cx, bz = cz - (r - 2);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) yaz(w, bx + dx, y, bz + dz, Material.IRON_BLOCK);
        yaz(w, bx, y + 1, bz, Material.BEACON);
        for (int yy = y + 2; yy < w.getMaxHeight(); yy++) {
            Block b = w.getBlockAt(bx, yy, bz);
            if (!b.getType().isAir()) b.setType(Material.AIR, false);
        }
        portal = new Location(w, cx + 1.0, y + 3, cz + 0.5);
        hazir = true;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("x", cx);
        d.put("y", y);
        d.put("z", cz);
        d.put("mesafe", Math.round(baslangicMesafe));
        if (oyun.aktif()) oyun.olaylar().kaydet("portal_kuruldu", d);
    }

    private static void yaz(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    private void pusulaVer(Player p) {
        ItemStack it = new ItemStack(Material.COMPASS);
        CompassMeta m = (CompassMeta) it.getItemMeta();
        m.setLodestone(portal);
        m.setLodestoneTracked(false);
        m.displayName(Component.text("Kurtuluş Pusulası"));
        it.setItemMeta(m);
        p.getInventory().addItem(it);
    }

    /** Onceki oyundan kalan (eski portali gosteren) pusulalar silinir. */
    private void eskiPusulalariSil(Player p) {
        ItemStack[] icerik = p.getInventory().getContents();
        for (int i = 0; i < icerik.length; i++) {
            ItemStack it = icerik[i];
            if (it != null && it.getType() == Material.COMPASS && it.getItemMeta() instanceof CompassMeta m && m.hasLodestone()) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    private boolean pusulaVarMi(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == Material.COMPASS && it.getItemMeta() instanceof CompassMeta m && m.hasLodestone()) return true;
        }
        return false;
    }

    // ---------- oyun sirasinda ----------

    private double mesafe(Player p) {
        if (portal == null || !p.getWorld().equals(portal.getWorld())) return Double.MAX_VALUE;
        return Math.hypot(p.getLocation().getX() - portal.getX(), p.getLocation().getZ() - portal.getZ());
    }

    /** Yaklastikca mudahale/tuzak bekleme sureleri bu carpanla kisalir. */
    public double carpan() {
        if (portal == null || bitti || !oyun.aktif() || oyun.hedef() == null) return 1.0;
        double d = mesafe(oyun.hedef());
        if (d <= 100) return 0.35;
        if (d <= 250) return 0.5;
        if (d <= 600) return 0.75;
        return 1.0;
    }

    /** Yaklastikca her mudahale cesidine eklenen hak. */
    public int ekHak() {
        if (portal == null || bitti || !oyun.aktif() || oyun.hedef() == null) return 0;
        double d = mesafe(oyun.hedef());
        if (d <= 250) return 2;
        if (d <= 600) return 1;
        return 0;
    }

    /** Her saniye: cubuk, esik olaylari. */
    public void tik(Player p) {
        if (portal == null || bitti || bar == null) return;
        double d = mesafe(p);
        if (d == Double.MAX_VALUE) {
            bar.name(Component.text("Kurtuluş portalı başka bir boyutta"));
            return;
        }
        bar.name(Component.text("Kurtuluş portalı: " + Math.round(d) + " blok"));
        bar.progress((float) Math.max(0, Math.min(1, 1 - d / baslangicMesafe)));
        for (int i = sonEsik + 1; i < ESIKLER.length; i++) {
            if (d <= ESIKLER[i]) {
                sonEsik = i;
                Map<String, Object> det = new LinkedHashMap<>();
                det.put("mesafe", Math.round(d));
                det.put("esik", (int) ESIKLER[i]);
                oyun.olaylar().kaydet("portal_yakin", det);
            }
        }
    }

    /** LLM durum raporu icin. Koordinat yok, sadece mesafe ve yon. */
    public Map<String, Object> ozet(Player p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (portal == null) return m;
        double d = mesafe(p);
        m.put("hazir", hazir);
        if (d == Double.MAX_VALUE) {
            m.put("durum", "baska_boyutta");
            return m;
        }
        double aci = Math.toDegrees(Math.atan2(portal.getZ() - p.getLocation().getZ(), portal.getX() - p.getLocation().getX()));
        m.put("mesafe", Math.round(d));
        m.put("yon", YONLER[Math.floorMod((int) Math.round(aci / 45.0), 8)]);
        m.put("ilerleme_yuzde", Math.round(Math.max(0, Math.min(1, 1 - d / baslangicMesafe)) * 100));
        m.put("kazanma_yakinligi", d <= 40 ? "cok_yakin" : d <= 150 ? "yakin" : d <= 600 ? "orta" : "uzak");
        return m;
    }

    public void temizle() {
        if (bar != null && oyun.hedef() != null) oyun.hedef().hideBossBar(bar);
        bar = null;
    }

    public void sifirla() {
        temizle();
        portal = null;
        hazir = false;
        bitti = false;
    }

    // ---------- olaylar ----------

    private boolean portalYakin(Player p, Location l) {
        return portal != null && hazir && !bitti && oyun.aktif() && oyun.hedefMi(p)
                && l.getWorld().equals(portal.getWorld())
                && Math.hypot(l.getX() - portal.getX(), l.getZ() - portal.getZ()) <= 7 && Math.abs(l.getY() - portal.getY()) <= 8;
    }

    /** Oyuncu cikip girince Bukkit yeni Player nesnesi verir: hedefi yenile, cubugu ve pusulayi geri ver. */
    @EventHandler
    public void girisYapti(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!oyun.aktif() || !oyun.hedefMi(p)) return;
        oyun.hedefYenile(p);
        if (portal == null || bitti) return;
        if (bar != null) p.showBossBar(bar);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !pusulaVarMi(p)) pusulaVer(p);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void girdi(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom().getBlock().equals(e.getTo().getBlock())) return;
        if (!portalYakin(e.getPlayer(), e.getTo())) return;
        if (e.getTo().getBlock().getType() == Material.NETHER_PORTAL) kurtul(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void portalOlayi(PlayerPortalEvent e) {
        if (!portalYakin(e.getPlayer(), e.getFrom())) return;
        e.setCancelled(true);
        kurtul(e.getPlayer());
    }

    @EventHandler
    public void yenidenDogus(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (portal == null || bitti || !oyun.aktif() || !oyun.hedefMi(p)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !pusulaVarMi(p)) pusulaVer(p);
        }, 5L);
    }

    // ---------- final ----------

    private void kurtul(Player p) {
        if (bitti) return;
        bitti = true;
        oyun.olaylar().kaydet("kurtulus", Map.of("sure_sn", Math.round(oyun.sure())));
        temizle();
        String metin = plugin.getConfig().getString("kapanis.yenilgi_metni");
        if (metin != null && !metin.isBlank()) oyun.konusButceDisi(metin);
        sahne(p);
    }

    /** Savas modu bitisi: konusma yapilmadan dogrudan kapanis sahnesi. */
    public void final_(Player p) {
        if (bitti) return;
        bitti = true;
        oyun.olaylar().kaydet("kurtulus", Map.of("sure_sn", Math.round(oyun.sure()), "yol", "savas"));
        temizle();
        sahne(p);
    }

    private void sahne(Player p) {
        World d = finalDunyasi();
        p.setInvulnerable(true);
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 70, 0));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 1f, 1f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.teleport(new Location(d, 0.5, 101, 0.5));
            p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.showTitle(Title.title(Component.text("ÖZGÜRSÜN"), Component.text("Kapı seni başka bir dünyaya taşıdı."),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(5000), Duration.ofMillis(2000))));
        }, 50L);
        long bekleme = plugin.getConfig().getLong("kapanis.ejderha_bekleme_sn") * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.olumAkisi().bitir("kurtulus"), bekleme + 50L);
    }

    /** Bos dunya: yuzen beyaz platform, isik halkasi. */
    private World finalDunyasi() {
        World d = plugin.getServer().getWorld("kurtulus");
        if (d == null) {
            d = new WorldCreator("kurtulus").generator(new BosUretici()).generateStructures(false).createWorld();
        }
        int r = 9;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r + 0.5) continue;
                yaz(d, dx, 100, dz, dist > r - 1.2 ? Material.SEA_LANTERN : Material.WHITE_CONCRETE);
            }
        }
        d.setTime(6000);
        d.setStorm(false);
        return d;
    }

    /** Hicbir sey uretmeyen dunya uretici. */
    private static final class BosUretici extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new Location(world, 0.5, 101, 0.5);
        }
    }
}
