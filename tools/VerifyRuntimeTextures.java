import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.expedition.arcane.client.assets.RuntimeTextureAssets;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Offline pixel regression against the approved prototype; no game or server is launched. */
public final class VerifyRuntimeTextures {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: VerifyRuntimeTextures <reference.json> <installed-dependency.jar>...");
        JsonObject reference = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        Map<String, ZipFile> sources = new LinkedHashMap<>();
        java.util.List<ZipFile> opened = new java.util.ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            ZipFile archive = new ZipFile(args[i]);
            opened.add(archive);
            archive.stream().filter(e -> e.getName().startsWith("assets/")).forEach(entry -> {
                String[] parts = entry.getName().split("/", 3);
                if (parts.length == 3) sources.putIfAbsent(entry.getName(), archive);
            });
        }
        try {
            Map<ResourceLocation, byte[]> generated = new LinkedHashMap<>();
            RuntimeTextureAssets.generate(id -> {
                String path = "assets/" + id.getNamespace() + "/" + id.getPath();
                ZipFile archive = sources.get(path);
                if (archive == null) throw new java.io.IOException("Missing installed source asset " + id);
                var entry = archive.getEntry(path);
                if (entry == null) throw new java.io.IOException("Missing dependency asset " + id);
                try (var input = archive.getInputStream(entry)) { return input.readAllBytes(); }
            }, generated);
            long pixels = 0; int textures = 0, metadata = 0;
            for (var entry : reference.getAsJsonObject("textures").entrySet()) {
                JsonObject expected = entry.getValue().getAsJsonObject();
                byte[] bytes = generated.get(new ResourceLocation("arcane_industry", entry.getKey()));
                if (bytes == null) throw new AssertionError("Texture missing: " + entry.getKey());
                var image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image.getWidth() != expected.get("width").getAsInt() || image.getHeight() != expected.get("height").getAsInt()) throw new AssertionError("Dimensions differ: " + entry.getKey());
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    hash.update((byte)(argb >>> 16)); hash.update((byte)(argb >>> 8)); hash.update((byte)argb); hash.update((byte)(argb >>> 24));
                }
                if (!HexFormat.of().formatHex(hash.digest()).equals(expected.get("rgbaSha256").getAsString())) throw new AssertionError("Pixels differ: " + entry.getKey());
                textures++; pixels += (long)image.getWidth() * image.getHeight();
            }
            for (var entry : reference.getAsJsonObject("metadata").entrySet()) {
                byte[] bytes = generated.get(new ResourceLocation("arcane_industry", entry.getKey()));
                if (bytes == null || !JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).equals(entry.getValue())) throw new AssertionError("Animation differs: " + entry.getKey());
                metadata++;
            }
            System.out.println("PASS: " + textures + " textures, " + metadata + " animation metadata, " + pixels + " exact RGBA pixels");
        } finally {
            for (ZipFile archive : opened) archive.close();
        }
    }
}
