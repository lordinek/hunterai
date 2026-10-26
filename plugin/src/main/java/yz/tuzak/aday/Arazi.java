package yz.tuzak.aday;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Arazi sorgulari: yurunebilir nokta bulma, kazilabilir kati arazi. */
public final class Arazi {

    private Arazi() {}

    /** Ust y'den alta dogru ilk yurunebilir hucre: ayak hucresi, ustu bos, alti kati. */
    public static Location yuruneBul(World w, int x, int z, int yUst, int yAlt) {
        int ust = Math.min(yUst, w.getMaxHeight() - 2);
        int alt = Math.max(yAlt, w.getMinHeight() + 1);
        for (int y = ust; y >= alt; y--) {
            Block ayak = w.getBlockAt(x, y, z);
            Block alti = ayak.getRelative(BlockFace.DOWN);
            if (ayak.isPassable() && !ayak.isLiquid()
                    && ayak.getRelative(BlockFace.UP).isPassable() && !ayak.getRelative(BlockFace.UP).isLiquid()
                    && alti.isSolid() && !alti.isLiquid()
                    && alti.getType() != Material.MAGMA_BLOCK && alti.getType() != Material.CAMPFIRE) {
                return new Location(w, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    /** Kati, sivi olmayan, kirilabilir (bedrock degil) blok. */
    public static boolean kati(Block b) {
        Material m = b.getType();
        return m.isSolid() && !b.isLiquid() && m.getHardness() >= 0 && !m.isAir();
    }

    /** Nokta oyuncunun gorus alaninda mi? Aci icinde ve engelsiz bir isin varsa gorunur. */
    public static boolean gorunuyor(Player p, Location nokta, double gorusAcisiDerece) {
        Location goz = p.getEyeLocation();
        Vector hedef = nokta.clone().add(0, 1, 0).toVector().subtract(goz.toVector());
        double mesafe = hedef.length();
        if (mesafe < 1e-6) return true;
        double yarim = Math.toRadians(gorusAcisiDerece / 2);
        double aci = Math.acos(Math.max(-1, Math.min(1, goz.getDirection().normalize().dot(hedef.clone().normalize()))));
        if (aci > yarim) return false;
        RayTraceResult r = p.getWorld().rayTraceBlocks(goz, hedef.normalize(), mesafe,
                FluidCollisionMode.NEVER, true);
        return r == null || r.getHitBlock() == null;
    }
}
