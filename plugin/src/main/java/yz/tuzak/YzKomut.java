package yz.tuzak;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import yz.tuzak.aday.Aday;
import yz.tuzak.aday.AdayGosterici;
import yz.tuzak.aday.AdayUretici;
import yz.tuzak.beyin.BeyinIstemci;
import yz.tuzak.tuzak.IzinliTarifler;
import yz.tuzak.tuzak.ModulKaydi;
import yz.tuzak.tuzak.Tarif;
import yz.tuzak.tuzak.TuzakTesti;
import yz.tuzak.tuzak.Yerlestirici;

public final class YzKomut implements TabExecutor {

    private final YzPlugin plugin;
    private final Oyun oyun;
    private final BeyinIstemci beyin;
    private final ModulKaydi moduller;
    private final Yerlestirici yerlestirici;
    private final TuzakTesti tuzakTesti;

    public YzKomut(YzPlugin plugin, Oyun oyun, BeyinIstemci beyin, ModulKaydi moduller,
                   Yerlestirici yerlestirici, TuzakTesti tuzakTesti) {
        this.plugin = plugin;
        this.oyun = oyun;
        this.beyin = beyin;
        this.moduller = moduller;
        this.yerlestirici = yerlestirici;
        this.tuzakTesti = tuzakTesti;
    }

    @Override
    public boolean onCommand(CommandSender gonderen, Command komut, String etiket, String[] arg) {
        String alt = arg.length == 0 ? "" : arg[0].toLowerCase();
        switch (alt) {
            case "baslat" -> baslat(gonderen);
            case "durdur" -> {
                if (!oyun.aktif()) {
                    gonderen.sendMessage("Oyun aktif degil.");
                } else {
                    oyun.durdur();
                    gonderen.sendMessage("Oyun durduruldu.");
                }
            }
            case "isaret" -> {
                String hata = oyun.isaret();
                if (hata != null) gonderen.sendMessage(hata);
            }
            case "mudahale" -> {
                // gelistirme: /yz mudahale <duvar|mob_sel|it> (aktif oyun gerekir)
                Player o = gonderen instanceof Player pl ? pl : oyun.aktif() ? oyun.hedef() : null;
                if (o == null || !oyun.aktif() || arg.length < 2) gonderen.sendMessage("Kullanim: /yz mudahale <duvar|mob_sel|it> (oyun aktifken)");
                else {
                    String h = arg[1].equalsIgnoreCase("cukur_simdi")
                            ? oyun.dongu().mudahale().cukurSimdi(o)
                            : oyun.dongu().mudahale().uygula(arg[1].toLowerCase(), o, true);
                    if (h != null) gonderen.sendMessage("Reddedildi: " + h);
                }
            }
            case "savas" -> {
                if (gonderen instanceof Player pl) {
                    String h = oyun.savas().baslat(pl);
                    if (h != null) gonderen.sendMessage(h);
                } else {
                    gonderen.sendMessage("Oyuncu olarak calistir.");
                }
            }
            case "durum" -> durum(gonderen);
            case "varlik" -> varlikTest(gonderen, arg);
            case "spoiler" -> spoiler(gonderen, arg);
            case "oyuncu" -> {
                // gelistirme: konsoldan, belirtilen oyuncu adina komut calistirir
                Player o = arg.length >= 3 ? plugin.getServer().getPlayerExact(arg[1]) : null;
                if (o == null) gonderen.sendMessage("Kullanim: /yz oyuncu <ad> <komut...>");
                else plugin.getServer().dispatchCommand(o, "yz " + String.join(" ", java.util.Arrays.copyOfRange(arg, 2, arg.length)));
            }
            case "test" -> {
                if (arg.length == 2 && arg[1].equalsIgnoreCase("beyin")) beyin.test(gonderen);
                else if (arg.length == 2 && arg[1].equalsIgnoreCase("aday")) adayTest(gonderen);
                else if (arg.length == 2 && arg[1].equalsIgnoreCase("tuzaklar")) tuzakTesti.baslat(gonderen);
                else if (arg.length == 2 && arg[1].equalsIgnoreCase("kur")) kurTest(gonderen);
                else if (arg.length == 2 && arg[1].equalsIgnoreCase("ses") && gonderen instanceof Player p) {
                    String t = plugin.konusma().teshis(p);
                    p.sendMessage("Ses: " + t);
                    plugin.getLogger().info("Ses teshis (" + p.getName() + "): " + t);
                }
                else gonderen.sendMessage("Kullanim: /yz test <beyin|aday|tuzaklar|kur|ses>");
            }
            default -> gonderen.sendMessage("Kullanim: /yz <baslat|durdur|isaret|durum|test beyin>");
        }
        return true;
    }

    private void baslat(CommandSender gonderen) {
        Player p = gonderen instanceof Player pl
                ? pl
                : plugin.getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (p == null) {
            gonderen.sendMessage("Oyuncu yok.");
            return;
        }
        String hata = oyun.baslat(p);
        gonderen.sendMessage(hata == null ? "Oyun basladi." : hata);
    }

    /** Adaylari uretir, partikulle gosterir. Tuzak yok, spoiler degil. */
    private void adayTest(CommandSender gonderen) {
        if (!(gonderen instanceof Player p)) {
            gonderen.sendMessage("Oyun icinden calistir.");
            return;
        }
        double[] gidis = oyun.aktif() ? oyun.metrik().gidisVektoru() : null;
        List<Aday> adaylar = new AdayUretici(plugin.getConfig()).uret(p, gidis, List.of(), oyun.yerler());
        if (oyun.aktif()) {
            Map<String, Object> alan = new LinkedHashMap<>();
            List<Map<String, Object>> liste = new ArrayList<>();
            for (Aday a : adaylar) liste.add(a.logKaydi());
            alan.put("adaylar", liste);
            oyun.log().yaz("adaylar.jsonl", "aday", alan);
        }
        if (adaylar.isEmpty()) {
            p.sendMessage("Aday bulunamadi.");
            return;
        }
        for (Aday a : adaylar) {
            p.sendMessage(String.format("%s: %s%s, %d blok %s, olasilik %.2f, %s", a.etiket, a.tur,
                    a.detay == null ? "" : " (" + a.detay + ")", Math.round(a.mesafe), a.yon, a.olasilik, a.ortam));
        }
        AdayGosterici.goster(plugin, p, adaylar);
    }

    /** Gelistirme: izinli tariflerden birini en uygun adaya kurar. Ornek modullerde ayrinti verir. */
    private void kurTest(CommandSender gonderen) {
        if (!(gonderen instanceof Player p) || !oyun.aktif() || !oyun.hedefMi(p)) {
            gonderen.sendMessage("Oyun icinden, /yz baslat sonrasi calistir.");
            return;
        }
        List<IzinliTarifler.Izinli> izinli = IzinliTarifler.oku(
                java.nio.file.Path.of(plugin.getConfig().getString("tuzaklar_klasoru")).toAbsolutePath().normalize());
        if (izinli.isEmpty()) {
            gonderen.sendMessage("izinli_tarifler.json yok: once /yz test tuzaklar.");
            return;
        }
        List<Aday> adaylar = new AdayUretici(plugin.getConfig()).uret(p, oyun.metrik().gidisVektoru(),
                oyun.tuzaklar().stream().map(t -> t.konum).toList(), oyun.yerler());
        java.util.Random rnd = new java.util.Random();
        for (Aday a : adaylar) {
            String arazi = IzinliTarifler.arazi(a.konum.getWorld().getEnvironment(), a.ortam);
            List<IzinliTarifler.Izinli> uygun = izinli.stream().filter(i -> i.araziler().contains(arazi)).toList();
            if (uygun.isEmpty()) continue;
            Tarif t = uygun.get(rnd.nextInt(uygun.size())).tarif();
            double dx = p.getLocation().getX() - a.konum.getX();
            double dz = p.getLocation().getZ() - a.konum.getZ();
            double n = Math.max(1e-6, Math.hypot(dx, dz));
            Yerlestirici.Sonuc s = yerlestirici.kur(t, a.konum, new double[]{dx / n, dz / n}, p);
            if (!s.tamam()) continue;
            oyun.tuzakEkle(s.tuzak(), "test", "test kurulumu");
            boolean ornek = (t.tetikleyici() == null || t.tetikleyici().startsWith("ornek_"))
                    && (t.silah() == null || t.silah().startsWith("ornek_"))
                    && (t.fizik() == null || t.fizik().startsWith("ornek_"));
            if ((ornek && plugin.getConfig().getBoolean("tuzak.ornek_goster")) || !plugin.getConfig().getBoolean("spoiler_kilidi")) {
                gonderen.sendMessage("Ornek tuzak kuruldu: " + t.ozet() + " @ " + a.konum.getBlockX() + " "
                        + a.konum.getBlockY() + " " + a.konum.getBlockZ());
            } else {
                gonderen.sendMessage("Kuruldu.");
            }
            return;
        }
        gonderen.sendMessage("Kurulamadi.");
    }

    /** /yz varlik ad : animasyon testi. Oyun aktif degilse figuru gecici olarak baslatir. */
    private void varlikTest(CommandSender gonderen, String[] arg) {
        if (!(gonderen instanceof Player p)) {
            gonderen.sendMessage("Oyun icinden calistir.");
            return;
        }
        if (arg.length < 2) {
            gonderen.sendMessage("Kullanim: /yz varlik <bekle|izle|konus|isaret|egil|glitch|kapat>");
            return;
        }
        if (arg[1].equalsIgnoreCase("kapat")) {
            plugin.varlik().durdur();
            return;
        }
        if (!plugin.varlik().aktif()) plugin.varlik().baslat(p);
        if (!plugin.varlik().oynat(arg[1].toLowerCase())) gonderen.sendMessage("Bilinmeyen animasyon.");
    }

    /** Oyun bittikten sonra spoiler kilidini kaldirir (config ve kurgu scriptleri icin isaret dosyasi). */
    private void spoiler(CommandSender gonderen, String[] arg) {
        if (arg.length < 2 || !arg[1].equalsIgnoreCase("ac")) {
            gonderen.sendMessage("Kullanim: /yz spoiler ac");
            return;
        }
        if (oyun.aktif()) {
            gonderen.sendMessage("Once oyunu bitir.");
            return;
        }
        try {
            java.nio.file.Path veri = java.nio.file.Path.of(plugin.getConfig().getString("veri_klasoru")).toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(veri);
            java.nio.file.Files.writeString(veri.resolve("spoiler_acik"), "acik");
        } catch (java.io.IOException e) {
            gonderen.sendMessage("Isaret dosyasi yazilamadi.");
            return;
        }
        plugin.getConfig().set("spoiler_kilidi", false);
        plugin.saveConfig();
        gonderen.sendMessage("Spoiler kilidi acildi.");
    }

    private void durum(CommandSender gonderen) {
        if (!oyun.aktif()) {
            gonderen.sendMessage("Oyun: aktif degil");
        } else {
            long sn = (long) oyun.sure();
            int hak = plugin.getConfig().getInt("can_hakki");
            gonderen.sendMessage(String.format("Oyun: aktif, sure %02d:%02d, olum %d, can %s",
                    sn / 60, sn % 60, oyun.olumSayisi(), hak <= 0 ? "sinirsiz" : oyun.kalanCan() + "/" + hak));
        }
        beyin.baglantiDurumu(gonderen);
    }

    @Override
    public List<String> onTabComplete(CommandSender gonderen, Command komut, String etiket, String[] arg) {
        if (arg.length == 1) return List.of("baslat", "durdur", "isaret", "durum", "test", "varlik", "spoiler", "savas", "mudahale");
        if (arg.length == 2 && arg[0].equalsIgnoreCase("spoiler")) return List.of("ac");
        if (arg.length == 2 && arg[0].equalsIgnoreCase("varlik")) return List.of("bekle", "izle", "konus", "isaret", "egil", "glitch", "kapat");
        if (arg.length == 2 && arg[0].equalsIgnoreCase("test")) return List.of("beyin", "aday", "tuzaklar", "kur", "ses");
        return List.of();
    }
}
