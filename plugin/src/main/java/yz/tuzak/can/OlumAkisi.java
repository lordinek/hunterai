package yz.tuzak.can;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import yz.tuzak.Oyun;
import yz.tuzak.YzPlugin;

/**
 * 3 can ve olum akisi: Ali sadece "Seni oldurdum." basligini gorur; olum raporu arka planda yazilir
 * ve Ali'ye gosterilmez. 3. olumde (ya da ejderha olunce) oyun biter.
 */
public final class OlumAkisi implements Listener {

    private final YzPlugin plugin;
    private final Oyun oyun;
    private CompletableFuture<Void> bekleyenRapor = CompletableFuture.completedFuture(null);
    private final Set<UUID> baslikBekleyen = new HashSet<>();
    private final Set<UUID> spectatorBekleyen = new HashSet<>();
    private boolean bitiyor;

    public OlumAkisi(YzPlugin plugin, Oyun oyun) {
        this.plugin = plugin;
        this.oyun = oyun;
    }

    public void sifirla() {
        bekleyenRapor = CompletableFuture.completedFuture(null);
        bitiyor = false;
    }

    /** Oyun.olum() ve ardindan: rapor istegi, baslik, gerekirse oyun sonu. */
    public void olum(Player p, String sebep, String olumLogId, int olumNo, double tBas, double tOlum) {
        Map<String, Object> istek = new LinkedHashMap<>();
        istek.put("oyun_klasoru", oyun.log().klasor().toString());
        istek.put("olum_no", olumNo);
        istek.put("t_bas", tBas);
        istek.put("t_olum", tOlum);
        istek.put("olum_log_id", olumLogId);
        istek.put("sebep", sebep);
        bekleyenRapor = plugin.beyin().olumRaporu(istek).handle((r, e) -> null);

        baslikBekleyen.add(p.getUniqueId());
        plugin.varlik().egil();
        if (oyun.kalanCan() <= 0) {
            spectatorBekleyen.add(p.getUniqueId());
            long tik = plugin.getConfig().getLong("can.zorla_dogus_sn") * 20L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.isDead()) p.spigot().respawn();
            }, tik);
            bitir("can_bitti");
        }
    }

    @EventHandler
    public void yenidenDogus(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        boolean baslik = baslikBekleyen.remove(p.getUniqueId());
        boolean spectator = spectatorBekleyen.remove(p.getUniqueId());
        if (!baslik && !spectator) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (spectator) p.setGameMode(GameMode.SPECTATOR);
            if (baslik) baslikGoster(p);
        }, 2L);
        if (oyun.aktif() && oyun.hedefMi(p)) oyun.yeniHayat();
    }

    private void baslikGoster(Player p) {
        var c = plugin.getConfig();
        p.showTitle(Title.title(
                Component.text(c.getString("can.olum_baslik")),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(c.getLong("can.baslik_giris_ms")),
                        Duration.ofMillis(c.getLong("can.baslik_sure_ms")),
                        Duration.ofMillis(c.getLong("can.baslik_cikis_ms")))));
    }

    /** Ejderha oldu: bir kerelik yenilgi kabulu (konusma butcesinden bagimsiz) ve kapanis. */
    public void ejderhaOldu() {
        if (!oyun.aktif() || bitiyor) return;
        oyun.olaylar().kaydet("ejderha_oldu");
        String metin = plugin.getConfig().getString("kapanis.yenilgi_metni");
        if (metin != null && !metin.isBlank()) oyun.konusButceDisi(metin);
        long tik = plugin.getConfig().getLong("kapanis.ejderha_bekleme_sn") * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> bitir("ejderha"), tik);
    }

    /** Kapanis: rapor bitene (ya da zaman asimina) kadar bekle, sonra loglari kapat ve dunyayi yedekle. */
    public void bitir(String sebep) {
        if (!oyun.aktif() || bitiyor) return;
        bitiyor = true;
        oyun.olaylar().kaydet("oyun_bitiyor", Map.of("sebep", sebep));
        long bekleme = plugin.getConfig().getLong("kapanis.rapor_bekleme_sn");
        bekleyenRapor.orTimeout(bekleme, TimeUnit.SECONDS).handle((r, e) -> null).thenRun(() ->
                plugin.getServer().getScheduler().runTask(plugin, oyun::durdur));
    }
}
