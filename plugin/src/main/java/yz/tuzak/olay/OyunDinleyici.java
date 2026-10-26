package yz.tuzak.olay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.PortalCreateEvent;
import yz.tuzak.Oyun;
import yz.tuzak.YzPlugin;

/** Oyuncu olaylarini yakalar; sadece hedef oyuncu icin calisir ve yalnizca loga yazar. */
public final class OyunDinleyici implements Listener {

    private final YzPlugin plugin;
    private final Oyun oyun;

    public OyunDinleyici(YzPlugin plugin, Oyun oyun) {
        this.plugin = plugin;
        this.oyun = oyun;
    }

    private boolean hedef(Entity e) {
        return e instanceof Player p && oyun.aktif() && oyun.hedefMi(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void kirma(BlockBreakEvent e) {
        if (hedef(e.getPlayer())) oyun.metrik().kazma(e.getPlayer(), e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void koyma(BlockPlaceEvent e) {
        if (hedef(e.getPlayer())) oyun.metrik().sayac("blok_koyma");
    }

    @EventHandler(ignoreCancelled = true)
    public void yeme(PlayerItemConsumeEvent e) {
        if (hedef(e.getPlayer()) && e.getItem().getType().isEdible()) oyun.metrik().sayac("yemek");
    }

    @EventHandler(ignoreCancelled = true)
    public void yatak(PlayerBedEnterEvent e) {
        if (!hedef(e.getPlayer())) return;
        oyun.metrik().sayac("yatak");
        oyun.yerEkle("yatak", e.getBed().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        if (!hedef(e.getWhoClicked())) return;
        Material m = e.getRecipe().getResult().getType();
        oyun.metrik().craft(m, e.getRecipe().getResult().getAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void envanterAcma(InventoryOpenEvent e) {
        if (!hedef(e.getPlayer())) return;
        Location l = e.getInventory().getLocation();
        if (l == null || l.getBlock() == null) return;
        List<String> tipler = plugin.getConfig().getStringList("gozlem.sandik_tipleri");
        if (tipler.contains(l.getBlock().getType().name())) {
            oyun.metrik().sandikAcildi(l);
            oyun.yerEkle("sandik", l);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void saldiri(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        if (hedef(d)) oyun.metrik().sayac("saldiri");
    }

    @EventHandler(ignoreCancelled = true)
    public void hasar(EntityDamageEvent e) {
        if (!hedef(e.getEntity())) return;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("neden", e.getCause().name());
        d.put("miktar", Math.round(e.getFinalDamage() * 10) / 10.0);
        if (e instanceof EntityDamageByEntityEvent be) d.put("saldiran", be.getDamager().getType().name());
        oyun.olaylar().kaydet("hasar", d);
    }

    @EventHandler
    public void olum(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!hedef(p)) return;
        EntityDamageEvent son = p.getLastDamageCause();
        String sebep = son == null ? "BILINMIYOR" : son.getCause().name();
        String saldiran = son instanceof EntityDamageByEntityEvent be ? be.getDamager().getType().name() : null;
        oyun.olum(p, sebep, saldiran);
    }

    @EventHandler
    public void yenidenDogus(PlayerRespawnEvent e) {
        if (hedef(e.getPlayer())) oyun.olaylar().kaydet("yeniden_dogus");
    }

    @EventHandler
    public void ejderha(EntityDeathEvent e) {
        if (e.getEntity() instanceof EnderDragon) plugin.olumAkisi().ejderhaOldu();
    }

    @EventHandler
    public void dunyaDegisti(PlayerChangedWorldEvent e) {
        if (!hedef(e.getPlayer())) return;
        String yeni = e.getPlayer().getWorld().getEnvironment().name();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("kimden", e.getFrom().getEnvironment().name());
        d.put("kime", yeni);
        d.put("ilk", oyun.ilkGiris(yeni));
        oyun.olaylar().kaydet("dunya_degisti", d);
    }

    @EventHandler(ignoreCancelled = true)
    public void portal(PortalCreateEvent e) {
        if (!oyun.aktif() || e.getReason() != PortalCreateEvent.CreateReason.FIRE) return;
        Player p = oyun.hedef();
        if (!p.getWorld().equals(e.getWorld())) return;
        double yaricap = plugin.getConfig().getDouble("gozlem.portal_yaricap", 128);
        for (var b : e.getBlocks()) {
            if (b.getLocation().distance(p.getLocation()) <= yaricap) {
                oyun.olaylar().kaydet("portal_yakildi");
                oyun.yerEkle("portal", b.getLocation());
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void portalGecis(PlayerPortalEvent e) {
        if (hedef(e.getPlayer())) oyun.yerEkle("portal", e.getFrom());
    }

    @EventHandler
    public void enderGozu(EntitySpawnEvent e) {
        if (!(e.getEntity() instanceof EnderSignal) || !oyun.aktif()) return;
        Player p = oyun.hedef();
        double yaricap = plugin.getConfig().getDouble("gozlem.ender_gozu_yaricap", 16);
        if (p.getWorld().equals(e.getLocation().getWorld()) && p.getLocation().distance(e.getLocation()) <= yaricap) {
            oyun.olaylar().kaydet("ender_gozu_firlatildi");
        }
    }

    @EventHandler
    public void ilerleme(PlayerAdvancementDoneEvent e) {
        if (!hedef(e.getPlayer())) return;
        String anahtar = e.getAdvancement().getKey().toString();
        if (plugin.getConfig().getStringList("gozlem.yapi_olaylari").contains(anahtar)) {
            oyun.olaylar().kaydet("yapi_kesfi", Map.of("ilerleme", anahtar));
        }
    }

    @EventHandler
    public void cikis(PlayerQuitEvent e) {
        if (hedef(e.getPlayer())) oyun.olaylar().kaydet("oyuncu_cikti");
    }
}
