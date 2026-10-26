package yz.tuzak.tuzak;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Arazinin bir kutu icindeki anlik goruntusu. Kablo aramasi dunya API'sini cagirmadan
 * bu dizi uzerinde calisir (ana thread'i kilitlememek icin).
 */
public final class Izgara {

    /** Chunk yuklu degil ya da kutu disi. */
    public static final byte BILINMEYEN = 0;
    /** Kati, kirilabilir, kazilabilir arazi. */
    public static final byte KAZILABILIR = 1;
    /** Kati ama kazilamaz (kutu, kirilmaz blok, etkilesimli blok). */
    public static final byte KATI = 2;
    /** Hava, sivi ya da gecilebilir. */
    public static final byte BOS = 3;

    private final int x0, y0, z0, sx, sy, sz;
    private final byte[] tip;

    public Izgara(World w, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.x0 = minX;
        this.y0 = Math.max(minY, w.getMinHeight());
        this.z0 = minZ;
        this.sx = maxX - minX + 1;
        this.sy = Math.min(maxY, w.getMaxHeight() - 1) - this.y0 + 1;
        this.sz = maxZ - minZ + 1;
        this.tip = new byte[sx * Math.max(sy, 0) * sz];
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                if (!w.isChunkLoaded((x0 + x) >> 4, (z0 + z) >> 4)) continue;
                for (int y = 0; y < sy; y++) {
                    Block b = w.getBlockAt(x0 + x, y0 + y, z0 + z);
                    tip[(x * sy + y) * sz + z] = sinifla(b);
                }
            }
        }
    }

    private static byte sinifla(Block b) {
        Material m = b.getType();
        if (m.isAir() || b.isLiquid() || !m.isSolid()) return BOS;
        if (m.getHardness() < 0 || m.isInteractable()) return KATI;
        return KAZILABILIR;
    }

    public byte tip(int x, int y, int z) {
        int ix = x - x0, iy = y - y0, iz = z - z0;
        if (ix < 0 || iy < 0 || iz < 0 || ix >= sx || iy >= sy || iz >= sz) return BILINMEYEN;
        return tip[(ix * sy + iy) * sz + iz];
    }

    public boolean kazilabilir(int x, int y, int z) {
        return tip(x, y, z) == KAZILABILIR;
    }

    /** Kati (kazilabilir ya da degil): bir hucrenin komsusu olarak dis havaya acilmayi onler. */
    public boolean kati(int x, int y, int z) {
        byte t = tip(x, y, z);
        return t == KAZILABILIR || t == KATI;
    }
}
