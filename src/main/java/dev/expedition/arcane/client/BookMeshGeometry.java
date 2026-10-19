package dev.expedition.arcane.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Articulates a dependency's model at runtime; contains no dependency model or UV data. */
final class BookMeshGeometry {
    private static final int[] TRIANGLES = {0, 1, 2, 0, 2, 3};
    private static final double COVER_THICKNESS = .30, SPINE_THICKNESS = .25;
    private static final double DECOR_GAP = .025, DECOR_RELIEF = .055;

    private BookMeshGeometry() {}

    static Definition build(String id, String texture, JsonArray elements, List<Template> templates) {
        List<Element> source = new ArrayList<>();
        elements.forEach(value -> source.add(new Element(value.getAsJsonObject())));
        // The enclosed paper is shorter along the book's vertical axis than its covers.
        Element paper = source.stream().min(Comparator.comparingDouble(e -> e.size(2))).orElseThrow();
        double center = (paper.from[0] + paper.to[0]) / 2;
        Element coverA = source.stream().filter(e -> e.from[0] >= center && e.size(1) > paper.size(1))
                .min(Comparator.comparingDouble(e -> e.size(0))).orElseThrow();
        Element coverB = source.stream().filter(e -> e.to[0] <= center && e.size(1) > paper.size(1))
                .min(Comparator.comparingDouble(e -> e.size(0))).orElseThrow();
        Element spine = source.stream().filter(e -> e != paper && e != coverA && e != coverB)
                .min(Comparator.comparingDouble(e -> e.size(1))).orElseThrow();
        List<Element> decoration = source.stream().filter(e -> e != paper && e != coverA && e != coverB && e != spine).toList();
        double thickness = paper.size(0) / 2;
        Definition book = new Definition(id, texture, (float) thickness, !decoration.isEmpty());
        double spineWidth = thickness + COVER_THICKNESS + (book.decor ? DECOR_GAP + DECOR_RELIEF : 0);
        List<Part> parts = new ArrayList<>();
        for (Template template : templates) {
            Part part = new Part(template.name);
            Builder mesh = new Builder();
            for (Face face : template.faces) {
                for (int index : TRIANGLES) {
                    double[] local = face.points[index].clone();
                    double tx = fraction(local, template.from, template.to, 0);
                    double ty = fraction(local, template.from, template.to, 1);
                    double tz = fraction(local, template.from, template.to, 2);
                    Element sourceElement;
                    String sourceFace;
                    double[] point;
                    if (template.name.equals("left_lid")) {
                        sourceElement = coverA;
                        sourceFace = lidFace(face.direction, true);
                        point = coverA.point(tz, tx, ty);
                    } else if (template.name.equals("right_lid")) {
                        sourceElement = coverB;
                        sourceFace = lidFace(face.direction, false);
                        point = coverB.point(1 - tz, 1 - tx, ty);
                    } else if (template.name.equals("seam")) {
                        sourceElement = spine;
                        sourceFace = seamFace(face.direction);
                        point = spine.point(tx, 1 - tz, ty);
                    } else {
                        sourceElement = paper;
                        sourceFace = lidFace(face.direction, false);
                        point = paper.point(1 - tz, 1 - tx, ty);
                    }
                    double[] uv = sourceElement.uv(sourceFace, point);
                    if (template.name.endsWith("_lid")) local[2] = tz * COVER_THICKNESS;
                    else if (template.name.equals("seam")) {
                        local[0] *= spineWidth;
                        local[2] = -SPINE_THICKNESS + tz * SPINE_THICKNESS;
                    } else {
                        local[0] *= 5.5 / 5;
                        local[1] *= 9.0 / 8;
                        local[2] = switch (template.name) {
                            case "left_pages" -> lerp(-thickness + .045 * thickness, -.025 * thickness, tz);
                            case "right_pages" -> lerp(.025 * thickness, thickness - .045 * thickness, tz);
                            default -> (tz - .5) * .012 * thickness;
                        };
                    }
                    mesh.vertex(local, uv, face.normal);
                }
            }
            part.meshes.add(mesh.finish(false));
            parts.add(part);
        }
        double sourceCenter = (coverA.to[0] + coverB.from[0]) / 2;
        for (Element decor : decoration) {
            for (boolean high : new boolean[]{true, false}) {
                Part part = parts.stream().filter(p -> p.name.equals(high ? "left_lid" : "right_lid")).findFirst().orElseThrow();
                Element cover = high ? coverA : coverB;
                Builder mesh = new Builder();
                for (String direction : decor.faces.keySet()) {
                    List<double[]> polygon = clip(new ArrayList<>(List.of(decor.face(direction))), 0, sourceCenter, high);
                    polygon = clip(polygon, 1, cover.to[1], false);
                    for (int i = 1; i < polygon.size() - 1; i++) {
                        for (double[] point : new double[][]{polygon.get(0), polygon.get(i), polygon.get(i + 1)}) {
                            double x = (point[1] - cover.to[1]) * (6 / cover.size(1)) * (high ? 1 : -1);
                            double y = point[2] - (cover.from[2] + cover.to[2]) / 2;
                            double shell = high ? (point[0] - sourceCenter) / (decor.to[0] - sourceCenter)
                                    : (sourceCenter - point[0]) / (sourceCenter - decor.from[0]);
                            double z = COVER_THICKNESS + DECOR_GAP + clamp(shell) * DECOR_RELIEF;
                            mesh.vertex(new double[]{x, y, z}, decor.uv(direction, point), null);
                        }
                    }
                }
                part.meshes.add(mesh.finish(true));
            }
        }
        book.parts = parts.toArray(Part[]::new);
        return book;
    }

    private static String lidFace(String face, boolean left) {
        return switch (face) {
            case "north" -> left ? "west" : "east";
            case "south" -> left ? "east" : "west";
            case "east" -> left ? "up" : "down";
            case "west" -> left ? "down" : "up";
            case "up" -> "south";
            case "down" -> "north";
            default -> throw new IllegalArgumentException("Unknown face " + face);
        };
    }

    private static String seamFace(String face) {
        return switch (face) {
            case "north" -> "up";
            case "south" -> "down";
            case "up" -> "south";
            case "down" -> "north";
            default -> face;
        };
    }

    private static List<double[]> clip(List<double[]> polygon, int axis, double split, boolean high) {
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            boolean inA = high ? a[axis] >= split : a[axis] <= split;
            boolean inB = high ? b[axis] >= split : b[axis] <= split;
            if (inA) result.add(a);
            if (inA != inB) {
                double t = (split - a[axis]) / (b[axis] - a[axis]);
                result.add(new double[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)});
            }
        }
        return result;
    }

    private static double fraction(double[] point, double[] from, double[] to, int axis) {
        return clamp((point[axis] - from[axis]) / (to[axis] - from[axis]));
    }

    private static double clamp(double n) { return Math.max(0, Math.min(1, n)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static final class Template {
        final String name;
        final double[] from, to;
        final List<Face> faces = new ArrayList<>();
        Template(String name, double[] from, double[] to) { this.name = name; this.from = from; this.to = to; }
    }
    static final class Face {
        final String direction;
        final double[][] points;
        final double[] normal;
        Face(String direction, double[][] points, double[] normal) { this.direction = direction; this.points = points; this.normal = normal; }
    }
    static final class Definition {
        final String id, texture;
        final float thickness;
        final boolean decor;
        Part[] parts;
        Definition(String id, String texture, float thickness, boolean decor) { this.id = id; this.texture = texture; this.thickness = thickness; this.decor = decor; }
    }
    static final class Part {
        final String name;
        final List<Mesh> meshes = new ArrayList<>();
        Part(String name) { this.name = name; }
    }
    static final class Mesh {
        final float[] p, uv, n;
        Mesh(float[] p, float[] uv, float[] n) { this.p = p; this.uv = uv; this.n = n; }
    }
    private static final class Builder {
        final List<Float> positions = new ArrayList<>(), uvs = new ArrayList<>(), normals = new ArrayList<>();
        void vertex(double[] p, double[] uv, double[] n) {
            for (double value : p) positions.add((float) (value / 16));
            for (double value : uv) uvs.add((float) value);
            if (n != null) for (double value : n) normals.add((float) value);
        }
        Mesh finish(boolean computeNormals) {
            float[] p = array(positions), uv = array(uvs), n = computeNormals ? new float[p.length] : array(normals);
            if (computeNormals) {
                // BufferGeometry's flat triangle normals use positions already rounded to float.
                for (int i = 0; i < p.length; i += 9) {
                    double ax = p[i + 6] - p[i + 3], ay = p[i + 7] - p[i + 4], az = p[i + 8] - p[i + 5];
                    double bx = p[i] - p[i + 3], by = p[i + 1] - p[i + 4], bz = p[i + 2] - p[i + 5];
                    double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                    // Match the intermediate Float32 normal attribute before normalization.
                    nx = (float) nx; ny = (float) ny; nz = (float) nz;
                    double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (length == 0) length = 1;
                    for (int j = 0; j < 9; j += 3) { n[i + j] = (float) (nx / length); n[i + j + 1] = (float) (ny / length); n[i + j + 2] = (float) (nz / length); }
                }
            }
            for (int i = 0; i < n.length; i++) if (n[i] == 0) n[i] = 0;
            return new Mesh(p, uv, n);
        }
        private static float[] array(List<Float> values) {
            float[] result = new float[values.size()];
            for (int i = 0; i < result.length; i++) {
                float value = values.get(i);
                result[i] = value == 0 ? 0 : value;
            }
            return result;
        }
    }
    private static final class Element {
        final double[] from, to;
        final JsonObject faces;
        Element(JsonObject source) {
            if (source.has("rotation")) throw new IllegalArgumentException("Rotated spell book elements need an adapter");
            from = vector(source.getAsJsonArray("from")); to = vector(source.getAsJsonArray("to"));
            faces = source.getAsJsonObject("faces");
        }
        double size(int axis) { return to[axis] - from[axis]; }
        double[] point(double x, double y, double z) { return new double[]{lerp(from[0], to[0], x), lerp(from[1], to[1], y), lerp(from[2], to[2], z)}; }
        double[][] face(String direction) {
            double x = from[0], y = from[1], z = from[2], X = to[0], Y = to[1], Z = to[2];
            return switch (direction) {
                case "down" -> new double[][]{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}};
                case "up" -> new double[][]{{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}};
                case "north" -> new double[][]{{X,Y,z},{X,y,z},{x,y,z},{x,Y,z}};
                case "south" -> new double[][]{{x,Y,Z},{x,y,Z},{X,y,Z},{X,Y,Z}};
                case "west" -> new double[][]{{x,Y,z},{x,y,z},{x,y,Z},{x,Y,Z}};
                case "east" -> new double[][]{{X,Y,Z},{X,y,Z},{X,y,z},{X,Y,z}};
                default -> throw new IllegalArgumentException("Unknown face " + direction);
            };
        }
        double[] uv(String direction, double[] point) {
            JsonObject spec = faces.getAsJsonObject(direction);
            double[] rect = vector(spec.getAsJsonArray("uv"));
            double[][] base = {{rect[0], rect[1]}, {rect[0], rect[3]}, {rect[2], rect[3]}, {rect[2], rect[1]}};
            int shift = spec.has("rotation") ? Math.floorMod(spec.get("rotation").getAsInt() / 90, 4) : 0;
            double[][] corners = face(direction);
            double t = along(point, corners[0], corners[1]), s = along(point, corners[0], corners[3]);
            double[] a = base[shift], b = base[(1 + shift) % 4], d = base[(3 + shift) % 4];
            return new double[]{(a[0] + t * (b[0] - a[0]) + s * (d[0] - a[0])) / 16,
                    (a[1] + t * (b[1] - a[1]) + s * (d[1] - a[1])) / 16};
        }
        private static double along(double[] point, double[] start, double[] end) {
            for (int i = 0; i < 3; i++) if (Math.abs(end[i] - start[i]) > 1e-8) return (point[i] - start[i]) / (end[i] - start[i]);
            return 0;
        }
        private static double[] vector(JsonArray array) {
            double[] result = new double[array.size()];
            for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsDouble();
            return result;
        }
    }
}
