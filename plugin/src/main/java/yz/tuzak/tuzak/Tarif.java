package yz.tuzak.tuzak;

/** Bir tuzak tarifi: tetikleyici + silah (+ yem) ya da fizik (+ yem). Bos alanlar null. */
public record Tarif(String tetikleyici, String silah, String fizik, String yem) {

    public boolean gecerli() {
        boolean birlesik = tetikleyici != null && silah != null && fizik == null;
        boolean tek = tetikleyici == null && silah == null && fizik != null;
        return birlesik || tek;
    }

    public String ozet() {
        return (fizik != null ? fizik : tetikleyici + "+" + silah) + (yem != null ? "+" + yem : "");
    }
}
