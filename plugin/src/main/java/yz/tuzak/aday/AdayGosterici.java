package yz.tuzak.aday;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import yz.tuzak.YzPlugin;

/** Test modunda adaylari partikul sutunu olarak gosterir. Tuzak icermez, spoiler degildir. */
public final class AdayGosterici {

    private AdayGosterici() {}

    public static void goster(YzPlugin plugin, Player p, List<Aday> adaylar) {
        int toplamTik = plugin.getConfig().getInt("aday.gosterim_sn") * 20;
        int sutunBoy = plugin.getConfig().getInt("aday.gosterim_sutun_boy");
        new BukkitRunnable() {
            int gecen = 0;

            @Override
            public void run() {
                if (!p.isOnline() || gecen >= toplamTik) {
                    cancel();
                    return;
                }
                for (Aday a : adaylar) {
                    Location l = a.konum;
                    if (!l.getWorld().equals(p.getWorld())) continue;
                    for (int i = 0; i < sutunBoy; i++) {
                        p.spawnParticle(Particle.END_ROD, l.getX(), l.getY() + i, l.getZ(), 1, 0, 0, 0, 0);
                    }
                }
                gecen += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
}
