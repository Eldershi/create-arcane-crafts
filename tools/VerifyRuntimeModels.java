import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.expedition.arcane.client.assets.RuntimeModelAssets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import net.minecraft.resources.ResourceLocation;

/** Verifies installed model derivation against the approved visual reference, without a game. */
public final class VerifyRuntimeModels {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: VerifyRuntimeModels <reference.json> <installed-dependency.jar>...");
        JsonObject reference = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        Map<String, ZipFile> sources = new LinkedHashMap<>();
        var opened = new ArrayList<ZipFile>();
        try {
            for (int i = 1; i < args.length; i++) {
                ZipFile archive = new ZipFile(args[i]);
                opened.add(archive);
                archive.stream().filter(e -> e.getName().startsWith("assets/")).forEach(e -> sources.putIfAbsent(e.getName(), archive));
            }
            Map<ResourceLocation, byte[]> generated = new LinkedHashMap<>();
            RuntimeModelAssets.generate(id -> {
                String path = "assets/" + id.getNamespace() + "/" + id.getPath();
                ZipFile archive = sources.get(path);
                if (archive == null) throw new IOException("Missing installed model " + id);
                try (var input = archive.getInputStream(archive.getEntry(path))) { return input.readAllBytes(); }
            }, generated);
            for (var entry : reference.getAsJsonObject("resources").entrySet()) {
                byte[] value = generated.get(new ResourceLocation("arcane_industry", entry.getKey()));
                if (value == null) throw new AssertionError("Model not generated: " + entry.getKey());
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
                if (!hash.equals(entry.getValue().getAsString())) throw new AssertionError("Model changed: " + entry.getKey());
            }
            System.out.println("PASS: " + generated.size() + " runtime model resources match approved reference");
        } finally {
            for (ZipFile archive : opened) archive.close();
        }
    }
}
