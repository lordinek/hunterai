package yz.tuzak.tuzak;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;

/** tuzaklar/izinli_tarifler.json: otomatik testten gecen tarifler ve gectikleri araziler. */
public final class IzinliTarifler {

    public record Izinli(Tarif tarif, Set<String> araziler) {}

    private IzinliTarifler() {}

    public static List<Izinli> oku(Path klasor) {
        List<Izinli> l = new ArrayList<>();
        Path dosya = klasor.resolve("izinli_tarifler.json");
        if (!Files.exists(dosya)) return l;
        try {
            JsonObject kok = new Gson().fromJson(Files.readString(dosya, StandardCharsets.UTF_8), JsonObject.class);
            for (JsonElement e : kok.getAsJsonArray("tarifler")) {
                JsonObject o = e.getAsJsonObject();
                Tarif t = new Tarif(str(o, "tetikleyici"), str(o, "silah"), str(o, "fizik"), null);
                Set<String> araziler = new HashSet<>();
                JsonArray a = o.getAsJsonArray("araziler");
                for (JsonElement x : a) araziler.add(x.getAsString());
                l.add(new Izinli(t, araziler));
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return l;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    /** Adayin ortami hangi test arazisine denk geliyor. */
    public static String arazi(World.Environment env, String ortam) {
        if (env == World.Environment.NETHER) return "NETHER";
        if (ortam.equals("magara") || ortam.equals("yapi_ici") || ortam.equals("maden")) return "MAGARA";
        return "DUZ";
    }
}
