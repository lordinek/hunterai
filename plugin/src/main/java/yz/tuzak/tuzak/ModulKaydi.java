package yz.tuzak.tuzak;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import yz.tuzak.tuzak.moduller.GercekModuller;
import yz.tuzak.tuzak.ornek.OrnekModuller;

/** Butun modullerin kaydi. Icerigi asla loga/konsola basilmaz. */
public final class ModulKaydi {

    private final Map<String, Modul> modul = new LinkedHashMap<>();

    public ModulKaydi(FileConfiguration c) {
        List<Modul> hepsi = new ArrayList<>(GercekModuller.liste());
        if (c.getBoolean("tuzak.ornek_moduller")) hepsi.addAll(OrnekModuller.liste());
        for (Modul m : hepsi) {
            if (modul.put(m.id, m) != null) throw new IllegalStateException("tekrarlanan modul id");
        }
    }

    public Modul al(String id) {
        return id == null ? null : modul.get(id);
    }

    public List<Modul> tur(Modul.Tur t) {
        List<Modul> l = new ArrayList<>();
        for (Modul m : modul.values()) if (m.tur == t) l.add(m);
        return l;
    }

    public List<Modul> hepsi() {
        return new ArrayList<>(modul.values());
    }
}
