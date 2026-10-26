package yz.tuzak.beyin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.command.CommandSender;
import yz.tuzak.YzPlugin;

/** Beyin servisiyle HTTP iletisimi. Butun cagrilar asenkron, ana thread beklemez. */
public final class BeyinIstemci {

    private final YzPlugin plugin;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public BeyinIstemci(YzPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sahte bir durum raporuyla beyne cagri yapar; gonderene sadece OK + sure veya HATA doner. */
    public void test(CommandSender gonderen) {
        String url = plugin.getConfig().getString("beyin.url") + "/test";
        int zamanAsimi = plugin.getConfig().getInt("beyin.zaman_asimi_sn");
        String govde = "{\"asama\":\"overworld_erken\",\"can\":3,\"test\":true}";
        HttpRequest istek = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(zamanAsimi))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(govde))
                .build();
        long baslangic = System.nanoTime();
        http.sendAsync(istek, HttpResponse.BodyHandlers.discarding()).whenComplete((cevap, hata) -> {
            long ms = (System.nanoTime() - baslangic) / 1_000_000;
            boolean tamam = hata == null && cevap.statusCode() == 200;
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    gonderen.sendMessage(tamam ? "OK (" + ms + " ms)" : "HATA: beyin servisine ulasilamadi"));
        });
    }

    /** Beyin servisi baglanti durumu (spoiler icermez). */
    public void baglantiDurumu(CommandSender gonderen) {
        String url = plugin.getConfig().getString("beyin.url") + "/saglik";
        int zamanAsimi = plugin.getConfig().getInt("beyin.zaman_asimi_sn");
        HttpRequest istek = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(zamanAsimi)).GET().build();
        http.sendAsync(istek, HttpResponse.BodyHandlers.discarding()).whenComplete((cevap, hata) -> {
            boolean tamam = hata == null && cevap.statusCode() == 200;
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    gonderen.sendMessage("Beyin: " + (tamam ? "bagli" : "bagli degil")));
        });
    }

    /** Durum raporunu beyne yollar, karar JSON'unu dondurur. Asenkron. */
    public CompletableFuture<JsonObject> karar(Map<String, Object> durum, String hata) {
        Map<String, Object> govde = new LinkedHashMap<>();
        govde.put("durum", durum);
        govde.put("hata", hata);
        HttpRequest istek = HttpRequest.newBuilder(URI.create(plugin.getConfig().getString("beyin.url") + "/karar"))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("beyin.karar_zaman_asimi_sn")))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(govde), java.nio.charset.StandardCharsets.UTF_8))
                .build();
        return http.sendAsync(istek, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8))
                .thenApply(r -> {
                    if (r.statusCode() != 200) throw new CompletionException(new IOException("http " + r.statusCode()));
                    return gson.fromJson(r.body(), JsonObject.class);
                });
    }

    /** Plugin'in kesin bildigi olgulari profile isler (fire-and-forget). */
    public void profilOlay(Map<String, Object> olay) {
        HttpRequest istek = HttpRequest.newBuilder(URI.create(plugin.getConfig().getString("beyin.url") + "/profil/olay"))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("beyin.zaman_asimi_sn")))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(olay), java.nio.charset.StandardCharsets.UTF_8))
                .build();
        http.sendAsync(istek, HttpResponse.BodyHandlers.discarding());
    }

    /** Olum raporu uretimi (agir model, uzun zaman asimi). Beyin rapor dosyasini oyun klasorune yazar. */
    public CompletableFuture<JsonObject> olumRaporu(Map<String, Object> istek) {
        HttpRequest r = HttpRequest.newBuilder(URI.create(plugin.getConfig().getString("beyin.url") + "/olum_raporu"))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("beyin.rapor_zaman_asimi_sn")))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(istek), java.nio.charset.StandardCharsets.UTF_8))
                .build();
        return http.sendAsync(r, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8))
                .thenApply(x -> {
                    if (x.statusCode() != 200) throw new CompletionException(new IOException("http " + x.statusCode()));
                    return gson.fromJson(x.body(), JsonObject.class);
                });
    }

    /** Metni sese cevirtir; beyin PCM dosyasini oyun klasorune yazar ve yolunu dondurur. */
    public CompletableFuture<JsonObject> ses(Map<String, Object> istek) {
        HttpRequest r = HttpRequest.newBuilder(URI.create(plugin.getConfig().getString("beyin.url") + "/ses"))
                .timeout(Duration.ofSeconds(plugin.getConfig().getInt("beyin.ses_zaman_asimi_sn")))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(istek), java.nio.charset.StandardCharsets.UTF_8))
                .build();
        return http.sendAsync(r, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8))
                .thenApply(x -> {
                    if (x.statusCode() != 200) throw new CompletionException(new IOException("http " + x.statusCode()));
                    return gson.fromJson(x.body(), JsonObject.class);
                });
    }
}
