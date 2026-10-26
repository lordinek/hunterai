package yz.tuzak.can;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.World;
import yz.tuzak.YzPlugin;

/** Oyun bitince dunya klasorlerini oyun klasorune kopyalar (sinematik ve kesit cekimleri icin). */
public final class DunyaYedegi {

    private DunyaYedegi() {}

    public static void yedekle(YzPlugin plugin, Path hedef) {
        String testOneki = plugin.getConfig().getString("tuzak.test.dunya_oneki");
        List<Path> klasorler = new ArrayList<>();
        for (World w : plugin.getServer().getWorlds()) {
            if (w.getName().startsWith(testOneki)) continue;
            w.save();
            Path p = w.getWorldFolder().toPath().toAbsolutePath().normalize();
            if (!klasorler.contains(p)) klasorler.add(p);
        }
        // baska bir klasorun icindekileri ayrica kopyalama
        List<Path> kokler = new ArrayList<>();
        for (Path p : klasorler) {
            if (klasorler.stream().noneMatch(q -> !q.equals(p) && p.startsWith(q))) kokler.add(p);
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Path kok : kokler) {
                Path ad = kok.getFileName();
                try {
                    kopyala(kok, hedef.resolve(ad == null ? "dunya" : ad.toString()));
                } catch (IOException ignored) {
                    // yedek alinamazsa oyun kapanisi bozulmaz
                }
            }
        });
    }

    private static void kopyala(Path kaynak, Path hedef) throws IOException {
        Files.walkFileTree(kaynak, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
                Files.createDirectories(hedef.resolve(kaynak.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path dosya, BasicFileAttributes a) throws IOException {
                if (dosya.getFileName().toString().equals("session.lock")) return FileVisitResult.CONTINUE;
                try {
                    Files.copy(dosya, hedef.resolve(kaynak.relativize(dosya).toString()), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // kilitli dosyayi atla
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
