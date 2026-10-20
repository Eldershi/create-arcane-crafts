package dev.expedition.arcane.client.assets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Derives the selected three-band wheel from the player's installed CEI wheel geometry. */
public final class RuntimeGrindstoneModels {
    private static final Gson GSON = new Gson();
    private static final double EPS = 1e-8;
    private static final double MIDDLE_LOW = 6.25, MIDDLE_HIGH = 9.75;
    private static final String[] MATERIALS = {
        "stone_rim", "stone_cut", "mithril_rim", "mithril_cut"
    };

    private RuntimeGrindstoneModels() {}

    public static void generate(RuntimeAssetPack.Source source, Map<ResourceLocation, byte[]> output)
            throws IOException {
        JsonObject wheel = read(source, "create_enchantment_industry:models/block/mechanical_grindstone.json");
        JsonObject drain = read(source, "create_enchantment_industry:models/block/grindstone_drain/item.json");
        JsonArray nativeElements = wheel.getAsJsonArray("elements");
        if (nativeElements.size() != 10 || drain.getAsJsonArray("elements").size() != 16)
            throw new IOException("Unsupported installed CEI grindstone geometry");

        List<List<Point>> stone = new ArrayList<>();
        for (int i = 0; i < 8; i++) stone.add(polygon(nativeElements.get(i).getAsJsonObject()));
        List<List<Point>> tips = new ArrayList<>(stone.subList(4, 8));
        JsonObject cover = nativeElements.get(9).getAsJsonObject();
        List<Point> hole = rectangle(cover);
        double radius = 0;
        for (List<Point> polygon : stone)
            for (Point p : polygon) radius = Math.max(radius, Math.hypot(p.x - 8, p.z - 8));
        // One model pixel of maximum outer-vertex chord displacement around the original shaft.
        double phase = Math.toDegrees(2 * Math.asin(1 / (2 * radius)));
        List<List<Point>> metal = rotate(stone, phase);
        List<List<Point>> allShapes = new ArrayList<>(stone);
        allShapes.addAll(metal);
        allShapes.add(hole);
        List<Double> events = decompositionEvents(allShapes);
        List<List<Point>> tipLoops = boundary(tips), stoneLoops = boundary(stone);
        List<List<Point>> metalLoops = rotate(stoneLoops, phase);

        List<List<Point>> cap = cells(tips, List.of(hole), events);
        List<List<Point>> innerSubtract = new ArrayList<>(tips);
        innerSubtract.add(hole);
        List<List<Point>> ledge = cells(stone, innerSubtract, events);
        List<List<Point>> stoneCut = cells(stone, metal, events);
        List<List<Point>> metalCut = cells(metal, stone, events);
        JsonObject broad = nativeElements.get(0).getAsJsonObject();
        JsonObject narrow = nativeElements.get(4).getAsJsonObject();
        double broadLow = coordinate(broad, "from", 1), broadHigh = coordinate(broad, "to", 1);
        double tipLow = coordinate(narrow, "from", 1), tipHigh = coordinate(narrow, "to", 1);

        List<Face> faces = new ArrayList<>();
        rim(faces, tipLoops, tipLow, broadLow, "stone", false);
        rim(faces, stoneLoops, broadLow, MIDDLE_LOW, "stone", false);
        rim(faces, metalLoops, MIDDLE_LOW, MIDDLE_HIGH, "mithril", true);
        rim(faces, stoneLoops, MIDDLE_HIGH, broadHigh, "stone", false);
        rim(faces, tipLoops, broadHigh, tipHigh, "stone", false);
        cap(faces, cap, tipLow, false, "stone", phase);
        cap(faces, ledge, broadLow, false, "stone", phase);
        cap(faces, stoneCut, MIDDLE_LOW, true, "stone", phase);
        cap(faces, metalCut, MIDDLE_LOW, false, "mithril", phase);
        cap(faces, metalCut, MIDDLE_HIGH, true, "mithril", phase);
        cap(faces, stoneCut, MIDDLE_HIGH, false, "stone", phase);
        cap(faces, ledge, broadHigh, true, "stone", phase);
        cap(faces, cap, tipHigh, true, "stone", phase);

        int tile = 0;
        for (Face face : faces) {
            if (!face.atlas) continue;
            // Keep the native boundary traversal order: the selected atlas uses these 32 tiles.
            double x = (tile % 8) * 32 + 8.5, y = (tile / 8) * 32 + 8.5;
            face.uv = new double[][] {
                {round(x / 16, 7), round(y / 8, 7)},
                {round((x + 15) / 16, 7), round(y / 8, 7)},
                {round((x + 15) / 16, 7), round((y + 15) / 8, 7)},
                {round(x / 16, 7), round((y + 15) / 8, 7)}
            };
            tile++;
        }
        if (tile != 32) throw new IOException("Installed wheel does not match the selected 32-face material atlas");
        put(output, "models/block/offset_grindstone_y.obj", obj(faces, false));
        put(output, "models/block/offset_grindstone_z.obj", obj(faces, true));
        StringBuilder mtl = new StringBuilder();
        for (String material : MATERIALS) {
            if (!mtl.isEmpty()) mtl.append('\n');
            mtl.append("newmtl ").append(material).append("\nmap_Kd #").append(material)
                    .append("\nKd 1 1 1\n");
        }
        put(output, "models/block/offset_grindstone.mtl", mtl.toString());
        put(output, "models/block/arcane_grindstone.json", composite(wheel, false));
        put(output, "models/item/arcane_grindstone_drain.json", composite(drain, true));
    }

    private static JsonObject read(RuntimeAssetPack.Source source, String id) throws IOException {
        return JsonParser.parseString(new String(source.read(new ResourceLocation(id)), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static void put(Map<ResourceLocation, byte[]> output, String path, Object value) {
        String text = value instanceof String string ? string : GSON.toJson(value);
        output.put(new ResourceLocation("arcane_industry", path), text.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject composite(JsonObject source, boolean combined) {
        JsonObject result = new JsonObject();
        result.addProperty("parent", combined ? "minecraft:block/block" : "create:block/large_wheels");
        result.addProperty("loader", "forge:composite");
        result.addProperty("render_type", "minecraft:cutout_mipped");
        JsonObject particle = new JsonObject();
        particle.add("particle", source.getAsJsonObject("textures").get("cover").deepCopy());
        result.add("textures", particle);
        JsonObject children = new JsonObject(), material = new JsonObject();
        material.addProperty("loader", "forge:obj");
        material.addProperty("model", "arcane_industry:models/block/offset_grindstone_" + (combined ? "z" : "y") + ".obj");
        material.addProperty("automatic_culling", false);
        material.addProperty("shade_quads", true);
        material.addProperty("flip_v", false);
        material.addProperty("render_type", "minecraft:cutout_mipped");
        JsonObject textures = new JsonObject();
        for (String name : MATERIALS)
            textures.addProperty(name, "arcane_industry:block/grindstone/offset_" + name);
        textures.addProperty("particle", "arcane_industry:block/grindstone/offset_stone_cut");
        material.add("textures", textures);
        children.add("wheel", material);
        JsonObject nativePart = new JsonObject();
        nativePart.addProperty("render_type", "minecraft:cutout_mipped");
        JsonObject nativeTextures = source.getAsJsonObject("textures").deepCopy();
        nativeTextures.remove("cog");
        nativePart.add("textures", nativeTextures);
        JsonArray elements = source.getAsJsonArray("elements"), keep = new JsonArray();
        if (combined) for (int i = 0; i < 6; i++) keep.add(elements.get(i).deepCopy());
        for (int i = combined ? 14 : 8; i < (combined ? 16 : 10); i++) keep.add(elements.get(i).deepCopy());
        nativePart.add("elements", keep);
        children.add("native", nativePart);
        result.add("children", children);
        JsonArray order = new JsonArray();
        order.add("wheel");
        order.add("native");
        result.add("item_render_order", order);
        return result;
    }

    private record Point(double x, double z) {}
    private record PointKey(long x, long z) {}
    private record Edge(Point a, Point b) {}
    private record EdgeKey(PointKey a, PointKey b) {}
    private record Line(double slope, double intercept) {
        double at(double x) { return slope * x + intercept; }
    }
    private record Interval(Line low, Line high) {}
    private record RunKey(long lowSlope, long lowIntercept, long highSlope, long highIntercept) {}
    private static final class Run {
        final double left;
        double right;
        final Line low, high;
        Run(double left, double right, Line low, Line high) {
            this.left = left; this.right = right; this.low = low; this.high = high;
        }
    }
    private static final class Face {
        final double[][] points;
        double[][] uv;
        final String material;
        final boolean atlas;
        Face(double[][] points, double[][] uv, String material, boolean atlas) {
            this.points = points; this.uv = uv; this.material = material; this.atlas = atlas;
        }
    }

    private static double coordinate(JsonObject element, String field, int axis) {
        return element.getAsJsonArray(field).get(axis).getAsDouble();
    }
    private static List<Point> rectangle(JsonObject element) {
        double x0 = coordinate(element, "from", 0), z0 = coordinate(element, "from", 2);
        double x1 = coordinate(element, "to", 0), z1 = coordinate(element, "to", 2);
        return List.of(new Point(x0, z0), new Point(x1, z0), new Point(x1, z1), new Point(x0, z1));
    }
    private static List<Point> polygon(JsonObject element) throws IOException {
        List<Point> result = rectangle(element);
        if (!element.has("rotation")) return result;
        JsonObject rotation = element.getAsJsonObject("rotation");
        if (!"y".equals(rotation.get("axis").getAsString()))
            throw new IOException("Installed wheel has a non-Y authored element rotation");
        double angle = Math.toRadians(rotation.get("angle").getAsDouble());
        JsonArray origin = rotation.getAsJsonArray("origin");
        double ox = origin.get(0).getAsDouble(), oz = origin.get(2).getAsDouble();
        List<Point> rotated = new ArrayList<>();
        for (Point p : result) rotated.add(turn(p, angle, ox, oz));
        return rotated;
    }
    private static Point turn(Point p, double radians, double ox, double oz) {
        double x = p.x - ox, z = p.z - oz, c = Math.cos(radians), s = Math.sin(radians);
        return new Point(ox + x * c + z * s, oz - x * s + z * c);
    }
    private static List<List<Point>> rotate(List<List<Point>> polygons, double angle) {
        List<List<Point>> result = new ArrayList<>();
        for (List<Point> polygon : polygons) {
            List<Point> rotated = new ArrayList<>();
            for (Point p : polygon) rotated.add(turn(p, Math.toRadians(angle), 8, 8));
            result.add(rotated);
        }
        return result;
    }
    private static Point minus(Point a, Point b) { return new Point(a.x - b.x, a.z - b.z); }
    private static double cross(Point a, Point b) { return a.x * b.z - a.z * b.x; }
    private static double distance(Point a, Point b) { return Math.hypot(a.x - b.x, a.z - b.z); }
    private static Point at(Edge edge, double t) {
        return new Point(edge.a.x + (edge.b.x - edge.a.x) * t, edge.a.z + (edge.b.z - edge.a.z) * t);
    }
    private static long key(double number) { return (long) Math.rint(number * 1e8); }
    private static PointKey key(Point point) { return new PointKey(key(point.x), key(point.z)); }
    private static double round(double number, int places) {
        double power = Math.pow(10, places);
        return Math.rint(number * power) / power;
    }
    private static double signed(List<Point> polygon) {
        double sum = 0;
        for (int i = 0; i < polygon.size(); i++) sum += cross(polygon.get(i), polygon.get((i + 1) % polygon.size()));
        return sum / 2;
    }
    private static boolean inside(Point point, List<Point> polygon) {
        for (int i = 0; i < polygon.size(); i++) {
            Point a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            if (cross(minus(b, a), minus(point, a)) < -1e-9) return false;
        }
        return true;
    }
    private static boolean inUnion(Point point, List<List<Point>> polygons) {
        for (List<Point> polygon : polygons) if (inside(point, polygon)) return true;
        return false;
    }
    private static List<Edge> edges(List<List<Point>> polygons) {
        List<Edge> result = new ArrayList<>();
        for (List<Point> polygon : polygons)
            for (int i = 0; i < polygon.size(); i++) result.add(new Edge(polygon.get(i), polygon.get((i + 1) % polygon.size())));
        return result;
    }
    private static double clamp(double value) { return Math.min(1, Math.max(0, value)); }
    private static List<Double> intersections(Edge a, Edge b) {
        Point r = minus(a.b, a.a), s = minus(b.b, b.a), ca = minus(b.a, a.a);
        double denominator = cross(r, s);
        if (Math.abs(denominator) > 1e-10) {
            double t = cross(ca, s) / denominator, u = cross(ca, r) / denominator;
            return t >= -EPS && t <= 1 + EPS && u >= -EPS && u <= 1 + EPS ? List.of(clamp(t)) : List.of();
        }
        if (Math.abs(cross(ca, r)) > EPS) return List.of();
        boolean xAxis = Math.abs(r.x) > Math.abs(r.z);
        List<Double> result = new ArrayList<>();
        for (Point p : List.of(b.a, b.b)) {
            double t = xAxis ? (p.x - a.a.x) / r.x : (p.z - a.a.z) / r.z;
            if (t >= -EPS && t <= 1 + EPS) result.add(clamp(t));
        }
        return result;
    }
    private static List<Double> unique(List<Double> numbers, double tolerance) {
        List<Double> sorted = new ArrayList<>(numbers);
        sorted.sort(Double::compare);
        List<Double> result = new ArrayList<>();
        for (double number : sorted)
            if (result.isEmpty() || Math.abs(number - result.get(result.size() - 1)) > tolerance) result.add(number);
        return result;
    }
    private static List<List<Point>> boundary(List<List<Point>> polygons) throws IOException {
        List<Edge> segments = edges(polygons);
        Map<EdgeKey, Edge> visible = new LinkedHashMap<>();
        for (Edge edge : segments) {
            List<Double> parameters = new ArrayList<>(List.of(0.0, 1.0));
            for (Edge other : segments) parameters.addAll(intersections(edge, other));
            parameters = unique(parameters, 1e-9);
            Point r = minus(edge.b, edge.a);
            double length = Math.hypot(r.x, r.z);
            for (int i = 0; i + 1 < parameters.size(); i++) {
                double t = parameters.get(i), u = parameters.get(i + 1);
                if ((u - t) * length < 1e-7) continue;
                Point a = at(edge, t), b = at(edge, u), middle = new Point((a.x + b.x) / 2, (a.z + b.z) / 2);
                Point normal = new Point(r.z / length, -r.x / length);
                Point out = new Point(middle.x + normal.x * 1e-6, middle.z + normal.z * 1e-6);
                Point in = new Point(middle.x - normal.x * 1e-6, middle.z - normal.z * 1e-6);
                if (inUnion(in, polygons) && !inUnion(out, polygons))
                    visible.put(new EdgeKey(key(a), key(b)), new Edge(a, b));
            }
        }
        List<Edge> result = new ArrayList<>(visible.values());
        Map<PointKey, Integer> starts = new LinkedHashMap<>();
        for (int i = 0; i < result.size(); i++)
            if (starts.put(key(result.get(i).a), i) != null) throw new IOException("Branched grindstone boundary");
        List<List<Point>> loops = new ArrayList<>();
        boolean[] used = new boolean[result.size()];
        for (int j = 0; j < result.size(); j++) {
            if (used[j]) continue;
            List<Point> loop = new ArrayList<>();
            int i = j;
            while (!used[i]) {
                used[i] = true;
                Edge edge = result.get(i);
                loop.add(edge.a);
                Integer next = starts.get(key(edge.b));
                if (next == null) throw new IOException("Open grindstone boundary");
                i = next;
            }
            if (i != j) throw new IOException("Intersecting grindstone boundary loops");
            loops.add(loop);
        }
        return loops;
    }
    private static Line line(Point a, Point b) {
        double slope = (b.z - a.z) / (b.x - a.x);
        return new Line(slope, a.z - slope * a.x);
    }
    private static List<Interval> unionIntervals(List<List<Point>> polygons, double x) {
        List<Interval> intervals = new ArrayList<>();
        for (List<Point> polygon : polygons) {
            List<Line> hits = new ArrayList<>();
            for (int i = 0; i < polygon.size(); i++) {
                Point a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
                if (Math.min(a.x, b.x) < x && x < Math.max(a.x, b.x)) hits.add(line(a, b));
            }
            if (!hits.isEmpty()) {
                hits.sort(Comparator.comparingDouble(l -> l.at(x)));
                intervals.add(new Interval(hits.get(0), hits.get(hits.size() - 1)));
            }
        }
        intervals.sort(Comparator.comparingDouble(i -> i.low.at(x)));
        List<Interval> result = new ArrayList<>();
        for (Interval next : intervals) {
            int last = result.size() - 1;
            if (last >= 0 && next.low.at(x) <= result.get(last).high.at(x) + EPS) {
                Interval previous = result.get(last);
                if (next.high.at(x) > previous.high.at(x)) result.set(last, new Interval(previous.low, next.high));
            } else result.add(next);
        }
        return result;
    }
    private static List<Interval> difference(List<Interval> a, List<Interval> b, double x) {
        List<Interval> result = new ArrayList<>();
        for (Interval interval : a) {
            Line current = interval.low;
            for (Interval subtract : b) {
                if (subtract.high.at(x) <= current.at(x) + EPS) continue;
                if (subtract.low.at(x) >= interval.high.at(x) - EPS) break;
                if (subtract.low.at(x) > current.at(x) + EPS) result.add(new Interval(current, subtract.low));
                if (subtract.high.at(x) > current.at(x)) current = subtract.high;
                if (current.at(x) >= interval.high.at(x) - EPS) break;
            }
            if (current.at(x) < interval.high.at(x) - EPS) result.add(new Interval(current, interval.high));
        }
        return result;
    }
    private static List<Double> decompositionEvents(List<List<Point>> polygons) {
        List<Double> result = new ArrayList<>();
        for (List<Point> polygon : polygons) for (Point p : polygon) result.add(p.x);
        List<Edge> segments = edges(polygons);
        for (int i = 0; i < segments.size(); i++)
            for (int j = i + 1; j < segments.size(); j++)
                for (double t : intersections(segments.get(i), segments.get(j))) result.add(at(segments.get(i), t).x);
        return unique(result, 1e-8);
    }
    private static List<List<Point>> cells(List<List<Point>> polygons, List<List<Point>> subtract, List<Double> events) {
        List<Run> runs = new ArrayList<>();
        Map<RunKey, Integer> previous = new LinkedHashMap<>();
        for (int i = 0; i + 1 < events.size(); i++) {
            double left = events.get(i), right = events.get(i + 1), middle = (left + right) / 2;
            List<Interval> intervals = difference(unionIntervals(polygons, middle), unionIntervals(subtract, middle), middle);
            Map<RunKey, Integer> current = new LinkedHashMap<>();
            for (Interval interval : intervals) {
                RunKey key = new RunKey(key(interval.low.slope), key(interval.low.intercept), key(interval.high.slope), key(interval.high.intercept));
                Integer at = previous.get(key);
                if (at != null && Math.abs(runs.get(at).right - left) < EPS) runs.get(at).right = right;
                else {
                    at = runs.size();
                    runs.add(new Run(left, right, interval.low, interval.high));
                }
                current.put(key, at);
            }
            previous = current;
        }
        List<List<Point>> result = new ArrayList<>();
        for (Run run : runs) {
            List<Point> polygon = new ArrayList<>();
            for (Point p : List.of(new Point(run.left, run.low.at(run.left)), new Point(run.right, run.low.at(run.right)),
                    new Point(run.right, run.high.at(run.right)), new Point(run.left, run.high.at(run.left))))
                if (polygon.isEmpty() || distance(p, polygon.get(polygon.size() - 1)) > 1e-7) polygon.add(p);
            if (polygon.size() > 1 && distance(polygon.get(0), polygon.get(polygon.size() - 1)) <= 1e-7)
                polygon.remove(polygon.size() - 1);
            if (polygon.size() >= 3 && Math.abs(signed(polygon)) > 1e-7) result.add(polygon);
        }
        return result;
    }
    private static void rim(List<Face> faces, List<List<Point>> loops, double low, double high, String material, boolean atlas) {
        for (List<Point> loop : loops) {
            double[] lengths = new double[loop.size()];
            double perimeter = 0;
            for (int i = 0; i < loop.size(); i++) {
                lengths[i] = distance(loop.get(i), loop.get((i + 1) % loop.size()));
                perimeter += lengths[i];
            }
            double at = 0;
            for (int i = 0; i < loop.size(); i++) {
                Point a = loop.get(i), b = loop.get((i + 1) % loop.size());
                double[][] p = {{a.x, high, a.z}, {b.x, high, b.z}, {b.x, low, b.z}, {a.x, low, a.z}};
                double[][] uv = {{at / perimeter * 16, (13 - high) * 1.6}, {(at + lengths[i]) / perimeter * 16, (13 - high) * 1.6},
                        {(at + lengths[i]) / perimeter * 16, (13 - low) * 1.6}, {at / perimeter * 16, (13 - low) * 1.6}};
                rounded(p); rounded(uv);
                faces.add(new Face(p, uv, material + "_rim", atlas));
                at += lengths[i];
            }
        }
    }
    private static void cap(List<Face> faces, List<List<Point>> polygons, double y, boolean top, String material, double phase) {
        for (List<Point> polygon : polygons) {
            List<Point> order = new ArrayList<>(polygon);
            if (top) Collections.reverse(order);
            double[][] p = new double[order.size()][3], uv = new double[order.size()][2];
            for (int i = 0; i < order.size(); i++) {
                Point point = order.get(i), texture = material.equals("mithril") ? turn(point, Math.toRadians(-phase), 8, 8) : point;
                p[i] = new double[] {point.x, y, point.z};
                uv[i] = new double[] {texture.x, texture.z};
            }
            rounded(p); rounded(uv);
            faces.add(new Face(p, uv, material + "_cut", false));
        }
    }
    private static void rounded(double[][] values) {
        for (double[] point : values) for (int i = 0; i < point.length; i++) point[i] = round(point[i], 7);
    }
    private static String obj(List<Face> faces, boolean embedded) {
        StringBuilder result = new StringBuilder("mtllib offset_grindstone.mtl\n");
        int index = 1;
        for (Face face : faces) {
            result.append("usemtl ").append(face.material).append('\n');
            for (double[] p : face.points) {
                double x = embedded ? 16 - p[0] : p[0], y = embedded ? 16 - p[2] : p[1], z = embedded ? 16 - p[1] : p[2];
                result.append(String.format(Locale.ROOT, "v %.9f %.9f %.9f\n", x / 16, y / 16, z / 16));
            }
            // OBJ and Forge's flip_v:false both use direct V; do not invert it here.
            for (double[] uv : face.uv) result.append(String.format(Locale.ROOT, "vt %.9f %.9f\n", uv[0] / 16, uv[1] / 16));
            result.append('f');
            for (int i = 0; i < face.points.length; i++) result.append(' ').append(index + i).append('/').append(index + i);
            result.append('\n');
            index += face.points.length;
        }
        return result.toString();
    }
}
