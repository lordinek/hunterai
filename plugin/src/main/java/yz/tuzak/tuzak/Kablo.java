package yz.tuzak.tuzak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Katı arazinin icinden gomulu kizilTas kablosu icin 3B yol bulma (A*).
 * Hareketler: 4 yatay yon x {ayni seviye, bir yukari capraz, bir asagi capraz}.
 * Her toz hucresinin alti kati arazi olmali; hucre ve gereken hava hucreleri katidan oyulur.
 */
public final class Kablo {

    /** Bulunan yol: toz hucreleri sirayla (bas -> bit), ek oyulacak hava hucreleri. */
    public record Yol(List<int[]> hucreler, Set<Long> ekHava) {}

    private record Dugum(int x, int y, int z, int g, Dugum ebeveyn, double f) {}

    private static final int[][] YON = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Kablo() {}

    public static long anahtar(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y + 2048 & 0xFFF);
    }

    /**
     * @param yasak     modul hucreleri (kabloya kapali, ama bitisik olabilir)
     * @param bitYon    bitis tozunun gosterdigi yatay yon (dx, dz)
     * @param maksUzunluk toz hucresi sayisi ust siniri
     * @return yol ya da null
     */
    public static Yol bul(Izgara w, int[] bas, int[] bit, int[] bitYon, Set<Long> yasak,
                          int maksUzunluk, int dugumSiniri) {
        if (!uygunHucre(w, bas[0], bas[1], bas[2], yasak, Set.of(), true)) return null;
        if (!uygunHucre(w, bit[0], bit[1], bit[2], yasak, Set.of(), true)) return null;
        PriorityQueue<Dugum> acik = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Long, Integer> enIyi = new HashMap<>();
        acik.add(new Dugum(bas[0], bas[1], bas[2], 1, null, sezgi(bas[0], bas[1], bas[2], bit)));
        enIyi.put(anahtar(bas[0], bas[1], bas[2]), 1);
        int islenen = 0;
        while (!acik.isEmpty() && islenen++ < dugumSiniri) {
            Dugum d = acik.poll();
            if (d.x() == bit[0] && d.y() == bit[1] && d.z() == bit[2]) {
                Yol yol = yolOlustur(d);
                if (yol != null) return yol;
                continue;
            }
            if (d.g() >= maksUzunluk) continue;
            for (int[] yn : YON) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = d.x() + yn[0];
                    int ny = d.y() + dy;
                    int nz = d.z() + yn[1];
                    // bitise son yaklasma, giris tozu iletken bloga bakacak sekilde dogru eksende olmali
                    if (nx == bit[0] && ny == bit[1] && nz == bit[2]
                            && (d.x() != bit[0] - bitYon[0] || d.z() != bit[2] - bitYon[1])) continue;
                    if (!hareketUygun(w, d, nx, ny, nz, dy, yasak)) continue;
                    if (komsuCakisma(d, nx, ny, nz)) continue;
                    long k = anahtar(nx, ny, nz);
                    int g = d.g() + 1;
                    Integer onceki = enIyi.get(k);
                    if (onceki != null && onceki <= g) continue;
                    enIyi.put(k, g);
                    acik.add(new Dugum(nx, ny, nz, g, d, g + sezgi(nx, ny, nz, bit) + (dy != 0 ? 0.2 : 0)));
                }
            }
        }
        return null;
    }

    private static double sezgi(int x, int y, int z, int[] bit) {
        return Math.abs(x - bit[0]) + Math.abs(z - bit[2]) + Math.abs(y - bit[1]) * 0.2;
    }

    private static boolean hareketUygun(Izgara w, Dugum d, int nx, int ny, int nz, int dy, Set<Long> yasak) {
        Set<Long> yoldaki = new HashSet<>();
        yoldaki.add(anahtar(d.x(), d.y(), d.z()));
        if (dy == 1) yoldaki.add(anahtar(d.x(), d.y() + 1, d.z()));
        if (dy == -1) yoldaki.add(anahtar(nx, ny + 1, nz));
        if (!uygunHucre(w, nx, ny, nz, yasak, yoldaki, true)) return false;
        if (dy == 1) {
            yoldaki.add(anahtar(nx, ny, nz));
            return uygunHucre(w, d.x(), d.y() + 1, d.z(), yasak, yoldaki, false);
        }
        if (dy == -1) {
            yoldaki.add(anahtar(nx, ny, nz));
            return uygunHucre(w, nx, ny + 1, nz, yasak, yoldaki, false);
        }
        return true;
    }

    /**
     * Hucre kati araziden oyulabilir mi, alti kati mi, etrafi kapali mi?
     *
     * @param yoldakiler bu hareketle birlikte oyulacak (hava olacak) komsu hucreler
     * @param destek     toz hucresi mi (alti kati olmali), yoksa ek hava hucresi mi
     */
    private static boolean uygunHucre(Izgara w, int x, int y, int z, Set<Long> yasak,
                                      Set<Long> yoldakiler, boolean destek) {
        if (yasak.contains(anahtar(x, y, z))) return false;
        if (!w.kazilabilir(x, y, z)) return false;
        if (destek && !w.kati(x, y - 1, z) && !yasak.contains(anahtar(x, y - 1, z))) return false;
        int[][] komsu = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] k : komsu) {
            int kx = x + k[0], ky = y + k[1], kz = z + k[2];
            long ak = anahtar(kx, ky, kz);
            if (yoldakiler.contains(ak) || yasak.contains(ak)) continue;
            if (!w.kati(kx, ky, kz)) return false;
        }
        return true;
    }

    private static boolean komsuCakisma(Dugum d, int nx, int ny, int nz) {
        // yolun onceki hucreleriyle (ebeveyn haric) 6-komsu ya da dikey capraz komsu olmasin
        Dugum o = d.ebeveyn();
        while (o != null) {
            int ax = Math.abs(o.x() - nx);
            int ay = Math.abs(o.y() - ny);
            int az = Math.abs(o.z() - nz);
            if (ax + ay + az == 0) return true;
            if (ax + az == 1 && ay <= 1) return true;
            if (ax == 0 && az == 0 && ay <= 2) return true;
            o = o.ebeveyn();
        }
        return false;
    }

    private static Yol yolOlustur(Dugum son) {
        List<int[]> hucreler = new ArrayList<>();
        for (Dugum d = son; d != null; d = d.ebeveyn()) hucreler.add(0, new int[]{d.x(), d.y(), d.z()});
        Set<Long> yol = new HashSet<>();
        for (int[] h : hucreler) yol.add(anahtar(h[0], h[1], h[2]));
        Set<Long> ek = new HashSet<>();
        for (int i = 1; i < hucreler.size(); i++) {
            int[] c = hucreler.get(i - 1);
            int[] n = hucreler.get(i);
            int dy = n[1] - c[1];
            if (dy == 1) ek.add(anahtar(c[0], c[1] + 1, c[2]));
            else if (dy == -1) ek.add(anahtar(n[0], n[1] + 1, n[2]));
        }
        // ek hucre baska bir toz hucresiyle cakisiyorsa gecersiz
        for (Long k : ek) if (yol.contains(k)) return null;
        // hicbir toz hucresinin destegi oyulan bir hucre olmamali
        for (int[] h : hucreler) {
            long destek = anahtar(h[0], h[1] - 1, h[2]);
            if (yol.contains(destek) || ek.contains(destek)) return null;
        }
        return new Yol(hucreler, ek);
    }
}
