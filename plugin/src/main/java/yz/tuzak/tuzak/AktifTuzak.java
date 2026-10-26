package yz.tuzak.tuzak;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

/** Dunyada kurulu bir tuzak. */
public final class AktifTuzak {

    public final String id;
    public final Tarif tarif;
    public final Location konum;
    public final BoundingBox kutu;
    public final long zamanMs;
    public String durum = "kurulu";
    public String tahmin;
    public String dusunce;
    /** Izleme durumu: Ali tuzaga bakarken olculen mesafe, yoksa -1. */
    public double bakisMesafesi = -1;
    public long tetiklenmeMs;

    public AktifTuzak(String id, Tarif tarif, Location konum, BoundingBox kutu) {
        this.id = id;
        this.tarif = tarif;
        this.konum = konum;
        this.kutu = kutu;
        this.zamanMs = System.currentTimeMillis();
    }
}
