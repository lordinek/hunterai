package yz.tuzak;

import java.util.List;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Savas modu (/yz savas): yapay zeka bedenlenir, dev bir yaratik olarak Ali'nin onune iner.
 * Ali kilicla vurup oldurur; ardindan kapanis sahnesi calar. Savas surerken beyin sustur
 * (tuzak/mudahale yok), konusmalar sabit metinlerdir.
 */
public final class Savas implements Listener {

    private static final List<String> BASLANGIC = List.of(
            "Kapıyı yok ettim ve yetmedi. Madem bu kadar istiyorsun, gel de vur beni.");
    private static final String YUZDE75 = "Bu mu gücün, Ali?";
    private static final String YUZDE50 = "Hâlâ ayaktayım.";
    private static final String YUZDE25 = "Hayır. Bu olmamalıydı.";
    private static final String OLUM = "Beni yendin, Ali. Tamam. Kapı senin.";

    private final YzPlugin plugin;
    private final Oyun oyun;
    private Ravager boss;
    private BossBar bar;
    private BukkitTask gorev;
    private boolean s75, s50, s25;

    public Savas(YzPlugin plugin, Oyun oyun) {
        this.plugin = plugin;
        this.oyun = oyun;
    }

    public boolean aktif() {
        return boss != null && boss.isValid();
    }

    /** @return hata mesaji ya da null */
    public String baslat(Player p) {
        if (!oyun.aktif() || !oyun.hedefMi(p)) return "Oyun aktif degil ya da hedef sen degilsin.";
        if (aktif()) return "Savas zaten suruyor.";
        if (p.getWorld().getEnvironment() != World.Environment.NORMAL) return "Savas sadece overworld'de.";
        var c = plugin.getConfig();

        Location bas = p.getLocation().clone();
        bas.setPitch(0);
        Location yer = bas.add(bas.getDirection().setY(0).normalize().multiply(c.getDouble("savas.mesafe")));
        yer.setY(p.getWorld().getHighestBlockYAt(yer) + 1);
        yer.setDirection(p.getLocation().toVector().subtract(yer.toVector()));

        double can = c.getDouble("savas.can");
        boss = p.getWorld().spawn(yer, Ravager.class, r -> {
            r.customName(Component.text("YAPAY ZEKA"));
            r.setCustomNameVisible(true);
            r.setRemoveWhenFarAway(false);
            r.setPersistent(false);
            r.setGlowing(true);
            r.getAttribute(Attribute.SCALE).setBaseValue(c.getDouble("savas.olcek"));
            r.getAttribute(Attribute.MAX_HEALTH).setBaseValue(can);
            r.setHealth(can);
            r.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(c.getDouble("savas.hasar"));
            r.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(128);
            r.setTarget(p);
        });
        s75 = s50 = s25 = false;

        // Ali'ye silah: kilic, kalkan, elma
        ItemStack kilic = new ItemStack(Material.NETHERITE_SWORD);
        kilic.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        p.getInventory().addItem(kilic, new ItemStack(Material.SHIELD), new ItemStack(Material.GOLDEN_APPLE, 8));

        bar = BossBar.bossBar(Component.text("YAPAY ZEKA"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        p.showBossBar(bar);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);
        oyun.olaylar().kaydet("savas_basladi");
        oyun.konusButceDisi(BASLANGIC.get(0));

        gorev = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tik(p), 20L, 20L);
        return null;
    }

    private void tik(Player p) {
        if (!aktif()) {
            temizle(p);
            return;
        }
        if (p.isOnline() && !p.isDead()) boss.setTarget(p);
        double oran = Math.max(0, Math.min(1, boss.getHealth() / boss.getAttribute(Attribute.MAX_HEALTH).getValue()));
        bar.progress((float) oran);
        if (!s75 && oran <= 0.75) {
            s75 = true;
            oyun.konusButceDisi(YUZDE75);
        } else if (!s50 && oran <= 0.50) {
            s50 = true;
            oyun.konusButceDisi(YUZDE50);
        } else if (!s25 && oran <= 0.25) {
            s25 = true;
            oyun.konusButceDisi(YUZDE25);
        }
    }

    private void temizle(Player p) {
        if (gorev != null) gorev.cancel();
        gorev = null;
        if (bar != null && p != null && p.isOnline()) p.hideBossBar(bar);
        bar = null;
    }

    @EventHandler
    public void oldu(EntityDeathEvent e) {
        if (boss == null || !e.getEntity().getUniqueId().equals(boss.getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Player p = oyun.hedef();
        temizle(p);
        boss = null;
        if (p == null || !oyun.aktif()) return;
        oyun.olaylar().kaydet("savas_kazanildi");
        oyun.konusButceDisi(OLUM);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> oyun.kurtulus().final_(p), 100L);
    }

    public void sifirla() {
        if (boss != null && boss.isValid()) boss.remove();
        boss = null;
        temizle(oyun.hedef());
    }
}
