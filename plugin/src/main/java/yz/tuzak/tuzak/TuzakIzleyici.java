package yz.tuzak.tuzak;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import yz.tuzak.Oyun;
import yz.tuzak.YzPlugin;

/**
 * Kurulu tuzaklarla Ali'nin iliskisini izler: fark edildi (yavaslama, yon degistirme,
 * bakip uzaklasma, tetiklemeden kirma), tetiklendi ve kil payi. Sadece loga yazar.
 */
public final class TuzakIzleyici implements Listener {

    private final YzPlugin plugin;
    private final Oyun oyun;
    private final Deque<Location> gecmis = new ArrayDeque<>();

    public TuzakIzleyici(YzPlugin plugin, Oyun oyun) {
        this.plugin = plugin;
        this.oyun = oyun;
    }

    /** Saniyede bir, hedef oyuncu icin. */
    public void izle(Player p) {
        Location l = p.getLocation();
        gecmis.addLast(l.clone());
        int ornek = plugin.getConfig().getInt("izleme.gecmis_ornek");
        while (gecmis.size() > ornek) gecmis.pollFirst();
        if (gecmis.size() < ornek) return;

        double yakin = plugin.getConfig().getDouble("izleme.yakin_mesafe");
        for (AktifTuzak t : new ArrayList<>(oyun.tuzaklar())) {
            if (!t.durum.equals("kurulu") || !t.konum.getWorld().equals(l.getWorld())) continue;
            double mesafe = l.distance(t.konum);
            List<String> nedenler = new ArrayList<>();
            if (mesafe <= yakin) {
                if (yavasladi(t)) nedenler.add("yavasladi");
                if (yonDegistirdi()) nedenler.add("yon_degistirdi");
            }
            if (bakipUzaklasti(p, t, mesafe)) nedenler.add("bakip_uzaklasti");
            if (!nedenler.isEmpty()) farkEdildi(t, nedenler);
        }
    }

    private boolean yavasladi(AktifTuzak t) {
        List<Location> g = new ArrayList<>(gecmis);
        double once = hiz(g.get(0), g.get(2));
        double simdi = hiz(g.get(g.size() - 3), g.get(g.size() - 1));
        double esik = plugin.getConfig().getDouble("izleme.hiz_esigi");
        // tuzaga yaklasirken hizin belirgin dusmesi
        double oncekiMesafe = g.get(0).distance(t.konum);
        double simdikiMesafe = g.get(g.size() - 1).distance(t.konum);
        return once > esik * 3 && simdi < once * plugin.getConfig().getDouble("izleme.yavaslama_orani")
                && simdikiMesafe <= oncekiMesafe;
    }

    private boolean yonDegistirdi() {
        List<Location> g = new ArrayList<>(gecmis);
        int n = g.size();
        Vector a = g.get(n / 2).toVector().subtract(g.get(0).toVector()).setY(0);
        Vector b = g.get(n - 1).toVector().subtract(g.get(n / 2).toVector()).setY(0);
        double esik = plugin.getConfig().getDouble("izleme.hiz_esigi");
        if (a.length() < esik * (n / 2) || b.length() < esik * (n / 2)) return false;
        double aci = Math.toDegrees(a.angle(b));
        return aci >= plugin.getConfig().getDouble("izleme.yon_degisimi_derece");
    }

    private boolean bakipUzaklasti(Player p, AktifTuzak t, double mesafe) {
        double bakisMesafe = plugin.getConfig().getDouble("izleme.bakis_mesafe");
        if (t.bakisMesafesi < 0) {
            if (mesafe > bakisMesafe) return false;
            Location goz = p.getEyeLocation();
            Vector hedef = t.konum.clone().add(0, 0.5, 0).toVector().subtract(goz.toVector());
            if (hedef.length() < 1e-6) return false;
            double aci = Math.toDegrees(goz.getDirection().angle(hedef));
            if (aci > plugin.getConfig().getDouble("izleme.bakis_acisi_derece")) return false;
            RayTraceResult r = p.getWorld().rayTraceBlocks(goz, hedef.clone().normalize(), hedef.length() - 0.5,
                    FluidCollisionMode.NEVER, true);
            if (r != null && r.getHitBlock() != null) return false;
            t.bakisMesafesi = mesafe;
            return false;
        }
        if (mesafe >= t.bakisMesafesi + plugin.getConfig().getDouble("izleme.uzaklasma_mesafe")) return true;
        if (mesafe < t.bakisMesafesi) t.bakisMesafesi = mesafe;
        return false;
    }

    private static double hiz(Location a, Location b) {
        return Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
    }

    private void farkEdildi(AktifTuzak t, List<String> nedenler) {
        t.durum = "fark_edildi";
        oyunaYaz("tuzak_fark_edildi", t, nedenler);
        for (String id : new String[]{t.tarif.tetikleyici(), t.tarif.silah(), t.tarif.fizik()}) oyun.yasakla(id);
    }

    private void profileBildir(String tip, AktifTuzak t, List<String> nedenler) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("tip", tip);
        o.put("oyun_id", oyun.oyunId());
        o.put("t", oyun.sure());
        List<String> moduller = new ArrayList<>();
        for (String id : new String[]{t.tarif.tetikleyici(), t.tarif.silah(), t.tarif.fizik()}) if (id != null) moduller.add(id);
        o.put("moduller", moduller);
        o.put("nedenler", nedenler);
        plugin.beyin().profilOlay(o);
    }

    private void oyunaYaz(String tip, AktifTuzak t, List<String> nedenler) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("tuzak_id", t.id);
        d.put("nedenler", nedenler);
        oyun.olaylar().kaydet(tip, d);
        oyun.tuzakLogu(tip, t, Map.of("nedenler", nedenler));
        if (tip.equals("tuzak_fark_edildi")) profileBildir("fark_edildi", t, nedenler);
        if (tip.equals("kil_payi")) profileBildir("kil_payi", t, nedenler);
    }

    // ---------- tetiklenme ve tetiklemeden kirma ----------

    @EventHandler(ignoreCancelled = true)
    public void kirma(BlockBreakEvent e) {
        if (!oyun.aktif() || !oyun.hedefMi(e.getPlayer())) return;
        for (AktifTuzak t : new ArrayList<>(oyun.tuzaklar())) {
            if (t.durum.equals("kurulu") && t.konum.getWorld().equals(e.getBlock().getWorld())
                    && t.kutu.contains(e.getBlock().getLocation().toVector().add(new Vector(0.5, 0.5, 0.5)))) {
                farkEdildi(t, List.of("tetiklemeden_kirdi"));
            }
        }
    }

    @EventHandler
    public void spawn(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof Item)) etki(e.getLocation());
    }

    @EventHandler
    public void patlama(EntityExplodeEvent e) {
        etki(e.getLocation());
    }

    @EventHandler
    public void patlamaBlok(BlockExplodeEvent e) {
        etki(e.getBlock().getLocation());
    }

    @EventHandler
    public void piston(BlockPistonExtendEvent e) {
        etki(e.getBlock().getLocation());
    }

    @EventHandler
    public void blokDegisimi(EntityChangeBlockEvent e) {
        etki(e.getBlock().getLocation());
    }

    private void etki(Location l) {
        if (!oyun.aktif()) return;
        double yaricap = plugin.getConfig().getDouble("izleme.patlama_yaricap");
        for (AktifTuzak t : new ArrayList<>(oyun.tuzaklar())) {
            if (t.durum.equals("tetiklendi") || !t.konum.getWorld().equals(l.getWorld())) continue;
            if (!t.kutu.clone().expand(yaricap).contains(l.toVector())) continue;
            t.durum = "tetiklendi";
            t.tetiklenmeMs = System.currentTimeMillis();
            oyunaYaz("tuzak_tetiklendi", t, List.of());
            kilPayiBekle(t);
        }
    }

    private void kilPayiBekle(AktifTuzak t) {
        long tik = plugin.getConfig().getLong("izleme.kil_payi_bekleme_sn") * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!oyun.aktif()) return;
            Player p = oyun.hedef();
            if (p.isDead() || !p.getWorld().equals(t.konum.getWorld())) return;
            if (p.getLocation().distance(t.konum) <= plugin.getConfig().getDouble("izleme.kil_payi_mesafe")) {
                oyunaYaz("kil_payi", t, List.of());
            }
        }, tik);
    }
}
