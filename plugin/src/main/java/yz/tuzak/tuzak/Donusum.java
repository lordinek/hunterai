package yz.tuzak.tuzak;

import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

/**
 * Modul yerel koordinatindan dunyaya: 90 derecelik k donus + capa konumu.
 * k=1: (x,z) -> (-z,x) (saat yonu, ustten bakinca; +z guney).
 */
public record Donusum(int k, int ox, int oy, int oz) {

    public int[] don(int x, int z) {
        int a = x;
        int b = z;
        for (int i = 0; i < k; i++) {
            int na = -b;
            int nb = a;
            a = na;
            b = nb;
        }
        return new int[]{a, b};
    }

    public int[] dunya(int x, int y, int z) {
        int[] r = don(x, z);
        return new int[]{ox + r[0], oy + y, oz + r[1]};
    }

    public int[] dunya(int[] v) {
        return dunya(v[0], v[1], v[2]);
    }

    /** Yon vektorunu (yerel) dunyaya cevirir; capa eklenmez. */
    public int[] yon(int[] v) {
        int[] r = don(v[0], v[2]);
        return new int[]{r[0], v[1], r[1]};
    }

    public StructureRotation donus() {
        return switch (((k % 4) + 4) % 4) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
    }

    public BlockData veri(BlockData d) {
        BlockData c = d.clone();
        c.rotate(donus());
        return c;
    }

    /**
     * Yerel +Z yaklasma yonune (tuzaktan Ali'nin geldigi tarafa) en yakin bakacak sekilde k secer.
     *
     * @param gelis tuzaktan Ali'nin geldigi tarafa birim vektor (x, z)
     */
    public static Donusum gelisten(int ox, int oy, int oz, double[] gelis) {
        int en = 0;
        double enDot = -2;
        for (int k = 0; k < 4; k++) {
            int[] r = new Donusum(k, 0, 0, 0).don(0, 1);
            double dot = r[0] * gelis[0] + r[1] * gelis[1];
            if (dot > enDot + 1e-9) {
                enDot = dot;
                en = k;
            }
        }
        return new Donusum(en, ox, oy, oz);
    }

    public Donusum capayla(int ox, int oy, int oz) {
        return new Donusum(k, ox, oy, oz);
    }
}
