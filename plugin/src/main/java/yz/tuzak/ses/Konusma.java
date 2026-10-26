package yz.tuzak.ses;

import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import yz.tuzak.Oyun;
import yz.tuzak.YzPlugin;

/**
 * Bir konusmayi sahneler: log, altyazi (action bar), ses uretimi (beyin), Simple Voice Chat ile calma ve
 * figurun konus animasyonu. Ses mod'u yoksa ya da ses uretilemezse sadece altyazi gorunur.
 */
public final class Konusma {

    private final YzPlugin plugin;
    private final Oyun oyun;
    private SesKopru kopru;
    private int sayac;
    private BukkitTask altyaziGorevi;

    public Konusma(YzPlugin plugin, Oyun oyun) {
        this.plugin = plugin;
        this.oyun = oyun;
    }

    public String teshis(Player p) {
        return kopru == null ? "Simple Voice Chat eklentisi yuklu degil" : kopru.durum(p);
    }

    public void koprusuAyarla(SesKopru k) {
        this.kopru = k;
    }

    /** Konusma butce kontrolu cagirandadir; burasi sadece sahneler. */
    public void soyle(String metin, boolean butceIci) {
        if (!oyun.aktif()) return;
        Player p = oyun.hedef();
        int no = ++sayac;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("no", no);
        d.put("metin", metin);
        d.put("ses_dosyasi", null);
        d.put("butce_ici", butceIci);
        String logId = oyun.log().yaz("konusmalar.jsonl", "konusma", d);

        double sure = Math.max(plugin.getConfig().getDouble("konusma.altyazi_min_sn"),
                metin.length() * plugin.getConfig().getDouble("konusma.altyazi_sn_karakter"));
        // altyazi ve agiz animasyonu ses calmaya baslayinca acilir (ses uretimi birkac sn surer)

        Map<String, Object> istek = new LinkedHashMap<>();
        istek.put("oyun_klasoru", oyun.log().klasor().toString());
        istek.put("no", no);
        istek.put("metin", metin);
        plugin.beyin().ses(istek).whenComplete((cevap, hata) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (hata != null || cevap == null || !oyun.aktif()) {
                        sesYok(p, metin, sure);
                        return;
                    }
                    JsonElement dosya = cevap.get("dosya");
                    if (dosya == null || dosya.isJsonNull()) {
                        sesYok(p, metin, sure);
                        return;
                    }
                    Map<String, Object> hazir = new LinkedHashMap<>();
                    hazir.put("konusma_log_id", logId);
                    hazir.put("no", no);
                    hazir.put("ses_dosyasi", dosya.getAsString());
                    hazir.put("uretim", cevap.has("uretim") ? cevap.get("uretim").getAsString() : "");
                    oyun.log().yaz("konusmalar.jsonl", "konusma", hazir);
                    boolean basladi = kopru != null && kopru.cal(p, Path.of(dosya.getAsString()),
                            plugin.getConfig().getDouble("konusma.ses_kazanci"),
                            plugin.varlik()::konusSeviye,
                            () -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                                plugin.varlik().konus(false);
                                if (altyaziGorevi != null) altyaziGorevi.cancel();
                                if (p.isOnline()) p.sendActionBar(Component.empty());
                            }));
                    if (basladi) {
                        altyaziGoster(p, metin, 20L * 120); // ses bitince iptal edilir
                        plugin.varlik().konus(true);
                    } else {
                        Map<String, Object> neden = new LinkedHashMap<>();
                        neden.put("konusma_log_id", logId);
                        neden.put("ses_calma", teshis(p));
                        oyun.log().yaz("konusmalar.jsonl", "konusma", neden);
                        sesYok(p, metin, sure);
                    }
                }));
    }

    /** Ses yok: altyazi simdi acilir, tahmini sure kadar kalir. */
    private void sesYok(Player p, String metin, double altyaziSn) {
        altyaziGoster(p, metin, (long) (altyaziSn * 20));
        plugin.varlik().konus(true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.varlik().konus(false), (long) (altyaziSn * 20));
    }

    /** Action bar yaklasik 3 sn gorunur; metin suresince tazelenir. */
    private void altyaziGoster(Player p, String metin, long toplamTik) {
        if (altyaziGorevi != null) altyaziGorevi.cancel();
        Component c = Component.text(metin);
        p.sendActionBar(c);
        long[] gecen = {0};
        altyaziGorevi = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            gecen[0] += 40;
            if (gecen[0] >= toplamTik || !p.isOnline()) {
                altyaziGorevi.cancel();
                return;
            }
            p.sendActionBar(c);
        }, 40L, 40L);
    }
}
