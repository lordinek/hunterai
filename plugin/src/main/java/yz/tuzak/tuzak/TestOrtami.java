package yz.tuzak.tuzak;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Test kancalarina verilen baglam: yerel koordinati dunyaya cevirir. */
public final class TestOrtami {

    private final World dunya;
    private final Donusum donusum;
    private final Plugin plugin;

    public TestOrtami(World dunya, Donusum donusum, Plugin plugin) {
        this.dunya = dunya;
        this.donusum = donusum;
        this.plugin = plugin;
    }

    public World dunya() {
        return dunya;
    }

    public Plugin plugin() {
        return plugin;
    }

    /** Modulun yerel hucresinin dunyadaki konumu (blok merkezi). */
    public Location konum(int x, int y, int z) {
        int[] d = donusum.dunya(x, y, z);
        return new Location(dunya, d[0] + 0.5, d[1], d[2] + 0.5);
    }
}
