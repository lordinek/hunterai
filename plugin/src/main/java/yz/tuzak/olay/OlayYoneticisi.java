package yz.tuzak.olay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import yz.tuzak.log.OyunLogu;

/** olaylar.jsonl yazar; ayni tip olay icin debounce bilgisini kayda ekler (beyin tetiklemesi icin). */
public final class OlayYoneticisi {

    /** Kayit sonrasi bildirim: tip, detay, debounce gecti mi. */
    @FunctionalInterface
    public interface Dinleyici {
        void olay(String tip, Map<String, Object> detay, boolean debounceGecti);
    }

    private static final int HAFIZA = 60;

    private final OyunLogu log;
    private final long debounceMs;
    private final Map<String, Long> sonOlay = new HashMap<>();
    private final Deque<Map<String, Object>> son = new ArrayDeque<>();
    private Dinleyici dinleyici;

    public OlayYoneticisi(OyunLogu log, int debounceSn) {
        this.log = log;
        this.debounceMs = debounceSn * 1000L;
    }

    public void dinleyiciAyarla(Dinleyici d) {
        this.dinleyici = d;
    }

    /** @return olayin log_id'si */
    public String kaydet(String tip, Map<String, Object> detay) {
        long simdi = System.currentTimeMillis();
        Long onceki = sonOlay.get(tip);
        boolean gecti = onceki == null || simdi - onceki >= debounceMs;
        if (gecti) sonOlay.put(tip, simdi);
        Map<String, Object> alan = new LinkedHashMap<>();
        alan.put("tip", tip);
        alan.put("detay", detay);
        alan.put("debounce_gecti", gecti);
        String id = log.yaz("olaylar.jsonl", "olay", alan);

        Map<String, Object> ozet = new LinkedHashMap<>();
        ozet.put("t", log.saniye());
        ozet.put("tip", tip);
        ozet.put("detay", detay);
        son.addLast(ozet);
        while (son.size() > HAFIZA) son.pollFirst();
        if (dinleyici != null) dinleyici.olay(tip, detay, gecti);
        return id;
    }

    public String kaydet(String tip) {
        return kaydet(tip, Map.of());
    }

    /** Son n olay (beyne gonderilmek icin); hasar gibi sik olaylar elenir. */
    public List<Map<String, Object>> son(int n) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Map<String, Object> o : son) {
            if (!"hasar".equals(o.get("tip")) && !"craft".equals(o.get("tip"))) l.add(o);
        }
        return l.subList(Math.max(0, l.size() - n), l.size());
    }
}
