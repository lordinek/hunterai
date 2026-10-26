package yz.tuzak.ses;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * Simple Voice Chat entegrasyonu: 48 kHz, 16 bit, mono PCM dosyasini Ali'ye "kafasinin icinde" calar
 * (konumsuz kanal). Ses mod'u olmayan istemcide cal() false doner; o zaman sadece altyazi gosterilir.
 * Voicechat API sinifi yalnizca bu sinifta kullanilir.
 */
public final class SesKopru implements VoicechatPlugin {

    private static final int KARE = 960; // 20 ms @ 48 kHz

    private volatile VoicechatServerApi api;

    @Override
    public String getPluginId() {
        return "yz_tuzak";
    }

    @Override
    public void initialize(VoicechatApi a) {
        if (a instanceof VoicechatServerApi s) api = s;
    }

    @Override
    public void registerEvents(EventRegistration r) {
        r.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
    }

    /** Teshis: ses neden calinmiyor? */
    public String durum(Player p) {
        VoicechatServerApi a = api;
        if (a == null) return "voicechat sunucu API'si hazir degil";
        VoicechatConnection b = a.getConnectionOf(p.getUniqueId());
        if (b == null) return "sunucu bu oyuncunun voicechat modunu GORMEDI (mod yok ya da surum uyusmuyor)";
        if (!b.isConnected()) {
            return "mod var ama ses baglantisi kurulmadi (kuruldu=" + b.isInstalled() + ", kapali=" + b.isDisabled()
                    + ", ses_grubu=" + (b.getGroup() != null) + ")";
        }
        return "tamam: mod var ve baglanti kurulu";
    }

    public boolean hazir() {
        return api != null;
    }

    /**
     * @param seviye ses siddeti 0..1 (gozlerin parlakligi icin)
     * @param bitti  calma bitince cagrilir
     * @return calmaya baslandiysa true
     */
    public boolean cal(Player p, Path pcm, double kazanc, DoubleConsumer seviye, Runnable bitti) {
        VoicechatServerApi a = api;
        if (a == null) return false;
        VoicechatConnection baglanti = a.getConnectionOf(p.getUniqueId());
        if (baglanti == null || !baglanti.isConnected()) return false;
        short[] ornekler;
        try {
            ornekler = oku(pcm);
        } catch (IOException e) {
            return false;
        }
        AudioChannel kanal = a.createStaticAudioChannel(UUID.randomUUID(), a.fromServerLevel(p.getWorld()), baglanti);
        if (kanal == null) return false;
        int[] konum = {0};
        Supplier<short[]> kaynak = () -> {
            if (konum[0] >= ornekler.length) return null;
            short[] kare = new short[KARE];
            int n = Math.min(KARE, ornekler.length - konum[0]);
            double toplam = 0;
            for (int i = 0; i < n; i++) {
                int v = (int) Math.max(-32768, Math.min(32767, Math.round(ornekler[konum[0] + i] * kazanc)));
                kare[i] = (short) v;
                toplam += (double) v * v;
            }
            konum[0] += n;
            seviye.accept(Math.min(1.0, Math.sqrt(toplam / n) / 12000.0));
            return kare;
        };
        AudioPlayer oynatici = a.createAudioPlayer(kanal, a.createEncoder(), kaynak);
        oynatici.setOnStopped(bitti);
        oynatici.startPlaying();
        return true;
    }

    private static short[] oku(Path pcm) throws IOException {
        byte[] b = Files.readAllBytes(pcm);
        short[] s = new short[b.length / 2];
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        return s;
    }
}
