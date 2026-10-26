package yz.tuzak.tuzak.moduller;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import yz.tuzak.tuzak.Modul;
import yz.tuzak.tuzak.TestOrtami;

/** Asama 6 (gizli gorev). Spoiler kuralina tabi. */
final class Tetikleyiciler {

    private Tetikleyiciler() {}

    static List<Modul> liste() {
        List<Modul> l = new ArrayList<>();
        l.add(basinc());
        l.add(ip());
        l.add(titresim());
        l.add(gozlem());
        l.add(huni());
        l.add(hedef());
        l.add(kol());
        l.add(kitap());
        return l;
    }

    // ---------- test yardimcilari ----------

    /** Sifir hizli esya birakir; esya varliklari olcume sayilmaz, sadece mekanizmayi oynatir. */
    private static void esyaBirak(TestOrtami o, int x, int y, int z, double dy) {
        Location l = o.konum(x, y, z).add(0, dy, 0);
        o.dunya().dropItem(l, new ItemStack(Material.STICK), it -> it.setVelocity(new Vector(0, 0, 0)));
    }

    /**
     * Oyuncu etkilesimi benzetimi: bir blok durumu degisince kenardaki toza guc guncellemesi
     * gitmeyebilir. Kablonun bastaki tozunun alt desteginde gecici blok degisimi yapilip geri alinir
     * (net fark yok), boylece toz guc degerini yeniden hesaplar.
     */
    private static void tozuUyandir(TestOrtami o, int x, int y, int z) {
        Block b = o.konum(x, y, z).getBlock();
        BlockData d = b.getBlockData();
        Material gecici = d.getMaterial() == Material.DIRT ? Material.STONE : Material.DIRT;
        b.setType(gecici, true);
        b.setBlockData(d, true);
    }

    private static void guclendir(TestOrtami o, int x, int y, int z) {
        Block b = o.konum(x, y, z).getBlock();
        BlockData d = b.getBlockData();
        if (d instanceof Powerable p) {
            p.setPowered(true);
            b.setBlockData(d, true);
        }
    }

    // ---------- moduller ----------

    /** Ahsap basinc plakasi; alti iletken blok; kablo bloğun altindan. */
    private static Modul basinc() {
        return Modul.yeni("t_basinc", Modul.Tur.TETIKLEYICI)
                .yuzey(0, 0, 0, "minecraft:dark_oak_pressure_plate")
                .govde(0, -1, 0)
                .cikis(0, -2, 0)
                .yerlesim("serbest")
                .baglam("acik_alan", "magara", "nether")
                .fark(2)
                .kacinma("Zemindeki plakalara basmamak.")
                .test(o -> esyaBirak(o, 0, 0, 0, 0.3))
                .bitir();
    }

    /** Iki kazik arasinda gerili ip; kancalardan biri arkasindaki blogu guclendirir, toz o blogun altinda. */
    private static Modul ip() {
        return Modul.yeni("t_ip", Modul.Tur.TETIKLEYICI)
                .yuzey(-2, 0, 0, "minecraft:cobblestone")
                .yuzey(2, 0, 0, "minecraft:cobblestone")
                .yuzey(0, 0, 0, "minecraft:tripwire")
                .yuzey(-1, 0, 0, "minecraft:tripwire_hook[facing=east]")
                .yuzey(1, 0, 0, "minecraft:tripwire_hook[facing=west]")
                .cikis(-2, -1, 0)
                .yerlesim("serbest", "duvar_ici")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Yerde gerili ipe dikkat etmek, uzerinden adimlamak.")
                .test(o -> esyaBirak(o, 0, 0, 0, 0.2))
                .bitir();
    }

    /** Yer altina gomulu titresim algilayici; yakindaki adimlari duyar, guc bir tekrarlayiciyla guclendirilir. */
    private static Modul titresim() {
        return Modul.yeni("t_titresim", Modul.Tur.TETIKLEYICI)
                .kritik(1, -2, 0, "minecraft:redstone_wire")
                .kritik(0, -2, 0, "minecraft:sculk_sensor")
                .kritik(2, -2, 0, "minecraft:repeater[facing=west]")
                .cikis(3, -2, 0)
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(5)
                .kacinma("Sessiz yurumek (cokerek ya da yavas adimlarla) ya da yerin altina mesafe koymak.")
                .test(o -> o.dunya().sendGameEvent(null, GameEvent.STEP, o.konum(0, 0, 0).toVector()))
                .bitir();
    }

    /** Yerde duran cevher yemi; altindaki gozlemci cevherin degisimini yakalar. */
    private static Modul gozlem() {
        return Modul.yeni("t_gozlem", Modul.Tur.TETIKLEYICI)
                .yuzey(0, 0, 0, "minecraft:iron_ore")
                .kritik(0, -1, 0, "minecraft:observer[facing=up]")
                .cikis(0, -2, 0)
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Zeminde tek basina duran cevhere dokunmamak, kirmamak.")
                .test(o -> {
                    Block b = o.konum(0, 0, 0).getBlock();
                    BlockData d = b.getBlockData();
                    b.setType(Material.AIR, true);
                    b.setBlockData(d, true);
                })
                .bitir();
    }

    /** Zemindeki kapagin altinda esya toplayan huni; okuyucu + tekrarlayici sinyali tasir. */
    private static Modul huni() {
        return Modul.yeni("t_huni", Modul.Tur.TETIKLEYICI)
                .yuzey(0, -1, 0, "minecraft:oak_trapdoor[half=bottom,open=false]")
                .kritik(0, -2, 0, "minecraft:hopper[facing=down]")
                .kritik(1, -2, 0, "minecraft:comparator[facing=west]")
                .kritik(2, -2, 0, "minecraft:repeater[facing=west]")
                .cikis(3, -2, 0)
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Zemindeki kapaklarin ustune esya dusurmemek.")
                .test(o -> esyaBirak(o, 0, -1, 0, 0.6))
                .bitir();
    }

    /** Zemine gomulu hedef tahtasi; ona degen bir sey toza guc verir. */
    private static Modul hedef() {
        return Modul.yeni("t_hedef", Modul.Tur.TETIKLEYICI)
                .yuzey(0, -1, 0, "minecraft:target")
                .kritik(0, -2, 0, "minecraft:redstone_wire")
                .kritik(1, -2, 0, "minecraft:repeater[facing=west]")
                .cikis(2, -2, 0)
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(2)
                .kacinma("Hedef tahtasina ok ya da mermi atmamak.")
                .test(o -> {
                    Block b = o.konum(0, -1, 0).getBlock();
                    BlockData d = b.getBlockData();
                    if (d instanceof AnaloguePowerable p) {
                        p.setPower(15);
                        b.setBlockData(d, true);
                    }
                })
                .bitir();
    }

    /** Yerdeki kol; cekilince altindaki blogu guclendirir. */
    private static Modul kol() {
        return Modul.yeni("t_kol", Modul.Tur.TETIKLEYICI)
                .yuzey(0, 0, 0, "minecraft:lever[face=floor,facing=north,powered=false]")
                .govde(0, -1, 0)
                .cikis(0, -2, 0)
                .yerlesim("serbest")
                .baglam("acik_alan", "magara", "nether")
                .fark(2)
                .kacinma("Ortada duran kollara dokunmamak.")
                .test(o -> {
                    guclendir(o, 0, 0, 0);
                    tozuUyandir(o, 0, -3, 0);
                })
                .bitir();
    }

    /** Yerdeki okuma sehpasi; sayfa cevrilince altindaki blogu guclendirir. */
    private static Modul kitap() {
        return Modul.yeni("t_kitap", Modul.Tur.TETIKLEYICI)
                .yuzey(0, 0, 0, "minecraft:lectern[facing=north,has_book=true,powered=false]")
                .govde(0, -1, 0)
                .cikis(0, -2, 0)
                .yerlesim("serbest")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Sehpadaki kitabi okumamak, sayfa cevirmemek.")
                .test(o -> {
                    guclendir(o, 0, 0, 0);
                    tozuUyandir(o, 0, -3, 0);
                })
                .bitir();
    }
}
