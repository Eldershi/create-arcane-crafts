package dev.expedition.arcane.client.assets;

import com.google.gson.*;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads installed dependency models; only our fit, material and animation rules are shipped. */
public final class RuntimeModelAssets {
    private static final Gson GSON = new Gson();
    private static final String PREFIX = "models/block/ascension_chamber/";
    private static final String TEX = "arcane_industry:textures/entity/chamber/";

    private RuntimeModelAssets() {}

    public static void generate(
            RuntimeAssetPack.Source source, Map<ResourceLocation, byte[]> output)
            throws IOException {
        JsonObject design;
        try (var input =
                RuntimeModelAssets.class.getResourceAsStream(
                        "/assets/arcane_industry/runtime/chamber_effect_design.json")) {
            if (input == null) throw new IOException("Missing authored chamber effect design");
            design =
                    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
        }
        JsonObject geometry = design.deepCopy();
        JsonObject heads = new JsonObject();
        JsonObject inert = json(source, "create:models/block/blaze_burner/blaze/inert.json");
        List<Face> idle = jsonFaces(inert, TEX + "blaze_inert_low_eyes.png", true);
        // Our two thin, low eyebrows are independent planes, fitted to the native inert head.
        idle.add(
                face(
                        new double[][] {
                            {7, 8.75, 4.8}, {7, 7.75, 4.8}, {5, 7.75, 4.8}, {5, 8.75, 4.8}
                        },
                        rect(2.0 / 16, 0, 0, 1.0 / 16),
                        TEX + "brows.png"));
        idle.add(
                face(
                        new double[][] {
                            {11, 8.75, 4.8}, {11, 7.75, 4.8}, {9, 7.75, 4.8}, {9, 8.75, 4.8}
                        },
                        rect(6.0 / 16, 0, 4.0 / 16, 1.0 / 16),
                        TEX + "brows.png"));
        heads.add("idle", facesJson(idle));
        JsonObject nativeHead = json(source, "create:models/block/blaze_burner/blaze/idle.json");
        JsonObject e = nativeHead.getAsJsonArray("elements").get(0).getAsJsonObject();
        double[] lo = numbers(e.getAsJsonArray("from")), hi = numbers(e.getAsJsonArray("to"));
        for (String state : List.of("active", "super")) {
            List<Face> head =
                    entityBox(
                            lo,
                            subtract(hi, lo),
                            0,
                            0,
                            64,
                            32,
                            TEX + "arcane_blaze_" + state + ".png");
            head.addAll(
                    entityBox(
                            new double[] {lo[0] - 1, lo[1] + 2, lo[2] - .2},
                            new double[] {hi[0] - lo[0] + 2, 3, 4},
                            4,
                            24,
                            64,
                            32,
                            TEX + "arcane_brows_" + state + ".png"));
            heads.add(state, facesJson(head));
        }
        geometry.add("heads", heads);
        JsonObject rods = new JsonObject();
        for (String state : List.of("active", "super")) {
            JsonObject groups = new JsonObject();
            for (String group : List.of("small", "large")) {
                List<Face> faces =
                        obj(
                                source,
                                "create:models/block/blaze_burner/blaze_rods_" + group + ".obj",
                                null);
                for (Face f : faces) {
                    f.texture = TEX + "chamber_rods_" + state + ".png";
                    if (state.equals("super") && flatY(f.points) && maxV(f.uv) <= 2.001 / 16) {
                        for (double[] uv : f.uv) uv[0] += .25;
                    }
                }
                groups.add(group, facesJson(faces));
            }
            rods.add(state, groups);
        }
        geometry.add("rods", rods);
        List<Face> allFlames =
                obj(source, "create:models/block/blaze_burner/blaze_flame.obj", null);
        List<Face> surfaces = new ArrayList<>();
        for (Face f : allFlames.subList(allFlames.size() / 2, allFlames.size())) {
            for (double[] uv : f.uv) {
                uv[0] *= 16;
                uv[1] *= 16;
            }
            surfaces.add(f);
        }
        geometry.add("surfaces", facesJson(surfaces));
        put(output, PREFIX + "runtime_geometry.json", geometry);

        JsonObject decorations = new JsonObject(),
                goggles = new JsonObject(),
                crowns = new JsonObject();
        JsonObject crown =
                json(source, "irons_spellbooks:geo/tarnished_armor.geo.json")
                        .getAsJsonArray("minecraft:geometry")
                        .get(0)
                        .getAsJsonObject();
        JsonObject description = crown.getAsJsonObject("description");
        double tw = description.get("texture_width").getAsDouble(),
                th = description.get("texture_height").getAsDouble();
        JsonObject crownHead = null;
        for (JsonElement bone : crown.getAsJsonArray("bones"))
            if (bone.getAsJsonObject().get("name").getAsString().equals("armorHead"))
                crownHead = bone.getAsJsonObject();
        if (crownHead == null) throw new IOException("Installed Iron crown has no armorHead");
        for (String size : List.of("idle", "active")) {
            boolean small = size.equals("idle");
            List<Face> g =
                    jsonFaces(
                            json(
                                    source,
                                    "create:models/block/blaze_burner/goggles"
                                            + (small ? "_small" : "")
                                            + ".json"),
                            "create:textures/block/hat_test.png",
                            false);
            for (Face f : g) for (double[] v : f.points) v[1] += 6;
            goggles.add(size, facesJson(g));
            List<Face> c = new ArrayList<>();
            for (JsonElement cubeValue : crownHead.getAsJsonArray("cubes")) {
                JsonObject cube = cubeValue.getAsJsonObject();
                double inflate = cube.has("inflate") ? cube.get("inflate").getAsDouble() : 0;
                double[] origin = numbers(cube.getAsJsonArray("origin")),
                        dimensions = numbers(cube.getAsJsonArray("size"));
                double[] min = origin.clone(), max = origin.clone();
                for (int i = 0; i < 3; i++) {
                    min[i] -= inflate;
                    max[i] += dimensions[i] + inflate;
                }
                Map<String, double[][]> points = box(min, max);
                for (var entry : cube.getAsJsonObject("uv").entrySet()) {
                    JsonObject uvData = entry.getValue().getAsJsonObject();
                    double[] uv = numbers(uvData.getAsJsonArray("uv")),
                            delta = numbers(uvData.getAsJsonArray("uv_size"));
                    double[][] vertices = points.get(entry.getKey());
                    double scale = small ? .6 : .8;
                    for (double[] v : vertices) {
                        v[0] = v[0] * scale + 8;
                        v[1] = (v[1] - 24) * scale + 7;
                        v[2] = v[2] * scale + 8;
                    }
                    c.add(
                            face(
                                    vertices,
                                    rect(
                                            uv[0] / tw,
                                            uv[1] / th,
                                            (uv[0] + delta[0]) / tw,
                                            (uv[1] + delta[1]) / th),
                                    "irons_spellbooks:textures/models/armor/tarnished.png"));
                }
            }
            crowns.add(size, facesJson(c));
        }
        decorations.add("goggles", goggles);
        decorations.add("crown", crowns);
        put(output, PREFIX + "runtime_decorations.json", decorations);
        JsonObject fire = new JsonObject();
        fire.addProperty("sprite", "minecraft:block/campfire_fire");
        fire.add(
                "faces",
                facesJson(
                        obj(source, "create:models/block/blaze_burner/campfire.obj", "Campfire")));
        put(output, PREFIX + "runtime_residual_fire.json", fire);

        List<Face> nativeCage =
                obj(source, "create:models/block/blaze_burner/blaze_cage.obj", null);
        JsonObject cage = cageModel(nativeCage);
        for (String state : List.of("idle", "active", "super"))
            put(output, "models/block/ascension_chamber_" + state + ".json", cage);
        List<Face> item = jsonFaces(cage, null, false, true);
        item.addAll(readFaces(heads.getAsJsonArray("active")));
        for (String group : List.of("small", "large")) {
            List<Face> rf = readFaces(rods.getAsJsonObject("active").getAsJsonArray(group));
            for (Face f : rf) for (double[] v : f.points) v[1] += group.equals("small") ? 2 : -2.75;
            item.addAll(rf);
        }
        String[] materials = {
            TEX + "chamber_cage.png",
            TEX + "chamber_base_top.png",
            TEX + "arcane_blaze_active.png",
            TEX + "arcane_brows_active.png",
            TEX + "chamber_rods_active.png",
            TEX + "chamber_base_wrap.png"
        };
        StringBuilder obj = new StringBuilder("mtllib selected_item.mtl\n"),
                mtl = new StringBuilder();
        for (int i = 0; i < materials.length; i++)
            mtl.append("newmtl m_").append(i).append("\nmap_Kd #m_").append(i).append('\n');
        int vertex = 1, normal = 1;
        for (Face f : item) {
            String texture = f.texture.replace(":block/", ":textures/entity/chamber/");
            if (!texture.endsWith(".png")) texture += ".png";
            int material = Arrays.asList(materials).indexOf(texture);
            if (material < 0) throw new IOException("Unknown item material " + texture);
            obj.append("usemtl m_").append(material).append('\n');
            for (double[] v : f.points)
                obj.append("v ")
                        .append(fmt(v[0] / 16))
                        .append(' ')
                        .append(fmt(v[1] / 16))
                        .append(' ')
                        .append(fmt(v[2] / 16))
                        .append('\n');
            for (double[] uv : f.uv)
                obj.append("vt ")
                        .append(fmt(uv[0]))
                        .append(' ')
                        .append(fmt(1 - uv[1]))
                        .append('\n');
            double[] n = normal(f.points);
            obj.append("vn ")
                    .append(fmt(n[0]))
                    .append(' ')
                    .append(fmt(n[1]))
                    .append(' ')
                    .append(fmt(n[2]))
                    .append('\n');
            obj.append('f');
            for (int i = 0; i < f.points.length; i++)
                obj.append(' ')
                        .append(vertex + i)
                        .append('/')
                        .append(vertex + i)
                        .append('/')
                        .append(normal);
            obj.append('\n');
            vertex += f.points.length;
            normal++;
        }
        put(output, PREFIX + "selected_item.obj", obj.toString());
        put(output, PREFIX + "selected_item.mtl", mtl.toString());
    }

    private static JsonObject cageModel(List<Face> nativeCage) throws IOException {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.addProperty("render_type", "minecraft:cutout");
        JsonObject textures = new JsonObject();
        for (String name : List.of("cage", "base_top", "base_wrap"))
            textures.addProperty(name, "arcane_industry:block/chamber_" + name);
        textures.addProperty("particle", "arcane_industry:block/chamber_copper");
        model.add("textures", textures);
        JsonArray elements = new JsonArray();
        JsonObject base =
                element(
                        "brass_wrapped_amethyst_base",
                        new double[] {2, 0, 2},
                        new double[] {14, 4, 14});
        JsonObject faces = new JsonObject();
        for (String side : List.of("down", "up", "north", "south", "west", "east")) {
            JsonObject f = new JsonObject();
            f.add(
                    "uv",
                    array(
                            side.equals("up")
                                    ? new double[] {0, 0, 12, 12}
                                    : side.equals("down")
                                            ? new double[] {9, 10, 15, 16}
                                            : new double[] {9, 8, 15, 10}));
            f.addProperty("texture", side.equals("up") ? "#base_top" : "#base_wrap");
            faces.add(side, f);
        }
        base.add("faces", faces);
        elements.add(base);
        double diagonal = Math.sqrt(2);
        String[] names = {"south", "north", "east", "west"};
        for (int side = 0; side < 4; side++) {
            boolean z = side < 2;
            double fixed = side % 2 == 0 ? 15 : 1, direction = side % 2 == 0 ? 1 : -1;
            addSheet(
                    elements,
                    nativeCage,
                    names[side] + "_wall",
                    z ? new double[] {1, 5, fixed} : new double[] {fixed, 5, 1},
                    z ? new double[] {15, 14, fixed} : new double[] {fixed, 14, 15},
                    z ? "z" : "x",
                    null,
                    0,
                    null);
            for (boolean upper : new boolean[] {true, false}) {
                double y = upper ? 14 : 4, position = upper ? fixed : fixed - direction;
                addSheet(
                        elements,
                        nativeCage,
                        names[side] + (upper ? "_upper_fold" : "_lower_fold"),
                        z ? new double[] {1, y, position} : new double[] {position, y, 1},
                        z
                                ? new double[] {15, y + diagonal, position}
                                : new double[] {position, y + diagonal, 15},
                        z ? "z" : "x",
                        z ? "x" : "z",
                        (z ? 45 : -45) * direction,
                        z ? new double[] {8, y, position} : new double[] {position, y, 8});
            }
        }
        double[][] feet = {{14, 14, -45}, {14, 2, 45}, {0, 0, -45}, {0, 16, 45}};
        String[] feetNames = {"south_east", "north_east", "north_west", "south_west"};
        for (int i = 0; i < 4; i++) {
            double x = feet[i][0], z = feet[i][1];
            addSheet(
                    elements,
                    nativeCage,
                    feetNames[i] + "_diagonal_foot",
                    new double[] {x, 0, z},
                    new double[] {x + 2 * diagonal, 5, z},
                    "z",
                    "y",
                    feet[i][2],
                    new double[] {x, 0, z});
        }
        model.add("elements", elements);
        model.add(
                "display",
                JsonParser.parseString(
                        "{\"gui\":{\"rotation\":[30,225,0],\"scale\":[0.7,0.7,0.7]},\"fixed\":{\"rotation\":[0,0,0],\"scale\":[0.5,0.5,0.5]}}"));
        return model;
    }

    private static void addSheet(
            JsonArray elements,
            List<Face> nativeCage,
            String name,
            double[] lo,
            double[] hi,
            String axis,
            String rotationAxis,
            double angle,
            double[] origin)
            throws IOException {
        JsonObject element = element(name, lo, hi);
        if (rotationAxis != null) {
            JsonObject r = new JsonObject();
            r.add("origin", array(origin));
            r.addProperty("axis", rotationAxis);
            r.addProperty("angle", angle);
            r.addProperty("rescale", false);
            element.add("rotation", r);
        }
        JsonObject faces = new JsonObject();
        for (String side : axis.equals("z") ? List.of("north", "south") : List.of("west", "east")) {
            double[][] points = box(lo, hi).get(side);
            for (double[] v : points) rotate(v, rotationAxis, angle, origin);
            // Block-model face winding is the opposite of our generic inward preview convention.
            double[] n = normal(points);
            Face original = null;
            for (Face f : nativeCage)
                if (sameQuad(points, f.points) && dot(n, normal(f.points)) < 0) {
                    original = f;
                    break;
                }
            if (original == null)
                throw new IOException("Installed Create cage face not found: " + name + "/" + side);
            double[][] uv = new double[4][2];
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 4; j++)
                    if (distance(points[i], original.points[j]) < 1e-8) uv[i] = original.uv[j];
            // Inward corners are reversed in vanilla's north/south/west/east face construction.
            double[] rect = {uv[3][0] * 16, uv[3][1] * 16, uv[1][0] * 16, uv[1][1] * 16};
            // Derive UV rectangle by testing the same vertex mapping the block baker uses.
            double[][] baked = {points[3], points[2], points[1], points[0]};
            double[][] target = {uv[3], uv[2], uv[1], uv[0]};
            rect =
                    new double[] {
                        target[0][0] * 16, target[0][1] * 16, target[2][0] * 16, target[2][1] * 16
                    };
            JsonObject face = new JsonObject();
            face.add("uv", array(rect));
            face.addProperty("texture", "#cage");
            faces.add(side, face);
        }
        element.add("faces", faces);
        elements.add(element);
    }

    private static JsonObject element(String name, double[] from, double[] to) {
        JsonObject e = new JsonObject();
        e.addProperty("name", name);
        e.add("from", array(from));
        e.add("to", array(to));
        return e;
    }

    private static JsonObject json(RuntimeAssetPack.Source source, String id) throws IOException {
        return JsonParser.parseString(
                        new String(source.read(new ResourceLocation(id)), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static void put(Map<ResourceLocation, byte[]> out, String path, JsonElement value) {
        put(out, path, GSON.toJson(value) + "\n");
    }

    private static void put(Map<ResourceLocation, byte[]> out, String path, String value) {
        out.put(
                new ResourceLocation("arcane_industry", path),
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String fmt(double x) {
        return String.format(Locale.ROOT, "%.9g", x);
    }

    private static double[] numbers(JsonArray a) {
        double[] x = new double[a.size()];
        for (int i = 0; i < x.length; i++) x[i] = a.get(i).getAsDouble();
        return x;
    }

    private static JsonArray array(double[] a) {
        JsonArray j = new JsonArray();
        for (double x : a) j.add(x);
        return j;
    }

    private static JsonArray matrix(double[][] a) {
        JsonArray j = new JsonArray();
        for (double[] x : a) j.add(array(x));
        return j;
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double distance(double[] a, double[] b) {
        double[] d = subtract(a, b);
        return dot(d, d);
    }

    private static double[] normal(double[][] p) {
        double[] a = subtract(p[1], p[0]), b = subtract(p[2], p[0]);
        double[] n = {
            a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
        };
        double l = Math.sqrt(dot(n, n));
        if (l > 0) for (int i = 0; i < 3; i++) n[i] /= l;
        return n;
    }

    private static boolean sameQuad(double[][] a, double[][] b) {
        if (a.length != b.length) return false;
        for (double[] p : a) {
            boolean found = false;
            for (double[] q : b) if (distance(p, q) < 1e-8) found = true;
            if (!found) return false;
        }
        return true;
    }

    private static boolean flatY(double[][] p) {
        for (double[] q : p) if (Math.abs(q[1] - p[0][1]) > 1e-7) return false;
        return true;
    }

    private static double maxV(double[][] uv) {
        double max = -Double.MAX_VALUE;
        for (double[] q : uv) max = Math.max(max, q[1]);
        return max;
    }

    private static double[][] rect(double u, double v, double U, double V) {
        return new double[][] {{u, v}, {u, V}, {U, V}, {U, v}};
    }

    private static final class Face {
        double[][] points, uv;
        String texture;

        Face(double[][] p, double[][] u, String t) {
            points = p;
            uv = u;
            texture = t;
        }
    }

    private static Face face(double[][] p, double[][] uv, String t) {
        return new Face(p, uv, t);
    }

    private static JsonArray facesJson(List<Face> faces) {
        JsonArray a = new JsonArray();
        for (Face f : faces) {
            JsonObject j = new JsonObject();
            j.add("points", matrix(f.points));
            j.add("uv", matrix(f.uv));
            if (f.texture != null) j.addProperty("texture", f.texture);
            a.add(j);
        }
        return a;
    }

    private static List<Face> readFaces(JsonArray a) {
        List<Face> fs = new ArrayList<>();
        for (JsonElement el : a) {
            JsonObject j = el.getAsJsonObject();
            double[][] p = new double[j.getAsJsonArray("points").size()][],
                    uv = new double[p.length][];
            for (int i = 0; i < p.length; i++) {
                p[i] = numbers(j.getAsJsonArray("points").get(i).getAsJsonArray());
                uv[i] = numbers(j.getAsJsonArray("uv").get(i).getAsJsonArray());
            }
            fs.add(face(p, uv, j.has("texture") ? j.get("texture").getAsString() : null));
        }
        return fs;
    }

    private static Map<String, double[][]> box(double[] a, double[] b) {
        double x = a[0], y = a[1], z = a[2], X = b[0], Y = b[1], Z = b[2];
        Map<String, double[][]> m = new LinkedHashMap<>();
        m.put("north", new double[][] {{x, Y, z}, {x, y, z}, {X, y, z}, {X, Y, z}});
        m.put("east", new double[][] {{X, Y, z}, {X, y, z}, {X, y, Z}, {X, Y, Z}});
        m.put("south", new double[][] {{X, Y, Z}, {X, y, Z}, {x, y, Z}, {x, Y, Z}});
        m.put("west", new double[][] {{x, Y, Z}, {x, y, Z}, {x, y, z}, {x, Y, z}});
        m.put("up", new double[][] {{x, Y, Z}, {x, Y, z}, {X, Y, z}, {X, Y, Z}});
        m.put("down", new double[][] {{x, y, z}, {x, y, Z}, {X, y, Z}, {X, y, z}});
        return m;
    }

    private static void rotate(double[] p, String axis, double angle, double[] origin) {
        if (axis == null || angle == 0) return;
        double x = p[0] - origin[0],
                y = p[1] - origin[1],
                z = p[2] - origin[2],
                c = Math.cos(Math.toRadians(angle)),
                s = Math.sin(Math.toRadians(angle));
        double[] q =
                switch (axis) {
                    case "x" -> new double[] {x, y * c - z * s, y * s + z * c};
                    case "y" -> new double[] {x * c + z * s, y, -x * s + z * c};
                    default -> new double[] {x * c - y * s, x * s + y * c, z};
                };
        for (int i = 0; i < 3; i++) p[i] = q[i] + origin[i];
    }

    private static List<Face> jsonFaces(JsonObject model, String forced, boolean reverse) {
        return jsonFaces(model, forced, reverse, false);
    }

    private static List<Face> jsonFaces(
            JsonObject model, String forced, boolean reverse, boolean baked) {
        List<Face> result = new ArrayList<>();
        JsonObject textures = model.getAsJsonObject("textures");
        for (JsonElement el : model.getAsJsonArray("elements")) {
            JsonObject e = el.getAsJsonObject();
            Map<String, double[][]> cube =
                    box(numbers(e.getAsJsonArray("from")), numbers(e.getAsJsonArray("to")));
            for (var entry : e.getAsJsonObject("faces").entrySet()) {
                JsonObject spec = entry.getValue().getAsJsonObject();
                double[][] points = cube.get(entry.getKey());
                if (baked) {
                    int[] order =
                            entry.getKey().equals("up") || entry.getKey().equals("down")
                                    ? new int[] {1, 0, 3, 2}
                                    : new int[] {3, 2, 1, 0};
                    points =
                            new double[][] {
                                points[order[0]],
                                points[order[1]],
                                points[order[2]],
                                points[order[3]]
                            };
                }
                if (e.has("rotation")) {
                    JsonObject r = e.getAsJsonObject("rotation");
                    for (double[] p : points)
                        rotate(
                                p,
                                r.get("axis").getAsString(),
                                r.get("angle").getAsDouble(),
                                numbers(r.getAsJsonArray("origin")));
                }
                double[] uv = numbers(spec.getAsJsonArray("uv"));
                double[][] coords = rect(uv[0] / 16, uv[1] / 16, uv[2] / 16, uv[3] / 16);
                int turns = spec.has("rotation") ? spec.get("rotation").getAsInt() / 90 : 0;
                for (int i = 0; i < turns; i++) {
                    double[] first = coords[0];
                    System.arraycopy(coords, 1, coords, 0, 3);
                    coords[3] = first;
                }
                if (reverse) {
                    Collections.reverse(Arrays.asList(points));
                    Collections.reverse(Arrays.asList(coords));
                }
                String texture = forced;
                if (texture == null) {
                    texture = spec.get("texture").getAsString();
                    while (texture.startsWith("#"))
                        texture = textures.get(texture.substring(1)).getAsString();
                }
                result.add(face(points, coords, texture));
            }
        }
        return result;
    }

    private static List<Face> entityBox(
            double[] lo, double[] size, double u, double v, double w, double h, String texture) {
        Map<String, double[][]> shapes =
                box(lo, new double[] {lo[0] + size[0], lo[1] + size[1], lo[2] + size[2]});
        double x = size[0], y = size[1], z = size[2];
        Map<String, double[]> uv = new HashMap<>();
        uv.put("west", new double[] {u, v + z, u + z, v + z + y});
        uv.put("north", new double[] {u + z, v + z, u + z + x, v + z + y});
        uv.put("east", new double[] {u + z + x, v + z, u + 2 * z + x, v + z + y});
        uv.put("south", new double[] {u + 2 * z + x, v + z, u + 2 * z + 2 * x, v + z + y});
        uv.put("down", new double[] {u + z, v, u + z + x, v + z});
        uv.put("up", new double[] {u + z + x, v, u + z + 2 * x, v + z});
        List<Face> result = new ArrayList<>();
        for (var entry : shapes.entrySet()) {
            double[] r = uv.get(entry.getKey());
            double[][] coords = rect(r[0] / w, r[1] / h, r[2] / w, r[3] / h);
            if (!entry.getKey().equals("up") && !entry.getKey().equals("down"))
                coords = new double[][] {coords[1], coords[0], coords[3], coords[2]};
            double[][] positions = entry.getValue();
            for (double[] point : positions) point[1] = lo[1] * 2 + size[1] - point[1];
            result.add(face(positions, coords, texture));
        }
        return result;
    }

    private static List<Face> obj(RuntimeAssetPack.Source source, String id, String filter)
            throws IOException {
        String text = new String(source.read(new ResourceLocation(id)), StandardCharsets.UTF_8);
        List<double[]> vertices = new ArrayList<>(), uv = new ArrayList<>();
        List<Face> result = new ArrayList<>();
        String material = "";
        for (String line : text.split("\\R")) {
            String[] a = line.trim().split("\\s+");
            switch (a[0]) {
                case "v" ->
                        vertices.add(
                                new double[] {
                                    Double.parseDouble(a[1]) * 16,
                                    Double.parseDouble(a[2]) * 16,
                                    Double.parseDouble(a[3]) * 16
                                });
                case "vt" ->
                        uv.add(
                                new double[] {
                                    Double.parseDouble(a[1]), 1 - Double.parseDouble(a[2])
                                });
                case "usemtl" -> material = a[1];
                case "f" -> {
                    if (filter != null && !filter.equals(material)) continue;
                    double[][] p = new double[a.length - 1][], t = new double[p.length][];
                    for (int i = 1; i < a.length; i++) {
                        String[] ix = a[i].split("/");
                        p[i - 1] = vertices.get(Integer.parseInt(ix[0]) - 1).clone();
                        t[i - 1] = uv.get(Integer.parseInt(ix[1]) - 1).clone();
                    }
                    result.add(face(p, t, null));
                }
            }
        }
        return result;
    }
}
