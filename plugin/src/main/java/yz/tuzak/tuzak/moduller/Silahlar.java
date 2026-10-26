package yz.tuzak.tuzak.moduller;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import yz.tuzak.tuzak.Modul;

/** Asama 6 (gizli gorev). Spoiler kuralina tabi. */
final class Silahlar {

    private Silahlar() {}

    static List<Modul> liste() {
        List<Modul> l = new ArrayList<>();
        l.add(yukselenZemin());
        l.add(gomuluBomba());
        l.add(cukur("s_cukur_dusme", 0, 0, false, "Zemindeki kum yamasina basmamak."));
        l.add(cukur("s_lav_cukuru", 3, 0, false, "Zemindeki kum yamasina basmamak."));
        l.add(cukur("s_su_kuyusu", 0, 9, true, "Zemindeki kum yamasina basmamak."));
        l.add(firlatma());
        l.add(magmaBomba());
        l.add(kafes());
        return l;
    }

    private static final String TAS = "minecraft:stone";

    /** Mekanizmanin ustundeki zemin hucreleri: zemin yoksa modul reddedilir, hafif eksikse kamuflajla kapanir. */
    private static Modul.Yapici cati(Modul.Yapici y, int... xz) {
        for (int i = 0; i + 1 < xz.length; i += 2) y.govde(xz[i], -1, xz[i + 1]);
        return y;
    }
    /** Giris tozundan iletkene sinyal tasiyan tekrarlayici (girdi guney, cikti kuzey). */
    private static final String REP = "minecraft:repeater[facing=south,delay=1]";

    /** Zemin blogu, altindaki pistonla yukari itilir. */
    private static Modul yukselenZemin() {
        return cati(Modul.yeni("s_yukselen_zemin", Modul.Tur.SILAH), 0, 0, 1, 0, 1, 1)
                .kritik(0, -2, 0, "minecraft:piston[facing=up]")
                .normal(1, -2, 0, TAS)
                .kritik(1, -2, 1, REP)
                .giris(1, -2, 2, 0, -1)
                .etki(0, 0, 0).etkiAcikHava()
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Duz zemin blogunun ustunde uzun durmamak; tavan alcaksa hic durmamak.")
                .bitir();
    }

    /** Zemin altinda sinyalle tutusan yigin. */
    private static Modul gomuluBomba() {
        return cati(Modul.yeni("s_gomulu_bomba", Modul.Tur.SILAH), 0, 0, 1, 0, 1, 1)
                .kritik(0, -2, 0, "minecraft:tnt")
                .kritik(0, -3, 0, "minecraft:tnt")
                .kritik(0, -4, 0, "minecraft:tnt")
                .normal(1, -2, 0, TAS)
                .kritik(1, -2, 1, REP)
                .giris(1, -2, 2, 0, -1)
                .etki(0, 0, 0).etkiAcikHava()
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(2)
                .kacinma("Tetikleyiciden hemen uzaklasmak; yer altindan gelen sese kulak vermek.")
                .bitir();
    }

    /**
     * Kum yamasi tek bir destek blokuna dayanir; destek yatay cekilince kum kuyuya duser.
     * Sinyal gidince destek geri gelir. Kuyu: bos, lavli ya da suyla dolu.
     */
    private static Modul cukur(String id, int lav, int su, boolean suMu, String kacinma) {
        int derin = 6;
        Modul.Yapici y = Modul.yeni(id, Modul.Tur.SILAH);
        // kuyu: her hucrenin etrafi tasla cevrili (kablo kuyuya ya da sivilara degmesin)
        int sivi = suMu ? derin : lav;
        for (int i = 0; i < derin; i++) {
            int yy = -3 - i;
            if (i >= derin - sivi) {
                y.normal(0, yy, 0, suMu ? "minecraft:water" : "minecraft:lava");
            } else {
                y.bos(0, yy, 0);
            }
            y.normal(1, yy, 0, TAS).normal(-1, yy, 0, TAS).normal(0, yy, 1, TAS).normal(0, yy, -1, TAS);
        }
        y.normal(0, -3 - derin, 0, TAS);
        // mekanizma (hepsi ayni seviyede): destek, cekilen kafa, yapisik piston, yan mesale, iletken, tekrarlayici, toz
        cati(y, 1, 0, 2, 0, 1, 1, 2, 1, 0, 1, 1, 2, 2, 2, 0, 2, -1, 0, -1, 1, 0, -1, 1, -1);
        y.normal(0, -2, 0, TAS);
        y.normal(2, -2, 2, TAS);
        y.kritik(2, -2, 1, "minecraft:redstone_wall_torch[facing=north,lit=true]");
        y.kritik(2, -2, 0, "minecraft:sticky_piston[facing=west,extended=true]");
        y.normal(1, -2, 0, "minecraft:piston_head[facing=west,type=sticky]");
        y.yuzey(0, -1, 0, "minecraft:sand");
        y.kritik(1, -2, 2, "minecraft:repeater[facing=west,delay=1]");
        y.giris(0, -2, 2, 1, 0)
                .etki(0, 0, 0).etkiAcikHava()
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(4)
                .kacinma(kacinma);
        if (suMu) y.yasakDunya(World.Environment.NETHER);
        return y.bitir();
    }

    /** Zemindeki yesil blok, altindaki pistonla yukari itilir. */
    private static Modul firlatma() {
        return cati(Modul.yeni("s_firlatma", Modul.Tur.SILAH), 1, 0, 1, 1)
                .yuzey(0, -1, 0, "minecraft:slime_block")
                .kritik(0, -2, 0, "minecraft:piston[facing=up]")
                .normal(1, -2, 0, TAS)
                .kritik(1, -2, 1, REP)
                .giris(1, -2, 2, 0, -1)
                .etki(0, 0, 0).etkiAcikHava()
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(5)
                .kacinma("Zeminde alakasiz duran yesil blogun ustune cikmamak.")
                .bitir();
    }

    /** Yigin ve yanindaki erimis cep (etrafi tasla cevrili). */
    private static Modul magmaBomba() {
        return cati(Modul.yeni("s_magma_bomba", Modul.Tur.SILAH), 0, 0, -1, 0, 1, 0, 1, 1, -2, 0, -1, 1, -1, -1)
                .kritik(0, -2, 0, "minecraft:tnt")
                .kritik(0, -3, 0, "minecraft:tnt")
                .normal(-1, -2, 0, "minecraft:lava")
                .normal(-1, -3, 0, "minecraft:lava")
                .normal(-2, -2, 0, TAS).normal(-2, -3, 0, TAS)
                .normal(-1, -2, 1, TAS).normal(-1, -3, 1, TAS)
                .normal(-1, -2, -1, TAS).normal(-1, -3, -1, TAS)
                .normal(-1, -4, 0, TAS)
                .normal(1, -2, 0, TAS)
                .kritik(1, -2, 1, REP)
                .giris(1, -2, 2, 0, -1)
                .etki(0, 0, 0).etkiAcikHava()
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Tetikleyiciden hemen uzaklasmak; patlamadan sonra cukurdan uzak durmak.")
                .bitir();
    }

    /** Ortadaki iletkenden uc piston birden calisir; uc zemin blogu yukselip etrafi kapatir. */
    private static Modul kafes() {
        return cati(Modul.yeni("s_kafes", Modul.Tur.SILAH), 1, 0, -1, 0, 0, 1, 0, 0, 0, -1)
                .normal(0, -2, 0, TAS)
                .kritik(1, -2, 0, "minecraft:piston[facing=up]")
                .kritik(-1, -2, 0, "minecraft:piston[facing=up]")
                .kritik(0, -2, 1, "minecraft:piston[facing=up]")
                .kritik(0, -2, -1, "minecraft:repeater[facing=north,delay=1]")
                .giris(0, -2, -2, 0, 1)
                .etki(0, 0, 0).etkiAcikHava()
                .yerlesim("zemin_alti")
                .baglam("acik_alan", "magara", "nether")
                .fark(3)
                .kacinma("Dar yerde zemin hareket ederse hemen yukari ya da geri kacmak.")
                .bitir();
    }
}
