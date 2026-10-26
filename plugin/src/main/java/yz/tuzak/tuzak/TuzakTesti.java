package yz.tuzak.tuzak;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.BoundingBox;
import yz.tuzak.YzPlugin;

/**
 * /yz test tuzaklar: gecici bos dunyalarda her kombinasyonu uc arazide kurar, tetikler, olcer.
 * Konsola/chat'e sadece sayilar yazilir; ayrintilar dosyaya gider.
 */
public final class TuzakTesti implements Listener {

    private enum Arazi { DUZ, MAGARA, TUNEL, NETHER }

    private record Vaka(Tarif tarif, Arazi arazi) {}

    private final YzPlugin plugin;
    private final ModulKaydi kayit;
    private final Yerlestirici yerlestirici;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private boolean calisiyor;
    private CommandSender gonderen;
    private final Deque<Vaka> kuyruk = new ArrayDeque<>();
    private final List<Map<String, Object>> sonuclar = new ArrayList<>();
    private final Map<Arazi, World> dunyalar = new HashMap<>();
    private int toplam;
    private int gecen;

    // olcum: her paralel kosu kendi kutusunu ve sayacini tasir
    private final class Kosu {
        final int slot;
        final World dunya;
        final BoundingBox olcum;
        int etki;

        Kosu(int slot, World dunya, BoundingBox olcum) {
            this.slot = slot;
            this.dunya = dunya;
            this.olcum = olcum;
        }
    }

    private final List<Kosu> aktifKosular = new ArrayList<>();
    private int calisanSayisi;

    public TuzakTesti(YzPlugin plugin, ModulKaydi kayit, Yerlestirici yerlestirici) {
        this.plugin = plugin;
        this.kayit = kayit;
        this.yerlestirici = yerlestirici;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean calisiyor() {
        return calisiyor;
    }

    public void baslat(CommandSender g) {
        if (calisiyor) {
            g.sendMessage("Test zaten calisiyor.");
            return;
        }
        calisiyor = true;
        gonderen = g;
        sonuclar.clear();
        kuyruk.clear();
        gecen = 0;
        for (Arazi a : Arazi.values()) {
            for (Modul t : kayit.tur(Modul.Tur.TETIKLEYICI)) {
                for (Modul s : kayit.tur(Modul.Tur.SILAH)) kuyruk.add(new Vaka(new Tarif(t.id, s.id, null, null), a));
            }
            for (Modul f : kayit.tur(Modul.Tur.FIZIK)) kuyruk.add(new Vaka(new Tarif(null, null, f.id, null), a));
        }
        toplam = kuyruk.size();
        calisanSayisi = 0;
        aktifKosular.clear();
        if (toplam == 0) {
            bitir();
            return;
        }
        int paralel = Math.max(1, plugin.getConfig().getInt("tuzak.test.paralel", 1));
        for (int slot = 0; slot < paralel && !kuyruk.isEmpty(); slot++) {
            calisanSayisi++;
            sonraki(slot);
        }
    }

    /** Slotlar ayni dunyada X ekseninde yan yana; patlama ve olcum birbirine karismasin diye aralikli. */
    private int slotAdimi(int h) {
        return 2 * h + 48;
    }

    private void sonraki(int slot) {
        Vaka v = kuyruk.poll();
        if (v == null) {
            if (--calisanSayisi == 0) bitir();
            return;
        }
        try {
            vakaCalistir(v, slot);
        } catch (RuntimeException e) {
            vakaKaydet(v, "hata", e.getClass().getSimpleName() + ": " + e.getMessage());
            aktifKosular.removeIf(k -> k.slot == slot);
            plugin.getServer().getScheduler().runTask(plugin, () -> sonraki(slot));
        }
    }

    private void vakaCalistir(Vaka v, int slot) {
        World w = dunya(v.arazi());
        int h = plugin.getConfig().getInt("tuzak.test.arazi_yaricap");
        int ox = slot * slotAdimi(h);
        araziKur(w, v.arazi(), h, ox);
        Tarif t = v.tarif();
        Yerlestirici.Sonuc s = yerlestirici.kur(t, new Location(w, ox, 61, 0), new double[]{0, 1}, null);
        if (!s.tamam()) {
            vakaKaydet(v, "kurulamadi", s.neden());
            plugin.getServer().getScheduler().runTask(plugin, () -> sonraki(slot));
            return;
        }
        BoundingBox kutu = s.tuzak().kutu;
        Map<Long, Material> ilk = anlik(w, kutu.clone().expand(1));
        Kosu kosu = new Kosu(slot, w, kutu.clone().expand(8));
        aktifKosular.add(kosu);
        int erken = plugin.getConfig().getInt("tuzak.test.bekleme_erken_tik");
        int bekle = plugin.getConfig().getInt("tuzak.test.bekleme_tetik_tik");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean erkenTetik = kosu.etki > 0 || !fark(ilk, anlik(w, kutu.clone().expand(1))).isEmpty();
            if (erkenTetik) {
                aktifKosular.remove(kosu);
                geriAlTemizle(w, ox, h);
                vakaKaydet(v, "erken_tetik", null);
                sonraki(slot);
                return;
            }
            Map<Long, Material> once = anlik(w, kutu.clone().expand(1));
            Modul tetik = kayit.al(t.fizik() != null ? t.fizik() : t.tetikleyici());
            tetik.test.tetikle(new TestOrtami(w, s.tetik(), plugin));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                boolean etki = kosu.etki > 0 || !fark(once, anlik(w, kutu.clone().expand(1))).isEmpty();
                aktifKosular.remove(kosu);
                vakaKaydet(v, etki ? "gecti" : "tetiklenmedi", null);
                sonraki(slot);
            }, bekle);
        }, erken);
    }

    private void vakaKaydet(Vaka v, String durum, String neden) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("tarif", v.tarif().ozet());
        k.put("arazi", v.arazi().name());
        k.put("durum", durum);
        if (neden != null) k.put("neden", neden);
        sonuclar.add(k);
        if (durum.equals("gecti")) gecen++;
    }

    /** Sadece bu slotun bolgesindeki varliklari siler; yan slotlar calismaya devam eder. */
    private void geriAlTemizle(World w, int ox, int h) {
        double yari = h + 20;
        for (Entity e : w.getEntities()) {
            if (Math.abs(e.getLocation().getX() - ox) <= yari) e.remove();
        }
    }

    // ---------- olcum ----------

    private Map<Long, Material> anlik(World w, BoundingBox k) {
        Map<Long, Material> m = new HashMap<>();
        for (int x = (int) Math.floor(k.getMinX()); x <= (int) Math.ceil(k.getMaxX()); x++) {
            for (int y = (int) Math.floor(k.getMinY()); y <= (int) Math.ceil(k.getMaxY()); y++) {
                for (int z = (int) Math.floor(k.getMinZ()); z <= (int) Math.ceil(k.getMaxZ()); z++) {
                    m.put(Kablo.anahtar(x, y, z), w.getBlockAt(x, y, z).getType());
                }
            }
        }
        return m;
    }

    private List<Long> fark(Map<Long, Material> a, Map<Long, Material> b) {
        List<Long> f = new ArrayList<>();
        for (Map.Entry<Long, Material> e : a.entrySet()) {
            if (b.get(e.getKey()) != e.getValue()) f.add(e.getKey());
        }
        return f;
    }

    /** Konumu kapsayan aktif kosuya bir etki yazar. */
    private void say(Location l) {
        for (Kosu k : aktifKosular) {
            if (k.dunya.equals(l.getWorld()) && k.olcum.contains(l.toVector())) {
                k.etki++;
                return;
            }
        }
    }

    @EventHandler
    public void olcumSpawn(EntitySpawnEvent e) {
        if (e.getEntity() instanceof Item) return;
        say(e.getLocation());
    }

    @EventHandler
    public void olcumPatlama(EntityExplodeEvent e) {
        say(e.getLocation());
    }

    @EventHandler
    public void olcumPatlamaBlok(BlockExplodeEvent e) {
        say(e.getBlock().getLocation());
    }

    @EventHandler
    public void olcumPiston(BlockPistonExtendEvent e) {
        say(e.getBlock().getLocation());
    }

    @EventHandler
    public void olcumDegisim(EntityChangeBlockEvent e) {
        say(e.getBlock().getLocation());
    }

    // ---------- arazi ----------

    private World dunya(Arazi a) {
        World w = dunyalar.get(a);
        if (w != null) return w;
        String ad = plugin.getConfig().getString("tuzak.test.dunya_oneki") + a.name().toLowerCase();
        World.Environment env = a == Arazi.NETHER ? World.Environment.NETHER : World.Environment.NORMAL;
        w = new WorldCreator(ad).environment(env).generator(new ChunkGenerator() {}).generateStructures(false).createWorld();
        if (w == null) throw new IllegalStateException("test dunyasi olusturulamadi");
        int h = plugin.getConfig().getInt("tuzak.test.arazi_yaricap");
        int paralel = Math.max(1, plugin.getConfig().getInt("tuzak.test.paralel", 1));
        int czSinir = (h >> 4) + 1;
        int cxAlt = (-h - 32) >> 4;
        int cxUst = ((paralel - 1) * slotAdimi(h) + h + 32) >> 4;
        for (int cx = cxAlt; cx <= cxUst; cx++) {
            for (int cz = -czSinir; cz <= czSinir; cz++) w.setChunkForceLoaded(cx, cz, true);
        }
        dunyalar.put(a, w);
        return w;
    }

    private void araziKur(World w, Arazi a, int h, int ox) {
        int alt = 30;
        int ust = 30 + plugin.getConfig().getInt("tuzak.test.arazi_yukseklik");
        for (int gx = ox - h; gx <= ox + h; gx++) {
            int x = gx - ox;
            for (int z = -h; z <= h; z++) {
                for (int y = alt; y <= ust; y++) {
                    Material m = switch (a) {
                        case DUZ -> y <= 56 ? Material.STONE : y <= 59 ? Material.DIRT : y == 60 ? Material.GRASS_BLOCK : Material.AIR;
                        case MAGARA -> (Math.abs(x) <= 8 && Math.abs(z) <= 8 && y >= 61 && y <= 64) ? Material.AIR : Material.STONE;
                        // dar tunel: 1 genis, 2 yuksek, z ekseni boyunca
                        case TUNEL -> (x == 0 && y >= 61 && y <= 62) ? Material.AIR : Material.STONE;
                        case NETHER -> (Math.abs(x) <= 8 && Math.abs(z) <= 8 && y >= 61 && y <= 64) ? Material.AIR : Material.NETHERRACK;
                    };
                    Block b = w.getBlockAt(gx, y, z);
                    if (b.getType() != m) b.setType(m, false);
                }
            }
        }
        geriAlTemizle(w, ox, h);
    }

    // ---------- bitis ----------

    private void bitir() {
        calisiyor = false;
        Path klasor = Path.of(plugin.getConfig().getString("tuzaklar_klasoru")).toAbsolutePath().normalize();
        try {
            Path sonuc = klasor.resolve("test_sonuclari");
            Files.createDirectories(sonuc);
            String ad = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json";
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("gecen", gecen);
            doc.put("toplam", toplam);
            doc.put("vakalar", sonuclar);
            Files.writeString(sonuc.resolve(ad), gson.toJson(doc), StandardCharsets.UTF_8);
            Files.writeString(klasor.resolve("izinli_tarifler.json"), gson.toJson(izinliTarifler()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        for (Map.Entry<Arazi, World> e : dunyalar.entrySet()) {
            World w = e.getValue();
            File klasorDunya = w.getWorldFolder();
            Bukkit.unloadWorld(w, false);
            sil(klasorDunya);
        }
        dunyalar.clear();
        gonderen.sendMessage(gecen + "/" + toplam + " kombinasyon gecti");
    }

    /** Gecen kombinasyonlar ve hangi arazilerde gectikleri. */
    private Map<String, Object> izinliTarifler() {
        Map<String, List<String>> gecenler = new LinkedHashMap<>();
        Map<String, Map<String, String>> tarifler = new LinkedHashMap<>();
        for (Map<String, Object> s : sonuclar) {
            if (!"gecti".equals(s.get("durum"))) continue;
            String tarif = (String) s.get("tarif");
            gecenler.computeIfAbsent(tarif, k -> new ArrayList<>()).add((String) s.get("arazi"));
        }
        List<Map<String, Object>> liste = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : gecenler.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            String[] p = e.getKey().split("\\+");
            if (p.length == 2) {
                m.put("tetikleyici", p[0]);
                m.put("silah", p[1]);
            } else {
                m.put("fizik", p[0]);
            }
            m.put("araziler", e.getValue());
            liste.add(m);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("uretim_zamani", java.time.Instant.now().toString());
        doc.put("tarifler", liste);
        return doc;
    }

    private static void sil(File f) {
        File[] alt = f.listFiles();
        if (alt != null) for (File a : alt) sil(a);
        f.delete();
    }
}
