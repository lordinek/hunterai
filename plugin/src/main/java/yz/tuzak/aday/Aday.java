package yz.tuzak.aday;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Location;

/**
 * Tuzak icin aday nokta. Koordinat plugin'de kalir; LLM'e sadece {@link #llmOzeti()} gider.
 */
public final class Aday {

    public String etiket;
    public final String tur;
    public final String detay;
    public final Location konum;
    public final double mesafe;
    public final String yon;
    public final String zemin;
    public final String duvar;
    public final String tavan;
    public final String ortam;
    public double olasilik;

    public Aday(String tur, String detay, Location konum, double mesafe, String yon,
                String zemin, String duvar, String tavan, String ortam) {
        this.tur = tur;
        this.detay = detay;
        this.konum = konum;
        this.mesafe = mesafe;
        this.yon = yon;
        this.zemin = zemin;
        this.duvar = duvar;
        this.tavan = tavan;
        this.ortam = ortam;
    }

    /** LLM'e giden ozet: koordinat yok. */
    public Map<String, Object> llmOzeti() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("etiket", etiket);
        m.put("tur", tur);
        if (detay != null) m.put("detay", detay);
        m.put("mesafe", Math.round(mesafe));
        m.put("yon", yon);
        m.put("olasilik", olasilik);
        m.put("zemin", zemin);
        m.put("duvar", duvar);
        m.put("tavan", tavan);
        m.put("ortam", ortam);
        m.put("dunya", konum.getWorld().getEnvironment().name());
        return m;
    }

    /** Log icin: koordinatli. */
    public Map<String, Object> logKaydi() {
        Map<String, Object> m = llmOzeti();
        m.put("x", konum.getBlockX());
        m.put("y", konum.getBlockY());
        m.put("z", konum.getBlockZ());
        return m;
    }
}
