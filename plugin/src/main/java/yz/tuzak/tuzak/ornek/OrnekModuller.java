package yz.tuzak.tuzak.ornek;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import yz.tuzak.tuzak.Modul;

/**
 * Asama 4 icin basit ornek moduller. Altyapiyi denemek icindir, oyunda kullanilmaz,
 * Asama 6'da silinir. Ali bunlari gorebilir.
 */
public final class OrnekModuller {

    private OrnekModuller() {}

    public static List<Modul> liste() {
        return List.of(plaka(), dispenser(), cokme(), yem());
    }

    /** Yuzeyde ahsap basinc plakasi; alti iletken blok; kablo bloğun altindan baslar. */
    private static Modul plaka() {
        return Modul.yeni("ornek_plaka", Modul.Tur.TETIKLEYICI)
                .yuzey(0, 0, 0, "minecraft:oak_pressure_plate")
                .govde(0, -1, 0)
                .cikis(0, -2, 0)
                .yerlesim("serbest")
                .baglam("acik_alan", "magara", "nether")
                .fark(2)
                .kacinma("Plakanin ustune basma.")
                .test(o -> {
                    Location l = o.konum(0, 0, 0).add(0, 0.3, 0);
                    o.dunya().dropItem(l, new ItemStack(Material.STICK), it -> it.setVelocity(new Vector(0, 0, 0)));
                })
                .bitir();
    }

    /** Arkasindaki iletken blok guclenince ok atan dispenser; ok yolu kapali bos hucre. */
    private static Modul dispenser() {
        return Modul.yeni("ornek_dispenser", Modul.Tur.SILAH)
                .kritik(0, 0, 0, "minecraft:dispenser[facing=south]", "ARROW:64")
                .normal(0, 0, -1, "minecraft:stone")
                .bos(0, 0, 1)
                .bos(0, 0, 2)
                .bos(0, 0, 3)
                .giris(0, 0, -2, 0, 1)
                .etki(0, 0, 2)
                .yerlesim("zemin_alti", "duvar_ici")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Plakaya basmamak.")
                .bitir();
    }

    /** Yuzeydeki cakil, altindaki tasin kirilmasiyla boslugu doldurur. */
    private static Modul cokme() {
        return Modul.yeni("ornek_cokme", Modul.Tur.FIZIK)
                .yuzey(0, -1, 0, "minecraft:gravel")
                .kritik(0, -2, 0, "minecraft:stone")
                .bos(0, -3, 0)
                .bos(0, -4, 0)
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(4)
                .kacinma("Cakili kirmadan once altini kontrol etmek.")
                .test(o -> o.konum(0, -2, 0).getBlock().setType(Material.AIR, true))
                .bitir();
    }

    /** Yuzeyde duran elmas cevheri. */
    private static Modul yem() {
        return Modul.yeni("ornek_yem", Modul.Tur.YEM)
                .yuzey(0, 0, 0, "minecraft:diamond_ore")
                .yerlesim("serbest")
                .baglam("acik_alan", "magara")
                .fark(1)
                .bitir();
    }
}
