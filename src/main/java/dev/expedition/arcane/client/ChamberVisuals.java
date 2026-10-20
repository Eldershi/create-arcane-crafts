package dev.expedition.arcane.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.expedition.arcane.ArcaneIndustry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exact mesh/UV export of the selected art; all coordinates are Y-up model units. */
final class ChamberVisuals {
    private static final ResourceLocation DATA = ArcaneIndustry.id(
            "models/block/ascension_chamber/runtime_geometry.json");
    private static final ResourceLocation DECORATIONS = ArcaneIndustry.id(
            "models/block/ascension_chamber/runtime_decorations.json");
    private static final ResourceLocation RESIDUAL_FIRE = ArcaneIndustry.id(
            "models/block/ascension_chamber/runtime_residual_fire.json");
    private final Definition definition;
    private final DecorationDefinition decorations;
    private final ResidualFireDefinition residualFire;
    private final Map<String, RenderType> flames;
    private final Map<String, RenderType> runes;

    ChamberVisuals() {
        // The dispatcher reconstructs its renderers on resource reload. Read the
        // current resource manager here, rather than keeping a static stale mesh.
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(DATA)) {
            definition = new Gson().fromJson(reader, Definition.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load selected ascension chamber geometry", exception);
        }
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(DECORATIONS)) {
            decorations = new Gson().fromJson(reader, DecorationDefinition.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load native ascension chamber decorations", exception);
        }
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(RESIDUAL_FIRE)) {
            residualFire = new Gson().fromJson(reader, ResidualFireDefinition.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load native empty chamber fire", exception);
        }
        for (Map<String, MeshFace[]> accessory : List.of(decorations.goggles, decorations.crown)) {
            accessory.values().forEach(faces -> {
                for (MeshFace face : faces) face.initialize();
            });
        }
        definition.heads.forEach((state, faces) -> {
            String openHead = ArcaneIndustry.id("textures/entity/chamber/arcane_blaze_" + state + ".png").toString();
            RenderType closedHead = state.equals("idle") ? null
                    : cutout(ArcaneIndustry.id("textures/entity/chamber/arcane_blaze_" + state + "_closed.png").toString());
            for (MeshFace face : faces) {
                face.initialize();
                // Keep the approved brow mesh/material while only closing the eyes.
                if (face.texture.equals(openHead)) face.closedType = closedHead;
            }
        });
        definition.rods.values().forEach(groups -> groups.values().forEach(faces -> {
            for (MeshFace face : faces) face.initialize();
        }));
        flames = Map.of("active", cutout(definition.fire_textures.get("active")),
                "super", cutout(definition.fire_textures.get("super")));
        // Depth offset is a decal render state, not thicker/offset rune geometry.
        // It prevents coplanar flame pixels from erasing the accepted glyphs.
        runes = Map.of("active", RenderType.entityCutoutNoCullZOffset(
                        new ResourceLocation(definition.rune_textures.get("active"))),
                "super", RenderType.entityCutoutNoCullZOffset(
                        new ResourceLocation(definition.rune_textures.get("super"))));
    }

    private static RenderType cutout(String texture) {
        return RenderType.entityCutoutNoCull(new ResourceLocation(texture));
    }

    void head(String state, boolean closedEyes, double y, PoseStack pose, MultiBufferSource buffers, int overlay) {
        for (MeshFace face : definition.heads.get(state)) {
            RenderType type = closedEyes && face.closedType != null ? face.closedType : face.type;
            emit(pose, buffers.getBuffer(type), face.points, face.uv,
                    y * 16, face.normal, overlay);
        }
    }

    void decorations(String state, boolean goggles, boolean crown, double y, PoseStack pose,
                     MultiBufferSource buffers, int overlay) {
        // Native goggles sit 0.05 model units ahead of the head. The approved
        // brows protrude 0.20 units, so depth places both open/closed brows first.
        // Accessories share the head's bob/yaw, inside the outer flame/rune shell.
        String size = state.equals("idle") ? "idle" : "active";
        if (goggles) draw(decorations.goggles.get(size), y, pose, buffers, overlay);
        if (crown) draw(decorations.crown.get(size), y, pose, buffers, overlay);
    }

    void rods(String state, String group, double y, PoseStack pose,
              MultiBufferSource buffers, int overlay) {
        draw(definition.rods.get(state).get(group), y, pose, buffers, overlay);
    }

    void residualFire(PoseStack pose, MultiBufferSource buffers, int overlay) {
        // Use the block atlas sprite so vanilla/resource-pack campfire animation
        // advances normally. This is Create's empty burner's four fire quads,
        // without its original cage, living blaze flame scroll or rune surface.
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(new ResourceLocation(residualFire.sprite));
        VertexConsumer buffer = buffers.getBuffer(RenderType.cutoutMipped());
        for (Surface face : residualFire.faces) {
            double[][] uv = new double[face.uv.length][2];
            for (int i = 0; i < uv.length; i++) {
                uv[i][0] = sprite.getU(face.uv[i][0] * 16);
                uv[i][1] = sprite.getV(face.uv[i][1] * 16);
            }
            emit(pose, buffer, face.points, uv, 0, normal(face.points), overlay);
        }
    }

    private void draw(MeshFace[] faces, double y, PoseStack pose,
                      MultiBufferSource buffers, int overlay) {
        for (MeshFace face : faces) {
            emit(pose, buffers.getBuffer(face.type), face.points, face.uv,
                    y * 16, face.normal, overlay);
        }
    }

    void flame(String state, double time, PoseStack pose, MultiBufferSource buffers, int overlay) {
        double speed = state.equals("super") ? .09375 : .078125;
        double u = 16 * fraction(speed * time / 2), v = 16 * fraction(speed * time);
        VertexConsumer buffer = buffers.getBuffer(flames.get(state));
        for (Surface surface : definition.surfaces) {
            double[][] uv = new double[surface.uv.length][2];
            for (int i = 0; i < uv.length; i++) {
                uv[i][0] = (surface.uv[i][0] + u) / 32;
                uv[i][1] = (surface.uv[i][1] + v) / 32;
            }
            emit(pose, buffer, surface.points, uv, 0, normal(surface.points), overlay);
        }
    }

    void runes(String state, double time, PoseStack pose, MultiBufferSource buffers, int overlay) {
        VertexConsumer buffer = buffers.getBuffer(runes.get(state));
        int[] cycles = definition.cycles.get(state);
        for (Lane lane : definition.lanes) {
            int count = cycles[lane.index];
            double local = time / definition.loop_ticks * count + lane.initial_phase;
            long cycle = (long) Math.floor(local);
            double phase = fraction(local);
            Birth birth = lane.births[Math.floorMod(cycle, count)];
            double v = 16 + birth.height / 2 + birth.lower_pad - definition.uv_span * phase;
            double u = birth.u + birth.drift * Math.sin(Math.PI * 2 * phase + birth.drift_phase);
            double[] rect = {u - birth.width / 2, v - birth.height / 2,
                    u + birth.width / 2, v + birth.height / 2};
            if (rect[3] <= 0 || rect[1] >= 16) continue;
            int variant = birth.variant;
            // Exact 9x13 content in the selected 176x45 atlas (one texel gutter).
            double[] atlas = {(birth.glyph * 11 + 1) / 176.0, (variant * 15 + 1) / 45.0,
                    (birth.glyph * 11 + 10) / 176.0, (variant * 15 + 14) / 45.0};
            for (int facet = 0; facet < 3; facet++) {
                Surface surface = definition.surfaces[lane.side * 3 + facet];
                for (int triangle = 1; triangle < 3; triangle++) {
                    double[][] original = {surface.uv[0], surface.uv[triangle], surface.uv[triangle + 1]};
                    List<double[]> clipped = new ArrayList<>(List.of(
                            original[0].clone(), original[1].clone(), original[2].clone()));
                    clipped = clip(clipped, 0, rect[0], true);
                    clipped = clip(clipped, 0, rect[2], false);
                    clipped = clip(clipped, 1, rect[1], true);
                    clipped = clip(clipped, 1, rect[3], false);
                    if (clipped.size() < 3) continue;
                    double[][] points = new double[clipped.size()][3];
                    double[][] uv = new double[clipped.size()][2];
                    int[] indices = {0, triangle, triangle + 1};
                    for (int i = 0; i < clipped.size(); i++) {
                        double[] point = clipped.get(i);
                        double[] weights = barycentric(point, original);
                        for (int j = 0; j < 3; j++) for (int axis = 0; axis < 3; axis++) {
                            points[i][axis] += weights[j] * surface.points[indices[j]][axis];
                        }
                        uv[i][0] = atlas[0] + (point[0] - rect[0]) / (rect[2] - rect[0]) * (atlas[2] - atlas[0]);
                        uv[i][1] = atlas[1] + (point[1] - rect[1]) / (rect[3] - rect[1]) * (atlas[3] - atlas[1]);
                    }
                    emit(pose, buffer, points, uv, 0, normal(surface.points), overlay);
                }
            }
        }
    }


    /** Accepted manual-processing shell: native UV motion, smooth upper bend and a .42-block alpha tail. */
    void manualEffects(String state, double time, double top, double opacity, PoseStack pose,
                       MultiBufferSource buffers, int overlay) {
        if (opacity <= .0001) return;
        top = Math.max(.251, Math.min(1, top));
        double speed = state.equals("super") ? .09375 : .078125;
        double scrollU = 16 * fraction(speed * time / 2), scrollV = 16 * fraction(speed * time);
        VertexConsumer fire = buffers.getBuffer(RenderType.entityTranslucentEmissive(
                new ResourceLocation(definition.fire_textures.get(state))));
        for (int i = 0; i < definition.surfaces.length; i++) {
            // Remove the old hard folded lip. Eight continuous strips bend each upper wall.
            if (i % 3 == 0) continue;
            Surface surface = definition.surfaces[i];
            int segments = i % 3 == 1 ? 8 : 1;
            for (int segment = 0; segment < segments; segment++) {
                double lo = segment / (double) segments, hi = (segment + 1.0) / segments;
                double[][] points = {lerp(surface.points[0], surface.points[1], lo),
                        lerp(surface.points[0], surface.points[1], hi),
                        lerp(surface.points[3], surface.points[2], hi),
                        lerp(surface.points[3], surface.points[2], lo)};
                double[][] uv = {lerp(surface.uv[0], surface.uv[1], lo),
                        lerp(surface.uv[0], surface.uv[1], hi),
                        lerp(surface.uv[3], surface.uv[2], hi),
                        lerp(surface.uv[3], surface.uv[2], lo)};
                for (int k = 0; k < 4; k++) {
                    points[k] = manualPoint(points[k], top);
                    uv[k] = new double[]{(uv[k][0] + scrollU) / 32, (uv[k][1] + scrollV) / 32};
                }
                emitManual(pose, fire, points, uv, top, opacity * .72, overlay);
            }
        }
        VertexConsumer glyphs = buffers.getBuffer(RenderType.entityTranslucentEmissive(
                new ResourceLocation(definition.rune_textures.get(state))));
        int[] cycles = definition.cycles.get(state);
        for (Lane lane : definition.lanes) {
            int count = cycles[lane.index];
            double local = time / definition.loop_ticks * count + lane.initial_phase;
            double phase = fraction(local);
            Birth birth = lane.births[Math.floorMod((long) Math.floor(local), count)];
            double v = 16 + birth.height / 2 + birth.lower_pad - definition.uv_span * phase;
            if (v < 0 || v > 16) continue;
            double u = birth.u + birth.drift * Math.sin(Math.PI * 2 * phase + birth.drift_phase);
            double[] center = manualSurfacePoint(lane.side, u, v);
            double[] along = manualSurfacePoint(lane.side, u + .1, v);
            if (center == null || along == null) continue;
            double tx = along[0] - center[0], tz = along[2] - center[2];
            double length = Math.hypot(tx, tz);
            if (length < 1e-9) continue;
            tx /= length; tz /= length;
            double cy = manualHeight(center[1] / 16, top), cx = center[0] / 16 - .5, cz = center[2] / 16 - .5;
            double width = birth.width / 16 * .75, height = width * 13 / 9;
            double u0 = (birth.glyph * 11 + 1) / 176.0, u1 = (birth.glyph * 11 + 10) / 176.0;
            double v0 = (birth.variant * 15 + 1) / 45.0, v1 = (birth.variant * 15 + 14) / 45.0;
            int[][] corners = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};
            double[][] points = new double[4][3], uv = new double[4][2];
            for (int k = 0; k < 4; k++) {
                double y = Math.max(.25, Math.min(top, cy + corners[k][1] * height / 2));
                double x = cx + tx * corners[k][0] * width / 2, z = cz + tz * corners[k][0] * width / 2;
                double radius = Math.max(1e-6, Math.hypot(x, z)), scale = manualBend(y, top);
                points[k] = new double[]{(.5 + x * scale + x / radius * .004) * 16, y * 16,
                        (.5 + z * scale + z / radius * .004) * 16};
                uv[k] = new double[]{corners[k][0] < 0 ? u0 : u1, v0 + (cy + height / 2 - y) / height * (v1 - v0)};
            }
            emitManual(pose, glyphs, points, uv, top, opacity * .96, overlay);
        }
    }

    private double[] manualSurfacePoint(int side, double u, double v) {
        double best = Double.POSITIVE_INFINITY;
        double[] nearest = null;
        for (int facet = 1; facet <= 2; facet++) {
            Surface face = definition.surfaces[side * 3 + facet];
            for (int triangle = 1; triangle <= 2; triangle++) {
                int[] indices = {0, triangle, triangle + 1};
                double[] weights = barycentric(new double[]{u, v},
                        new double[][]{face.uv[0], face.uv[triangle], face.uv[triangle + 1]});
                double penalty = 0;
                for (double weight : weights) penalty += Math.max(0, -weight);
                if (penalty >= best) continue;
                best = penalty; nearest = new double[3];
                for (int k = 0; k < 3; k++) for (int axis = 0; axis < 3; axis++)
                    nearest[axis] += weights[k] * face.points[indices[k]][axis];
            }
        }
        return nearest;
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        double[] result = new double[a.length];
        for (int i = 0; i < result.length; i++) result[i] = a[i] + (b[i] - a[i]) * t;
        return result;
    }
    private static double manualHeight(double y, double top) {
        double lower = Math.min(.125, (top - .25) / 3), lowerEnd = .25 + lower;
        return y <= .375 ? .25 + (y - .25) / .125 * lower
                : lowerEnd + (Math.max(.375, Math.min(.9375, y)) - .375) / .5625 * (top - lowerEnd);
    }
    private static double manualBend(double y, double top) {
        double upper = Math.max(0, Math.min(1, ((y - .25) / (top - .25) - .5) / .5));
        return 1 + .12 * upper * upper / Math.hypot(.3125, .3125);
    }
    private static double[] manualPoint(double[] point, double top) {
        double y = manualHeight(point[1] / 16, top), scale = manualBend(y, top);
        return new double[]{8 + (point[0] - 8) * scale, y * 16, 8 + (point[2] - 8) * scale};
    }
    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t);
    }
    private static void emitManual(PoseStack pose, VertexConsumer buffer, double[][] points, double[][] uv,
                                   double top, double opacity, int overlay) {
        Vector3f normal = normal(points);
        if (!Float.isFinite(normal.x()) || !Float.isFinite(normal.y()) || !Float.isFinite(normal.z())) return;
        for (int i = 0; i < 4; i++) {
            double distance = Math.max(0, Math.min(1, (top - points[i][1] / 16) / Math.min(.42, top - .25)));
            int alpha = (int) (255 * opacity * smooth(distance) * (.45 + .55 * smooth(distance / .62)));
            buffer.vertex(pose.last().pose(), (float) points[i][0] / 16, (float) points[i][1] / 16,
                            (float) points[i][2] / 16).color(255, 255, 255, alpha)
                    .uv((float) uv[i][0], (float) uv[i][1]).overlayCoords(overlay).uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.last().normal(), normal.x(), normal.y(), normal.z()).endVertex();
        }
    }

    private static double fraction(double value) { return value - Math.floor(value); }

    private static double[] barycentric(double[] p, double[][] t) {
        double ax = t[1][0] - t[0][0], ay = t[1][1] - t[0][1];
        double bx = t[2][0] - t[0][0], by = t[2][1] - t[0][1];
        double px = p[0] - t[0][0], py = p[1] - t[0][1];
        double determinant = ax * by - bx * ay;
        double b = (px * by - bx * py) / determinant;
        double c = (ax * py - px * ay) / determinant;
        return new double[]{1 - b - c, b, c};
    }

    private static List<double[]> clip(List<double[]> polygon, int axis, double bound, boolean greater) {
        List<double[]> result = new ArrayList<>();
        if (polygon.isEmpty()) return result;
        double[] previous = polygon.get(polygon.size() - 1);
        boolean wasInside = greater ? previous[axis] >= bound - 1e-9 : previous[axis] <= bound + 1e-9;
        for (double[] current : polygon) {
            boolean inside = greater ? current[axis] >= bound - 1e-9 : current[axis] <= bound + 1e-9;
            if (inside != wasInside) {
                double factor = (bound - previous[axis]) / (current[axis] - previous[axis]);
                double[] crossing = {previous[0] + factor * (current[0] - previous[0]),
                        previous[1] + factor * (current[1] - previous[1])};
                crossing[axis] = bound;
                result.add(crossing);
            }
            if (inside) result.add(current);
            previous = current;
            wasInside = inside;
        }
        return result;
    }

    private static Vector3f normal(double[][] p) {
        return new Vector3f((float) (p[1][0] - p[0][0]), (float) (p[1][1] - p[0][1]),
                (float) (p[1][2] - p[0][2])).cross(new Vector3f((float) (p[2][0] - p[0][0]),
                (float) (p[2][1] - p[0][1]), (float) (p[2][2] - p[0][2]))).normalize();
    }

    private static void emit(PoseStack pose, VertexConsumer buffer, double[][] points, double[][] uv,
                             double y, Vector3f normal, int overlay) {
        if (points.length == 4) {
            for (int i = 0; i < 4; i++) vertex(pose, buffer, points[i], uv[i], y, normal, overlay);
        } else {
            // QUADS buffer: repeat the final triangle corner for a degenerate fourth edge.
            for (int k = 1; k + 1 < points.length; k++) {
                for (int index : new int[]{0, k, k + 1, k + 1}) {
                    vertex(pose, buffer, points[index], uv[index], y, normal, overlay);
                }
            }
        }
    }

    private static void vertex(PoseStack pose, VertexConsumer buffer, double[] point, double[] uv,
                               double y, Vector3f normal, int overlay) {
        buffer.vertex(pose.last().pose(), (float) point[0] / 16, (float) (point[1] + y) / 16,
                        (float) point[2] / 16).color(255, 255, 255, 255)
                .uv((float) uv[0], (float) uv[1]).overlayCoords(overlay).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.last().normal(), normal.x(), normal.y(), normal.z()).endVertex();
    }

    private static final class Definition {
        Map<String, MeshFace[]> heads;
        Map<String, Map<String, MeshFace[]>> rods;
        Surface[] surfaces;
        Map<String, String> fire_textures, rune_textures;
        Lane[] lanes;
        Map<String, int[]> cycles;
        double loop_ticks, uv_span;
    }
    private static final class DecorationDefinition {
        Map<String, MeshFace[]> goggles, crown;
    }
    private static final class ResidualFireDefinition {
        String sprite;
        Surface[] faces;
    }
    private static class Surface { double[][] points, uv; }
    private static final class MeshFace extends Surface {
        String texture;
        transient RenderType type;
        transient RenderType closedType;
        transient Vector3f normal;
        void initialize() { type = cutout(texture); normal = normal(points); }
    }
    private static final class Lane {
        int index, side;
        double initial_phase;
        Birth[] births;
    }
    private static final class Birth {
        int glyph, variant;
        double height, width, u, lower_pad, drift, drift_phase;
    }
}
