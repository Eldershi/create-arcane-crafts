import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.expedition.arcane.client.assets.RuntimeGrindstoneModels;
import dev.expedition.arcane.client.assets.RuntimeGrindstoneTextures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;

/** Executes the production generators against installed dependencies, without starting Minecraft. */
public final class VerifyRuntimeGrindstone {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException(
                "Usage: VerifyRuntimeGrindstone <approved-assets-directory> <evidence-directory> <dependency.jar>...");
        Path approved = Path.of(args[0]), evidence = Path.of(args[1]);
        Files.createDirectories(evidence);
        Map<String, ZipFile> sources = new LinkedHashMap<>();
        var opened = new ArrayList<ZipFile>();
        try {
            for (int i = 2; i < args.length; i++) {
                ZipFile archive = new ZipFile(args[i]);
                opened.add(archive);
                archive.stream().filter(e -> e.getName().startsWith("assets/"))
                        .forEach(e -> sources.putIfAbsent(e.getName(), archive));
            }
            var generated = new LinkedHashMap<ResourceLocation, byte[]>();
            dev.expedition.arcane.client.assets.RuntimeAssetPack.Source source = id -> {
                String path = "assets/" + id.getNamespace() + "/" + id.getPath();
                ZipFile archive = sources.get(path);
                if (archive == null) throw new IOException("Missing installed dependency resource " + id);
                try (var input = archive.getInputStream(archive.getEntry(path))) { return input.readAllBytes(); }
            };
            RuntimeGrindstoneTextures.generate(source, generated);
            RuntimeGrindstoneModels.generate(source, generated);
            if (generated.size() != 9) throw new AssertionError("Expected exactly nine r57 resources");
            JsonObject report = new JsonObject(), resources = new JsonObject();
            long pixels = 0; int faces = 0;
            for (var entry : generated.entrySet()) {
                String path = entry.getKey().getPath();
                byte[] actual = entry.getValue(), expected = Files.readAllBytes(approved.resolve(path));
                JsonObject row = new JsonObject();
                row.addProperty("generatedSha256", hash(actual));
                row.addProperty("selectedSha256", hash(expected));
                if (path.endsWith(".png")) {
                    var a = ImageIO.read(new ByteArrayInputStream(actual));
                    var b = ImageIO.read(new ByteArrayInputStream(expected));
                    if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
                        throw new AssertionError("Texture dimensions differ: " + path);
                    for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++)
                        if (a.getRGB(x, y) != b.getRGB(x, y))
                            throw new AssertionError("Texture pixel differs: " + path + " at " + x + "," + y);
                    pixels += (long)a.getWidth() * a.getHeight();
                    row.addProperty("exactRgbaPixels", a.getWidth() * a.getHeight());
                } else if (path.endsWith(".json")) {
                    if (!JsonParser.parseString(new String(actual, StandardCharsets.UTF_8))
                            .equals(JsonParser.parseString(new String(expected, StandardCharsets.UTF_8))))
                        throw new AssertionError("Composite model differs: " + path);
                    row.addProperty("parsedJsonEqual", true);
                } else if (path.endsWith(".obj")) {
                    faces += compareObj(path, actual, expected);
                    row.addProperty("orderedGeometryAndUvEqualWithin", 1e-8);
                } else if (!java.util.Arrays.equals(actual, expected)) {
                    throw new AssertionError("Resource bytes differ: " + path);
                }
                Path target = evidence.resolve("generated").resolve(path);
                Files.createDirectories(target.getParent());
                Files.write(target, actual);
                resources.add(path, row);
            }
            report.addProperty("status", "PASS");
            report.addProperty("resources", generated.size());
            report.addProperty("exactRgbaPixels", pixels);
            report.addProperty("orderedObjFaces", faces);
            report.addProperty("minecraftStarted", false);
            report.addProperty("serverStarted", false);
            report.addProperty("gameTestsRun", false);
            report.add("resourceChecks", resources);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report);
            Files.writeString(evidence.resolve("runtime-grindstone-verification.json"), json + "\n");
            System.out.println("PASS: " + generated.size() + " selected r57 resources, "
                    + pixels + " exact RGBA pixels, " + faces + " ordered OBJ faces");
        } finally {
            for (ZipFile archive : opened) archive.close();
        }
    }

    private static int compareObj(String path, byte[] actual, byte[] expected) {
        String[] a = new String(actual, StandardCharsets.UTF_8).strip().split("\\R");
        String[] b = new String(expected, StandardCharsets.UTF_8).strip().split("\\R");
        if (a.length != b.length) throw new AssertionError("OBJ line count differs: " + path);
        int faces = 0;
        for (int i = 0; i < a.length; i++) {
            String[] aa = a[i].strip().split("\\s+"), bb = b[i].strip().split("\\s+");
            if (aa.length != bb.length || !aa[0].equals(bb[0]))
                throw new AssertionError("OBJ command differs: " + path + ":" + (i + 1));
            if (aa[0].equals("v") || aa[0].equals("vt")) {
                for (int j = 1; j < aa.length; j++) {
                    double av = Double.parseDouble(aa[j]), bv = Double.parseDouble(bb[j]);
                    if (!Double.isFinite(av) || Math.abs(av - bv) > 1e-8)
                        throw new AssertionError("OBJ numeric value differs: " + path + ":" + (i + 1));
                }
            } else if (!java.util.Arrays.equals(aa, bb)) {
                throw new AssertionError("OBJ face/material differs: " + path + ":" + (i + 1));
            }
            if (aa[0].equals("f")) faces++;
        }
        return faces;
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
