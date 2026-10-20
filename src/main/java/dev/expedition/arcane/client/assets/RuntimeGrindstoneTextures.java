package dev.expedition.arcane.client.assets;

import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;

/** Authored coarse material patterns, using only color ramps from installed dependencies. */
public final class RuntimeGrindstoneTextures {
    private static final String PREFIX = "textures/block/grindstone/offset_";
    // These are selected shade ranks, not source image coordinates or a texel-index table.
    private static final int[] STONE_RANKS = {3, 4, 5, 6, 7, 9, 10, 11, 13};
    private static final int[] STONE_SHADES = {3, 4, 5, 5, 6, 4, 5, 7};
    // Boundary traversal repeats wide tip, trailing side, long tip, leading side.
    private static final int[][][] TOOTH_PROFILES = {
        {{3, 3, 3}, {3, 3, 3}, {2, 2, 2}},
        {{2}, {2}, {2}},
        {{4, 4, 2}, {4, 4, 2}, {3, 3, 2}},
        {{3}, {3}, {2}}
    };

    private RuntimeGrindstoneTextures() {}

    public static void generate(RuntimeAssetPack.Source source, Map<ResourceLocation, byte[]> output)
            throws IOException {
        int[] nativeStone = palette(source, "create:textures/block/crushing_wheel_plates.png", 18);
        int[] stone = new int[STONE_RANKS.length];
        for (int i = 0; i < stone.length; i++) stone[i] = nativeStone[STONE_RANKS[i]];
        int[] mithril = palette(source, "irons_spellbooks:textures/item/mithril_ingot.png", 8);
        put(output, "stone_rim", stone(64, 16, stone));
        put(output, "stone_cut", stone(16, 16, stone));
        put(output, "mithril_rim", mithrilRim(mithril));
        put(output, "mithril_cut", mithrilCut(mithril));
    }

    private static int[] palette(RuntimeAssetPack.Source source, String id, int expected)
            throws IOException {
        BufferedImage image;
        try (var input = new ByteArrayInputStream(source.read(new ResourceLocation(id)))) {
            image = ImageIO.read(input);
        }
        if (image == null) throw new IOException("Dependency material is not a PNG: " + id);
        var colors = new TreeSet<Integer>(Comparator.comparingInt(RuntimeGrindstoneTextures::brightness)
                .thenComparingInt(Integer::intValue));
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) != 0) colors.add(argb & 0xffffff);
        }
        if (colors.size() != expected) throw new IOException(
                "Installed material has an unsupported shade count: " + id + ": "
                        + colors.size() + " (expected " + expected + ")");
        return colors.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int brightness(int rgb) {
        return (rgb >>> 16) + ((rgb >>> 8) & 255) + (rgb & 255);
    }

    private static BufferedImage stone(int width, int height, int[] palette) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            long fine = hash(x, y, 19), coarse = hash(x / 2, y / 2, 43);
            int shade = STONE_SHADES[(int) (coarse % STONE_SHADES.length)];
            if (fine % 31 == 0) shade = 1;
            if (fine % 41 == 0) shade = 8;
            image.setRGB(x, y, 0xff000000 | palette[shade]);
        }
        return image;
    }

    private static BufferedImage mithrilRim(int[] palette) {
        BufferedImage image = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
        for (int tile = 0; tile < 32; tile++) {
            int[][] profile = TOOTH_PROFILES[tile % TOOTH_PROFILES.length];
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                // Replicate the nearest content texel into the complete eight-pixel gutter.
                int sx = Math.max(0, Math.min(15, x - 8));
                int sy = Math.max(0, Math.min(15, y - 8));
                int row = sy < 5 ? 0 : sy < 11 ? 1 : 2;
                int col = profile[0].length == 1 ? 0 : sx < 5 ? 0 : sx < 11 ? 1 : 2;
                int shade = profile[row][col];
                int rgb = profile[0].length > 1
                        ? grain(palette, shade, row * 3 + col, tile, 9)
                        : palette[shade];
                image.setRGB((tile % 8) * 32 + x, (tile / 8) * 32 + y, 0xff000000 | rgb);
            }
        }
        return image;
    }

    private static BufferedImage mithrilCut(int[] palette) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int row = y / 4, col = x / 4, shade = row < 2 ? 4 : 3;
            image.setRGB(x, y, 0xff000000 | grain(palette, shade, row * 4 + col, 73, 16));
        }
        return image;
    }

    private static int grain(int[] palette, int shade, int cell, int seed, int count) {
        long hash = hash(seed, 19, 211);
        int first = (int) (hash % count);
        int second = (first + 1 + (int) ((hash >>> 8) % (count - 1))) % count;
        if (cell != first && cell != second) return palette[shade];
        int next = Math.max(1, Math.min(5, shade + (cell == first ? 1 : -1)));
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int base = (palette[shade] >>> shift) & 255;
            int target = (palette[next] >>> shift) & 255;
            rgb |= (int) Math.round(base + (target - base) * .45) << shift;
        }
        return rgb;
    }

    /** Java overflow and unsigned shifts match the preview's Math.imul/uint32 hash exactly. */
    private static long hash(int x, int y, int seed) {
        int h = (x + 13) * 374761393 ^ (y + 7) * 668265263 ^ (seed + 31) * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return Integer.toUnsignedLong(h ^ (h >>> 16));
    }

    private static void put(Map<ResourceLocation, byte[]> output, String name, BufferedImage image)
            throws IOException {
        try (var bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) throw new IOException("PNG encoder unavailable");
            output.put(new ResourceLocation("arcane_industry", PREFIX + name + ".png"), bytes.toByteArray());
        }
    }
}
