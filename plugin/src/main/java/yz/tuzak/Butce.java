package yz.tuzak;

import java.util.List;
import java.util.Map;

/** Tuzak hakki (zamanla dolar, asamaya gore artar) ve konusma butcesi. */
public final class Butce {

    private static final List<String> SIRA = List.of("overworld_erken", "overworld_hazirlik", "nether", "stronghold");

    private final YzPlugin plugin;
    private int asamaIdx;
    private double hak;
    private int konusulan;
    private long sonKonusmaMs;

    public Butce(YzPlugin plugin) {
        this.plugin = plugin;
    }

    public String asama() {
        return SIRA.get(asamaIdx);
    }

    public int maksAktif() {
        return plugin.getConfig().getInt("asamalar." + asama() + ".maks_aktif");
    }

    public int hak() {
        return (int) Math.floor(hak);
    }

    /** Her saniye cagrilir; hak mevcut asamanin dolum hizina gore birikir. */
    public void tik(double dtSn) {
        double dolumSn = plugin.getConfig().getDouble("asamalar." + asama() + ".dolum_dk") * 60;
        double tavan = plugin.getConfig().getInt("maks_biriken_hak");
        hak = Math.min(tavan, hak + dtSn / dolumSn);
    }

    public boolean harca() {
        if (hak < 1) return false;
        hak -= 1;
        return true;
    }

    /** Olaylara bakarak asamayi ilerletir (geri donmez). */
    public void olay(String tip, Map<String, Object> detay) {
        int yeni = asamaIdx;
        switch (tip) {
            case "kritik_esya" -> {
                if (plugin.getConfig().getStringList("butce.hazirlik_esyalari").contains(String.valueOf(detay.get("esya")))) {
                    yeni = Math.max(yeni, 1);
                }
            }
            case "dunya_degisti" -> {
                String kime = String.valueOf(detay.get("kime"));
                if (kime.equals("NETHER")) yeni = Math.max(yeni, 2);
                if (kime.equals("THE_END")) yeni = Math.max(yeni, 3);
            }
            case "ender_gozu_firlatildi" -> yeni = Math.max(yeni, 3);
            default -> {
            }
        }
        asamaIdx = yeni;
    }

    // ---- konusma ----

    public int kalanKonusma() {
        return plugin.getConfig().getInt("konusma.toplam") - konusulan;
    }

    /** Sonraki konusmaya kalan sure (sn); 0 ise konusulabilir. */
    public long konusmaBeklemeSn() {
        if (konusulan == 0) return 0;
        long gecen = (System.currentTimeMillis() - sonKonusmaMs) / 1000;
        return Math.max(0, plugin.getConfig().getLong("konusma.min_aralik_dk") * 60 - gecen);
    }

    public boolean konusabilir() {
        return kalanKonusma() > 0 && konusmaBeklemeSn() == 0;
    }

    public void konustu() {
        konusulan++;
        sonKonusmaMs = System.currentTimeMillis();
    }
}
