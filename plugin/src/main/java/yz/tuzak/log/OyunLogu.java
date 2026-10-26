package yz.tuzak.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bir oyunun log klasoru. Yazma ayri thread'de, icerik asla konsola/chat'e basilmaz. */
public final class OyunLogu {

    private final Path klasor;
    private final long baslangicNs = System.nanoTime();
    private final AtomicLong sayac = new AtomicLong();
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final ExecutorService yazar = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "yz-log");
        t.setDaemon(true);
        return t;
    });
    // sadece yazar thread'inden erisilir
    private final Map<String, BufferedWriter> yazicilar = new HashMap<>();

    public OyunLogu(Path klasor) throws IOException {
        this.klasor = klasor;
        Files.createDirectories(klasor);
    }

    public Path klasor() {
        return klasor;
    }

    /** Oyun basindan beri gecen sure (sn, 0.1 hassasiyet). */
    public double saniye() {
        return Math.round((System.nanoTime() - baslangicNs) / 1e8) / 10.0;
    }

    /** JSONL kaydi ekler, log_id dondurur. */
    public String yaz(String dosya, String onek, Map<String, Object> alanlar) {
        String id = onek + "-" + sayac.incrementAndGet();
        Map<String, Object> kayit = new LinkedHashMap<>();
        kayit.put("log_id", id);
        kayit.put("t", saniye());
        kayit.put("zaman", Instant.now().toString());
        kayit.putAll(alanlar);
        String satir = gson.toJson(kayit);
        yazar.execute(() -> {
            try {
                BufferedWriter w = yazicilar.get(dosya);
                if (w == null) {
                    w = Files.newBufferedWriter(klasor.resolve(dosya), StandardCharsets.UTF_8,
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    yazicilar.put(dosya, w);
                }
                w.write(satir);
                w.newLine();
                w.flush();
            } catch (IOException ignored) {
                // log yazilamazsa oyun bozulmaz
            }
        });
        return id;
    }

    /** Tek seferlik dosya yazar (profil kopyasi, olum raporu vb.). */
    public void dosyaYaz(String ad, String icerik) {
        yazar.execute(() -> {
            try {
                Files.writeString(klasor.resolve(ad), icerik, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
            }
        });
    }

    public void kapat() {
        yazar.execute(() -> {
            for (BufferedWriter w : yazicilar.values()) {
                try {
                    w.close();
                } catch (IOException ignored) {
                }
            }
            yazicilar.clear();
        });
        yazar.shutdown();
        try {
            yazar.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
