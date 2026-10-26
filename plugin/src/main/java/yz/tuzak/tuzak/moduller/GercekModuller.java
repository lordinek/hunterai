package yz.tuzak.tuzak.moduller;

import java.util.ArrayList;
import java.util.List;
import yz.tuzak.tuzak.Modul;

/** Gercek tuzak modulleri (Asama 6, gizli gorev). Spoiler kuralina tabi. */
public final class GercekModuller {

    private GercekModuller() {}

    public static List<Modul> liste() {
        List<Modul> l = new ArrayList<>();
        l.addAll(Tetikleyiciler.liste());
        l.addAll(Silahlar.liste());
        l.addAll(Fizikler.liste());
        l.addAll(Yemler.liste());
        return l;
    }
}
