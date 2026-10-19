package dev.expedition.arcane.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BookModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the installed dependency's models; no upstream geometry or UVs are bundled. */
final class AnimatedSpellBookMeshes {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "irons_spellbooks";
    private static final String[] BOOKS = {"copper", "iron", "gold", "diamond", "netherite", "evoker",
            "necronomicon", "dragonskin", "blaze", "ice", "druidic", "rotten", "villager"};
    private static final String[] PARTS = {"left_lid", "right_lid", "seam", "left_pages", "right_pages", "flip_page1", "flip_page2"};
    private static volatile Map<ResourceLocation, BookMeshGeometry.Definition> definitions;

    private AnimatedSpellBookMeshes() {}

    static Map<ResourceLocation, BookMeshGeometry.Definition> books() {
        var current = definitions;
        if (current == null) {
            reload(Minecraft.getInstance().getResourceManager());
            current = definitions;
        }
        return current;
    }

    static void reload(ResourceManager resources) {
        List<BookMeshGeometry.Template> templates = templates();
        Map<ResourceLocation, BookMeshGeometry.Definition> result = new LinkedHashMap<>();
        for (String name : BOOKS) {
            ResourceLocation id = new ResourceLocation(NAMESPACE, name + "_spell_book");
            try {
                JsonObject item = read(resources, new ResourceLocation(NAMESPACE, "item/" + id.getPath()));
                JsonObject model = resolve(resources, item.has("base") ? item.getAsJsonObject("base") : item, 0);
                String texture = texture(model);
                result.put(id, BookMeshGeometry.build(id.toString(), texture, model.getAsJsonArray("elements"), templates));
            } catch (IOException | RuntimeException e) {
                // Unsupported resource-pack layouts still use the normal item renderer.
                LOGGER.warn("Cannot articulate {} from its installed model; using its item renderer", id, e);
            }
        }
        definitions = Map.copyOf(result);
    }

    private static JsonObject read(ResourceManager resources, ResourceLocation model) throws IOException {
        ResourceLocation path = new ResourceLocation(model.getNamespace(), "models/" + model.getPath() + ".json");
        try (var reader = resources.getResourceOrThrow(path).openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject resolve(ResourceManager resources, JsonObject child, int depth) throws IOException {
        if (depth > 32) throw new IOException("Spell book model parent cycle");
        JsonObject result = child.has("parent") ? resolve(resources, read(resources,
                new ResourceLocation(child.get("parent").getAsString())), depth + 1) : new JsonObject();
        JsonObject textures = result.has("textures") ? result.getAsJsonObject("textures").deepCopy() : new JsonObject();
        if (child.has("textures")) child.getAsJsonObject("textures").entrySet().forEach(e -> textures.add(e.getKey(), e.getValue()));
        child.entrySet().stream().filter(e -> !e.getKey().equals("textures")).forEach(e -> result.add(e.getKey(), e.getValue()));
        result.add("textures", textures);
        return result;
    }

    private static String texture(JsonObject model) throws IOException {
        JsonObject textures = model.getAsJsonObject("textures");
        String selected = null;
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty()) throw new IOException("Spell book model has no elements");
        for (var value : elements) {
            for (var face : value.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                String ref = face.getValue().getAsJsonObject().get("texture").getAsString();
                int depth = 0;
                while (ref.startsWith("#")) {
                    if (++depth > 32 || !textures.has(ref.substring(1))) throw new IOException("Unresolved spell book texture " + ref);
                    ref = textures.get(ref.substring(1)).getAsString();
                }
                ResourceLocation location = new ResourceLocation(ref);
                String path = new ResourceLocation(location.getNamespace(), "textures/" + location.getPath() + ".png").toString();
                if (selected != null && !selected.equals(path)) throw new IOException("Spell book model requires multiple textures");
                selected = path;
            }
        }
        if (selected == null) throw new IOException("Spell book model has no texture");
        return selected;
    }

    private static List<BookMeshGeometry.Template> templates() {
        // Obtain vanilla's actual articulated book cubes and face order through public APIs.
        var root = BookModel.createBodyLayer().bakeRoot();
        List<BookMeshGeometry.Template> result = new ArrayList<>();
        for (String name : PARTS) {
            root.getChild(name).visit(new PoseStack(), (pose, path, index, cube) -> {
                var template = new BookMeshGeometry.Template(name,
                        new double[]{cube.minX, cube.minY, cube.minZ}, new double[]{cube.maxX, cube.maxY, cube.maxZ});
                var collector = new FaceCollector(template);
                cube.compile(new PoseStack().last(), collector, 0, 0, 1, 1, 1, 1);
                result.add(template);
            });
        }
        return result;
    }

    private static final class FaceCollector implements VertexConsumer {
        private final BookMeshGeometry.Template target;
        private final double[][] points = new double[4][];
        private double[] normal;
        private int count;
        FaceCollector(BookMeshGeometry.Template target) { this.target = target; }
        @Override public VertexConsumer vertex(double x, double y, double z) {
            points[count] = new double[]{x * 16, y * 16, z * 16};
            return this;
        }
        @Override public VertexConsumer normal(float x, float y, float z) { normal = new double[]{x, y, z}; return this; }
        @Override public void endVertex() {
            if (++count == 4) {
                String direction = Direction.getNearest((float) normal[0], (float) normal[1], (float) normal[2]).getName();
                target.faces.add(new BookMeshGeometry.Face(direction, points.clone(), normal));
                count = 0;
            }
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }
}
