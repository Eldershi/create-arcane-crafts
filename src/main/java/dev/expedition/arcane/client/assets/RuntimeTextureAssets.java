package dev.expedition.arcane.client.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Builds addon materials from installed dependency art, without bundling a copy of that art. */
public final class RuntimeTextureAssets {
    private static final String SPEC = "/assets/arcane_industry/runtime/texture_transforms.json";

    private RuntimeTextureAssets() {}

    public static void generate(RuntimeAssetPack.Source source, Map<ResourceLocation, byte[]> output) throws IOException {
        JsonObject spec;
        try (InputStream input = RuntimeTextureAssets.class.getResourceAsStream(SPEC)) {
            if (input == null) throw new IOException("Missing authored texture transforms: " + SPEC);
            spec = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Map<ResourceLocation, BufferedImage> images = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : spec.getAsJsonObject("textures").entrySet()) {
            ResourceLocation id = own(entry.getKey());
            JsonObject recipe = entry.getValue().getAsJsonObject();
            int[] size = ints(recipe.getAsJsonArray("size"));
            BufferedImage result = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
            if (recipe.has("procedural")) {
                drawBrows(result, recipe);
            } else {
                for (JsonElement layer : recipe.getAsJsonArray("layers")) {
                    compose(result, layer.getAsJsonObject(), source, images);
                }
                if (recipe.has("paint")) {
                    for (JsonElement brush : recipe.getAsJsonArray("paint")) {
                        JsonArray b = brush.getAsJsonArray();
                        fill(result, b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt(), b.get(3).getAsInt(), rgba(b.get(4).getAsString()));
                    }
                }
            }
            images.put(id, result);
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (!ImageIO.write(result, "PNG", bytes)) throw new IOException("PNG encoder unavailable");
                output.put(id, bytes.toByteArray());
            }
        }
        for (Map.Entry<String, JsonElement> entry : spec.getAsJsonObject("metadata").entrySet()) {
            JsonObject recipe = entry.getValue().getAsJsonObject();
            byte[] bytes = recipe.has("source")
                    ? source.read(new ResourceLocation(recipe.get("source").getAsString()))
                    : recipe.get("value").toString().getBytes(StandardCharsets.UTF_8);
            output.put(own(entry.getKey()), bytes);
        }
    }

    private static ResourceLocation own(String path) { return new ResourceLocation("arcane_industry", path); }

    private static void compose(BufferedImage target, JsonObject layer, RuntimeAssetPack.Source source,
                                Map<ResourceLocation, BufferedImage> generated) throws IOException {
        ResourceLocation ref = new ResourceLocation(layer.get("source").getAsString());
        BufferedImage image = generated.get(ref);
        if (image == null) {
            if (ref.getNamespace().equals("arcane_industry")) throw new IOException("Texture dependency has not been generated: " + ref);
            try (InputStream stream = new ByteArrayInputStream(source.read(ref))) { image = ImageIO.read(stream); }
            if (image == null) throw new IOException("Dependency texture is not a PNG: " + ref);
            generated.put(ref, image);
        }
        int[] rect = ints(layer.getAsJsonArray("rect")), dest = ints(layer.getAsJsonArray("dest"));
        int sw = rect[2] - rect[0], sh = rect[3] - rect[1];
        int[] size = layer.has("resize") ? ints(layer.getAsJsonArray("resize")) : new int[]{sw, sh};
        Map<Integer, Integer> palette = new HashMap<>();
        if (layer.has("palette")) for (Map.Entry<String, JsonElement> color : layer.getAsJsonObject("palette").entrySet()) {
            palette.put(rgba(color.getKey()), rgba(color.getValue().getAsString()));
        }
        String mask = layer.has("mask") ? layer.get("mask").getAsString() : "";
        boolean alphaOnly = layer.has("alphaOnly") && layer.get("alphaOnly").getAsBoolean();
        for (int y = 0; y < size[1]; y++) for (int x = 0; x < size[0]; x++) {
            int dx = dest[0] + x, dy = dest[1] + y;
            if (!insideMask(mask, dx, dy)) continue;
            // Pixel-centre nearest-neighbour sampling matches the original authored materials.
            int sx = rect[0] + Math.min(sw - 1, (int)Math.floor((x + .5) * sw / size[0]));
            int sy = rect[1] + Math.min(sh - 1, (int)Math.floor((y + .5) * sh / size[1]));
            int original = image.getRGB(sx, sy);
            int color = palette.getOrDefault(original, original);
            if (alphaOnly && (color >>> 24) == 0) continue;
            target.setRGB(dx, dy, color);
        }
    }

    private static boolean insideMask(String mask, int x, int y) {
        boolean inner = x >= 2 && x < 10 && y >= 2 && y < 10;
        boolean outer = x == 0 || x == 11 || y == 0 || y == 11;
        return switch (mask) {
            case "cross" -> (x >= 6 && x < 10 && y >= 4 && y < 12) || (x >= 4 && x < 12 && y >= 6 && y < 10);
            case "rim" -> !inner;
            case "outerrim" -> outer;
            case "innerrim" -> !outer && !inner;
            default -> true;
        };
    }

    /** Six short stair strokes form two independent eyebrows; shades describe face orientation. */
    private static void drawBrows(BufferedImage image, JsonObject recipe) {
        int[] side = colors(recipe.getAsJsonArray("side")), front = colors(recipe.getAsJsonArray("front"));
        int[] tips = colors(recipe.getAsJsonArray("tips")), fold = colors(recipe.getAsJsonArray("fold"));
        fill(image, 4, 24, 4, 1, side[2]); fill(image, 4, 24, 1, 1, side[1]);
        fill(image, 4, 25, 4, 1, side[1]); fill(image, 4, 26, 4, 1, side[0]);
        int[][] stairs = {{4, 28, 3}, {6, 29, 5}, {9, 30, 3}};
        for (int[] stair : stairs) for (boolean mirror : new boolean[]{false, true}) {
            int x = stair[0], y = stair[1], width = stair[2];
            fill(image, mirror ? 26 - x - width : x, y, width, 1, front[1]);
            image.setRGB(mirror ? 25 - x : x, y, front[0]);
            if (y == 29) image.setRGB(mirror ? 23 - x : x + 2, y, front[0]);
        }
        for (boolean mirror : new boolean[]{false, true}) {
            for (int i = 0; i < tips.length; i++) image.setRGB(mirror ? 16 - i : 9 + i, 30, tips[i]);
            for (int i = 0; i < fold.length; i++) image.setRGB(mirror ? 16 - i : 9 + i, 29, fold[i]);
        }
    }

    private static int rgba(String hex) {
        long rgba = Long.parseUnsignedLong(hex, 16);
        return (int)((rgba & 0xff) << 24 | (rgba >>> 8));
    }
    private static int[] ints(JsonArray array) {
        int[] values = new int[array.size()]; for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsInt(); return values;
    }
    private static int[] colors(JsonArray array) {
        int[] values = new int[array.size()]; for (int i = 0; i < values.length; i++) values[i] = rgba(array.get(i).getAsString()); return values;
    }
    private static void fill(BufferedImage image, int x, int y, int width, int height, int rgba) {
        for (int yy = y; yy < y + height; yy++) for (int xx = x; xx < x + width; xx++) image.setRGB(xx, yy, rgba);
    }
}
