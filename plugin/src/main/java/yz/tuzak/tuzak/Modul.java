package yz.tuzak.tuzak;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;

/**
 * Kodla tanimlanmis tuzak modulu: capa noktasina gore goreli blok listesi.
 * Yerel eksen: +Z yaklasma yonune (Ali'nin geldigi tarafa) bakar.
 */
public final class Modul {

    public enum Tur { TETIKLEYICI, SILAH, FIZIK, YEM }

    /**
     * NORMAL: oldugu gibi konur. KRITIK: konur ve kurulum sonrasi dogrulanir.
     * GOVDE: arazi malzemesiyle doldurulur (arazide zaten katiysa dokunulmaz). BOS: havaya cevrilir.
     */
    public enum Rol { NORMAL, KRITIK, GOVDE, BOS }

    /** SERBEST: etki hucresi icin kosul yok. ACIK_HAVA: etki hucresi (ve ustu) arazide zaten acik hava olmali. */
    public enum EtkiKosulu { SERBEST, ACIK_HAVA }

    /**
     * @param veri   blok verisi ("minecraft:dispenser[facing=south]"); GOVDE/BOS icin null
     * @param yuzey  dunyanin gorunen yuzeyinde duran parca (gomulme ve gorus kontrolu disi)
     * @param icerik konteyner icerigi ("ARROW:64"), yoksa null
     */
    public record Blok(int x, int y, int z, Rol rol, String veri, boolean yuzey, String icerik) {}

    public final String id;
    public final Tur tur;
    public final List<Blok> bloklar;
    /** Kablonun basladigi (tetikleyici) kizilTas tozu hucresi, yerel. */
    public final int[] cikis;
    /** Kablonun bittigi (silah) kizilTas tozu hucresi, yerel. */
    public final int[] giris;
    /** Giris tozunun gosterdigi yatay yon (iletken bloga dogru), yerel. */
    public final int[] girisYon;
    /** Silahin etkisinin dustugu hucre, yerel; tetikleyiciye yakin konumlandirmada kullanilir. */
    public final int[] etki;
    public final EtkiKosulu etkiKosulu;
    public final Set<String> yerlesim;
    public final Set<String> baglam;
    public final int farkEdilme;
    public final String kacinma;
    public final Set<World.Environment> yasakDunya;
    public final TestKancasi test;

    private Modul(Yapici y) {
        this.id = y.id;
        this.tur = y.tur;
        this.bloklar = List.copyOf(y.bloklar);
        this.cikis = y.cikis;
        this.giris = y.giris;
        this.girisYon = y.girisYon;
        this.etki = y.etki;
        this.etkiKosulu = y.etkiKosulu;
        this.yerlesim = Set.copyOf(y.yerlesim);
        this.baglam = Set.copyOf(y.baglam);
        this.farkEdilme = y.farkEdilme;
        this.kacinma = y.kacinma;
        this.yasakDunya = y.yasakDunya.isEmpty() ? Set.of() : Set.copyOf(y.yasakDunya);
        this.test = y.test;
    }

    public static Yapici yeni(String id, Tur tur) {
        return new Yapici(id, tur);
    }

    /** Otomatik testte tetikleyiciyi / fizik tuzagini programatik calistirir. */
    @FunctionalInterface
    public interface TestKancasi {
        void tetikle(TestOrtami ortam);
    }

    public static final class Yapici {
        private final String id;
        private final Tur tur;
        private final List<Blok> bloklar = new ArrayList<>();
        private int[] cikis;
        private int[] giris;
        private int[] girisYon;
        private int[] etki;
        private EtkiKosulu etkiKosulu = EtkiKosulu.SERBEST;
        private final Set<String> yerlesim = new HashSet<>();
        private final Set<String> baglam = new HashSet<>();
        private int farkEdilme = 3;
        private String kacinma = "";
        private final Set<World.Environment> yasakDunya = EnumSet.noneOf(World.Environment.class);
        private TestKancasi test;

        private Yapici(String id, Tur tur) {
            this.id = id;
            this.tur = tur;
        }

        public Yapici kritik(int x, int y, int z, String veri) {
            bloklar.add(new Blok(x, y, z, Rol.KRITIK, veri, false, null));
            return this;
        }

        public Yapici kritik(int x, int y, int z, String veri, String icerik) {
            bloklar.add(new Blok(x, y, z, Rol.KRITIK, veri, false, icerik));
            return this;
        }

        public Yapici normal(int x, int y, int z, String veri) {
            bloklar.add(new Blok(x, y, z, Rol.NORMAL, veri, false, null));
            return this;
        }

        public Yapici yuzey(int x, int y, int z, String veri) {
            bloklar.add(new Blok(x, y, z, Rol.KRITIK, veri, true, null));
            return this;
        }

        public Yapici govde(int x, int y, int z) {
            bloklar.add(new Blok(x, y, z, Rol.GOVDE, null, false, null));
            return this;
        }

        public Yapici bos(int x, int y, int z) {
            bloklar.add(new Blok(x, y, z, Rol.BOS, null, false, null));
            return this;
        }

        public Yapici cikis(int x, int y, int z) {
            this.cikis = new int[]{x, y, z};
            return this;
        }

        public Yapici giris(int x, int y, int z, int yonX, int yonZ) {
            this.giris = new int[]{x, y, z};
            this.girisYon = new int[]{yonX, 0, yonZ};
            return this;
        }

        public Yapici etki(int x, int y, int z) {
            this.etki = new int[]{x, y, z};
            return this;
        }

        /** Silahin etki hucresi arazide zaten acik hava olmali (ok yolu, dusme boslugu oyuncunun alaninda). */
        public Yapici etkiAcikHava() {
            this.etkiKosulu = EtkiKosulu.ACIK_HAVA;
            return this;
        }

        public Yapici yerlesim(String... v) {
            yerlesim.addAll(List.of(v));
            return this;
        }

        public Yapici baglam(String... v) {
            baglam.addAll(List.of(v));
            return this;
        }

        public Yapici fark(int f) {
            this.farkEdilme = f;
            return this;
        }

        public Yapici kacinma(String k) {
            this.kacinma = k;
            return this;
        }

        public Yapici yasakDunya(World.Environment... d) {
            yasakDunya.addAll(List.of(d));
            return this;
        }

        public Yapici test(TestKancasi t) {
            this.test = t;
            return this;
        }

        public Modul bitir() {
            if (tur == Tur.TETIKLEYICI && cikis == null) throw new IllegalStateException(id + ": cikis yok");
            if (tur == Tur.SILAH && (giris == null || girisYon == null || etki == null)) {
                throw new IllegalStateException(id + ": giris/etki yok");
            }
            if ((tur == Tur.TETIKLEYICI || tur == Tur.FIZIK) && test == null) {
                throw new IllegalStateException(id + ": test kancasi yok");
            }
            return new Modul(this);
        }
    }
}
