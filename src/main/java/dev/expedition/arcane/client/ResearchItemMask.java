package dev.expedition.arcane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Clips native item quads along a local diagonal, retaining their texture, tint and glint.
 * This is a geometry wipe, so it needs no global shader, stencil buffer or item replacements.
 */
final class ResearchItemMask implements MultiBufferSource {
    private final MultiBufferSource target;
    private final Matrix4f inverse;
    private final double threshold;
    private final boolean below;
    private final Map<RenderType, VertexConsumer> consumers = new IdentityHashMap<>();

    ResearchItemMask(MultiBufferSource target, PoseStack pose, double fraction, boolean below) {
        this.target = target;
        inverse = new Matrix4f(pose.last().pose()).invert();
        threshold = -1.1 + 2.2 * Math.max(0, Math.min(1, fraction));
        this.below = below;
    }

    @Override public VertexConsumer getBuffer(RenderType type) {
        return consumers.computeIfAbsent(type, key -> new Clipped(target.getBuffer(key)));
    }

    private final class Clipped implements VertexConsumer {
        private final VertexConsumer output;
        private final List<Point> quad = new ArrayList<>(4);
        private Point point = new Point();
        private int[] defaultColor;

        Clipped(VertexConsumer output) { this.output = output; }
        @Override public VertexConsumer vertex(double x, double y, double z) {
            point.x = x; point.y = y; point.z = z;
            Vector3f local = inverse.transformPosition(new Vector3f((float) x, (float) y, (float) z));
            point.distance = (local.x + .55 * local.y - threshold) * (below ? -1 : 1);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            point.r = r; point.g = g; point.b = b; point.a = a; return this;
        }
        @Override public VertexConsumer uv(float u, float v) { point.u = u; point.v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { point.ou = u; point.ov = v; return this; }
        @Override public VertexConsumer uv2(int u, int v) { point.lu = u; point.lv = v; return this; }
        @Override public VertexConsumer normal(float x, float y, float z) {
            point.nx = x; point.ny = y; point.nz = z; return this;
        }
        @Override public void defaultColor(int r, int g, int b, int a) {
            defaultColor = new int[]{r, g, b, a};
            color(r, g, b, a);
        }
        @Override public void unsetDefaultColor() { defaultColor = null; }
        @Override public void endVertex() {
            quad.add(point);
            point = new Point();
            if (defaultColor != null) color(defaultColor[0], defaultColor[1], defaultColor[2], defaultColor[3]);
            if (quad.size() != 4) return;
            List<Point> polygon = new ArrayList<>(6);
            Point previous = quad.get(3);
            for (Point next : quad) {
                if ((previous.distance >= 0) != (next.distance >= 0)) {
                    polygon.add(previous.lerp(next, previous.distance / (previous.distance - next.distance)));
                }
                if (next.distance >= 0) polygon.add(next);
                previous = next;
            }
            if (polygon.size() == 4) polygon.forEach(this::emit);
            else for (int i = 1; i + 1 < polygon.size(); i++) {
                // A repeated last vertex makes a triangle in a QUADS buffer.
                emit(polygon.get(0)); emit(polygon.get(i));
                emit(polygon.get(i + 1)); emit(polygon.get(i + 1));
            }
            quad.clear();
        }
        private void emit(Point p) {
            output.vertex(p.x, p.y, p.z).color(p.r, p.g, p.b, p.a).uv(p.u, p.v)
                    .overlayCoords(p.ou, p.ov).uv2(p.lu, p.lv).normal(p.nx, p.ny, p.nz).endVertex();
        }
    }

    private static final class Point {
        double x, y, z, distance;
        int r = 255, g = 255, b = 255, a = 255, ou, ov = 10, lu = 240, lv = 240;
        float u, v, nx, ny = 1, nz;
        Point lerp(Point p, double t) {
            Point q = new Point();
            q.x = mix(x, p.x, t); q.y = mix(y, p.y, t); q.z = mix(z, p.z, t);
            q.r = (int) mix(r, p.r, t); q.g = (int) mix(g, p.g, t);
            q.b = (int) mix(b, p.b, t); q.a = (int) mix(a, p.a, t);
            q.u = (float) mix(u, p.u, t); q.v = (float) mix(v, p.v, t);
            q.ou = (int) mix(ou, p.ou, t); q.ov = (int) mix(ov, p.ov, t);
            q.lu = (int) mix(lu, p.lu, t); q.lv = (int) mix(lv, p.lv, t);
            q.nx = (float) mix(nx, p.nx, t); q.ny = (float) mix(ny, p.ny, t);
            q.nz = (float) mix(nz, p.nz, t);
            return q;
        }
        private static double mix(double a, double b, double t) { return a + (b - a) * t; }
    }
}
