package dev.expedition.arcane.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.AscensionChamberBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.io.IOException;

/**
 * Accepted scroll-reveal geometry, using the installed items' baked sprites.
 * The caller supplies a centred (x/z=.5) pose rotated by EFFECT_RIG_YAW and a
 * rest quaternion inverse(rig) * camera * Y(PI). This helper draws ALL three
 * items, so the caller must not draw a duplicate native item during this path.
 *
 * The dispatcher recreates this instance on resource reload. No copied native
 * images, DynamicTextures, global framebuffer replacement or render-state
 * mutation are used. Layouts and opaque texel coverage are cached; animation
 * only evaluates bounded numeric envelopes and submits quads.
 */
final class ChamberScrollRevealRenderer {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final ResourceLocation ATLAS = TextureAtlas.LOCATION_BLOCKS;
    private static final double TAU = Math.PI * 2;
    private static final int GRID = 16;
    private static final Completion[] COMPLETION = completionRows();
    private static final Rune[] RUNES = runeRows();
    private static final boolean[][][] GLYPHS = glyphs();
    private final Map<AscensionChamberBlockEntity, Transaction> transactions = new WeakHashMap<>();
    private final LinkedHashMap<ItemKey, ItemGeometry> geometries = new LinkedHashMap<>(32, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ItemKey, ItemGeometry> entry) { return size() > 48; }
    };
    private final Vector3f scratch = new Vector3f();
    private final Vertex[] clipA = new Vertex[12];
    private final List<ParticlePixel>[][] particlePixels = loadParticlePixels();

    void render(AscensionChamberBlockEntity chamber, double logicalTick, double wallSeconds,
                PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                ItemStack primary, ItemStack secondary, ItemStack output, boolean crimson,
                int rarityOrdinal, long seed, Quaternionf restQuaternion) {
        if (primary.isEmpty() || output.isEmpty()) return;
        double candidate = Math.max(0, Math.min(132, Double.isFinite(logicalTick) ? logicalTick : 0));
        Transaction transaction = transactions.get(chamber);
        if (transaction == null || transaction.seed != seed || !transaction.primary.matches(primary)
                || !transaction.secondary.matches(secondary) || !transaction.output.matches(output)) {
            transaction = new Transaction(seed, primary, secondary, output,
                    geometry(primary, chamber), geometry(secondary, chamber), geometry(output, chamber));
            transactions.put(chamber, transaction);
        }
        boolean completed = !chamber.isManualProcessing() && chamber.getManualCompletedAt() >= 0;
        double tick = transaction.clock.sample(candidate, completed);
        var motion = ScrollRevealMotion.sample(tick, wallSeconds);
        var rest = new ScrollRevealMotion.Quaternion(restQuaternion.x, restQuaternion.y,
                restQuaternion.z, restQuaternion.w);
        Quaternionf itemRotation = quaternion(ScrollRevealMotion.pose(motion, rest));
        ItemGeometry visibleMain = tick < 58 ? transaction.mainGeometry : transaction.outputGeometry;
        motion = motion.withClearance(visibleMain.minimumY(itemRotation, transaction.main.size),
                transaction.secondaryGeometry.minimumY(itemRotation, transaction.sub.size));
        if (Boolean.getBoolean("arcane.presentation.trace") && (transaction.traceTick != (int) tick
                || candidate + .0001 < transaction.traceCandidate || completed != transaction.traceCompleted)) {
            com.mojang.logging.LogUtils.getLogger().info(
                    "Chamber reveal {} seed={} server={} candidate={} shown={} imprint={} completed={} primaryY={} secondaryY={} minY={} outputFoil={}",
                    chamber.getBlockPos(), seed, chamber.getManualProgress(), candidate, tick, motion.imprintAlpha(),
                    completed, motion.mainY(), motion.secondaryY(),
                    motion.mainY() + visibleMain.minimumY(itemRotation, transaction.main.size), output.hasFoil());
            transaction.traceTick = (int) tick;
            transaction.traceCandidate = candidate;
            transaction.traceCompleted = completed;
        }
        Quaternionf billboard = new Quaternionf(restQuaternion).rotateY((float) Math.PI);
        Palette palette = crimson ? Palette.COOL : Palette.GOLD;
        ParticleStatus particleStatus = Minecraft.getInstance().options.particles().get();
        int particleStride = particleStatus == ParticleStatus.MINIMAL ? 0
                : particleStatus == ParticleStatus.DECREASED ? 2 : 1;

        pose.pushPose();
        pose.translate(motion.mainX(), motion.mainY(), 0);
        pose.mulPose(itemRotation);
        if (tick < 58) drawItem(transaction.mainGeometry, primary, transaction.main, tick, true,
                pose, buffers, light, overlay, chamber);
        else drawItem(transaction.outputGeometry, output, transaction.main, 104, false,
                pose, buffers, light, overlay, chamber);
        if (tick >= 10 && motion.imprintAlpha() > .0001)
            imprint(transaction.outputGeometry, transaction.main, tick, motion.imprintAlpha(),
                    palette, pose, buffers, overlay);
        crystals(transaction.main, tick, tick, palette, pose, buffers, overlay);
        pose.popPose();

        if (!secondary.isEmpty() && tick < 76) {
            pose.pushPose();
            pose.translate(motion.secondaryX(), motion.secondaryY(), 0);
            pose.mulPose(itemRotation);
            if (tick < 44) drawItem(transaction.secondaryGeometry, secondary, transaction.sub,
                    tick - 6, true, pose, buffers, light, overlay, chamber);
            crystals(transaction.sub, tick - 6, tick, palette, pose, buffers, overlay);
            pose.popPose();
        }
        if (particleStride > 0) {
            dust(transaction, tick, wallSeconds, motion, rest, itemRotation, palette,
                    pose, buffers, overlay, particleStride);
            if (rarityOrdinal >= 3 && tick >= 76)
                completion(tick, palette, billboard, pose, buffers, overlay, particleStride);
            if (rarityOrdinal >= 4 && tick >= 76)
                runes(tick, palette, billboard, pose, buffers, overlay, particleStride);
        }
    }

    private ItemGeometry geometry(ItemStack stack, AscensionChamberBlockEntity chamber) {
        if (stack.isEmpty()) return ItemGeometry.EMPTY;
        ItemKey key = new ItemKey(stack);
        ItemGeometry cached = geometries.get(key);
        if (cached != null) return cached;
        var minecraft = Minecraft.getInstance();
        var model = minecraft.getItemRenderer().getModel(stack, chamber.getLevel(), minecraft.player, 0);
        List<BakedQuad> source = new ArrayList<>();
        RandomSource random = RandomSource.create(42);
        for (Direction direction : Direction.values()) {
            random.setSeed(42); source.addAll(model.getQuads(null, direction, random));
        }
        random.setSeed(42); source.addAll(model.getQuads(null, null, random));
        List<Piece> pieces = new ArrayList<>();
        boolean[][] occupied = new boolean[GRID][GRID];
        for (BakedQuad quad : source) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            if (stride < 6) continue;
            Vertex[] vertices = new Vertex[4];
            for (int i = 0; i < 4; i++) {
                int offset = i * stride;
                vertices[i] = new Vertex(Float.intBitsToFloat(data[offset]) - .5,
                        Float.intBitsToFloat(data[offset + 1]) - .5,
                        Float.intBitsToFloat(data[offset + 2]) - .5,
                        Float.intBitsToFloat(data[offset + 4]), Float.intBitsToFloat(data[offset + 5]));
            }
            TextureAtlasSprite sprite = quad.getSprite();
            int tint = quad.isTinted() ? minecraft.getItemColors().getColor(stack, quad.getTintIndex()) : 0xFFFFFF;
            boolean front = quad.getDirection().getAxis() == Direction.Axis.Z;
            int steps = front ? Math.min(32, Math.max(16, sprite.contents().width())) : 1;
            for (int row = 0; row < steps; row++) for (int col = 0; col < steps; col++) {
                Vertex[] cell = {
                        bilinear(vertices, col / (double) steps, row / (double) steps),
                        bilinear(vertices, (col + 1.) / steps, row / (double) steps),
                        bilinear(vertices, (col + 1.) / steps, (row + 1.) / steps),
                        bilinear(vertices, col / (double) steps, (row + 1.) / steps)};
                Vertex center = bilinear(vertices, (col + .5) / steps, (row + .5) / steps);
                if (front && !opaque(sprite, center.u, center.v)) continue;
                pieces.add(new Piece(cell, quad.getDirection(), tint));
                if (front) {
                    int x = (int) Math.floor((center.x + .5) * GRID);
                    int y = (int) Math.floor((.5 - center.y) * GRID);
                    if (x >= 0 && x < GRID && y >= 0 && y < GRID) occupied[y][x] = true;
                }
            }
        }
        if (pieces.isEmpty()) {
            TextureAtlasSprite sprite = model.getParticleIcon();
            for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++)
                occupied[y][x] = opaque(sprite, sprite.getU((x + .5) / GRID * 16),
                        sprite.getV((y + .5) / GRID * 16));
        }
        cached = new ItemGeometry(pieces, occupied);
        geometries.put(key, cached);
        return cached;
    }

    private static boolean opaque(TextureAtlasSprite sprite, double u, double v) {
        double width = sprite.getU1() - sprite.getU0(), height = sprite.getV1() - sprite.getV0();
        if (width <= 0 || height <= 0) return false;
        int x = (int) Math.floor((u - sprite.getU0()) / width * sprite.contents().width());
        int y = (int) Math.floor((v - sprite.getV0()) / height * sprite.contents().height());
        x = Math.max(0, Math.min(sprite.contents().width() - 1, x));
        y = Math.max(0, Math.min(sprite.contents().height() - 1, y));
        int frame = sprite.contents().getUniqueFrames().findFirst().orElse(0);
        return (sprite.getPixelRGBA(frame, x, y) >>> 24) >= 38;
    }

    private void drawItem(ItemGeometry geometry, ItemStack stack, Layout layout, double time, boolean consume,
                          PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                          AscensionChamberBlockEntity chamber) {
        if (geometry.pieces.isEmpty()) {
            pose.pushPose(); pose.scale((float) layout.size, (float) layout.size, (float) layout.size);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
                    overlay, pose, buffers, chamber.getLevel(), 0);
            pose.popPose(); return;
        }
        VertexConsumer buffer = ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityCutoutNoCull(ATLAS), true, stack.hasFoil());
        for (Piece piece : geometry.pieces) {
            int count = 4;
            Vertex[] vertices = piece.vertices;
            if (consume && time >= layout.start) {
                count = clip(piece.vertices, layout, time, true, layout.size);
                vertices = clipA;
            }
            if (count < 3) continue;
            float shade = piece.normal == Direction.UP ? 1 : piece.normal == Direction.DOWN ? .75F
                    : piece.normal.getAxis() == Direction.Axis.X ? .85F : 1;
            float r = ((piece.tint >> 16) & 255) / 255F * shade;
            float g = ((piece.tint >> 8) & 255) / 255F * shade;
            float b = (piece.tint & 255) / 255F * shade;
            emitPolygon(pose, buffer, vertices, count, layout.size, r, g, b, 1, light, overlay);
        }
    }

    private void imprint(ItemGeometry geometry, Layout layout, double time, double alpha,
                         Palette palette, PoseStack pose, MultiBufferSource buffers, int overlay) {
        VertexConsumer buffer = buffers.getBuffer(ChamberGlowRenderTypes.core(WHITE));
        // The union of native alpha texels is drawn once, rather than brightening
        // once for each coloured model layer. Front and back remain pixel exact.
        for (Pixel pixel : geometry.pixels) {
            double x = pixel.x * layout.size, y = pixel.y * layout.size;
            double half = layout.size / GRID / 2;
            double front = layout.front(x, time);
            if (y - half > front) continue;
            double top = Math.min(y + half, front);
            if (top <= y - half) continue;
            float[] color = palette.grain(x, y, time);
            for (int side = -1; side <= 1; side += 2) {
                double z = side * .034 * layout.size;
                quad(pose, buffer, x - half, y - half, x + half, top, z,
                        color[0], color[1], color[2], alpha, overlay);
            }
        }
    }

    private void crystals(Layout layout, double time, double globalTime, Palette palette, PoseStack pose,
                          MultiBufferSource buffers, int overlay) {
        if (time <= 0 || time >= (layout.secondary ? 50 : 70)) return;
        VertexConsumer buffer = buffers.getBuffer(ChamberGlowRenderTypes.core(WHITE));
        int free = 0, index = 0;
        for (Crystal crystal : layout.crystals) {
            int colorIndex = index++;
            if (crystal.free && ++free > (layout.secondary ? 2 : 3)) continue;
            double age = time - crystal.birth, grow, fade;
            if (time < layout.start) {
                if (crystal.free || crystal.y > layout.minY + .070) continue;
                grow = smooth(time / layout.start) * .42; fade = 1;
            } else {
                if (age <= -1.8 || age >= 10) continue;
                grow = smooth((age + 1.8) / 2.6);
                fade = 1 - smooth((age - 3.5) / 6.5);
            }
            if (layout.secondary && time >= 38) fade *= 1 - smooth((time - 38) / 12);
            double alpha = grow * fade * (crystal.free ? .62 : .94);
            if (alpha < .008) continue;
            double scale = (.44 + .56 * grow) * (.22 + .78 * smooth(fade));
            float[] color = palette.grain(crystal.x, crystal.y, globalTime);
            // Seeded per-crystal colour identity sits on top of the continuous
            // body grain. Neighbours differ, without frame-random flicker or
            // an artificial border around their six softly shaded faces.
            double brightness = .84 + .30 * layout.noise(colorIndex + 6201);
            double hue = (layout.noise(colorIndex + 6301) - .5) * .10;
            color[0] = (float) Math.min(1, color[0] * brightness * (1 + hue));
            color[1] = (float) Math.min(1, color[1] * brightness);
            color[2] = (float) Math.min(1, color[2] * brightness * (1 - hue));
            cube(pose, buffer, crystal.x, crystal.y, crystal.z, crystal.size * scale,
                    color[0], color[1], color[2], alpha, overlay);
        }
    }

    private void dust(Transaction transaction, double time, double wallSeconds,
                      ScrollRevealMotion.Sample motion, ScrollRevealMotion.Quaternion rest,
                      Quaternionf itemRotation, Palette palette, PoseStack pose,
                      MultiBufferSource buffers, int overlay, int stride) {
        VertexConsumer core = buffers.getBuffer(ChamberGlowRenderTypes.core(WHITE));
        for (int role = 0; role < 2; role++) {
            Layout layout = role == 0 ? transaction.main : transaction.sub;
            double localTime = time - (role == 0 ? 0 : 6);
            for (Dust dust : layout.dust) {
                if (dust.index % stride != 0) continue;
                double age = localTime - dust.birth, p = clamp(age / dust.life);
                if (age < 0 || age >= dust.life) continue;
                double travel = (1 - Math.exp(-dust.drag * p)) / (1 - Math.exp(-dust.drag));
                double curl = dust.angle + dust.swirl * travel, arc = Math.sin(Math.PI * p);
                double dx = dust.distance * (Math.cos(dust.angle) * travel * .64 + dust.bendX * arc
                        + (Math.cos(curl) - Math.cos(dust.angle)) * .25);
                double dy = dust.distance * (Math.sin(dust.angle) * travel * .46 + dust.bendY * arc
                        + dust.lift * travel * (1 - .28 * p) + (Math.sin(curl) - Math.sin(dust.angle)) * .18);
                double dz = dust.distance * (dust.depth * travel + Math.sin(Math.PI * p * 1.25) * dust.swirl * .14);
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length > .14) { double k = .14 / length; dx *= k; dy *= k; dz *= k; }
                Vector3f point = new Vector3f((float) (dust.x + dx), (float) (dust.y + dy), (float) (dust.z + dz));
                Quaternionf rotation = itemRotation;
                double baseX = motion.mainX(), baseY = motion.mainY();
                if (role == 1 && !dust.absorb) {
                    double born = dust.birth + 6;
                    var birthMotion = ScrollRevealMotion.sample(born,
                            wallSeconds - (time - born) * ScrollRevealMotion.SECONDS_PER_TICK);
                    rotation = quaternion(ScrollRevealMotion.pose(birthMotion, rest));
                    baseX = birthMotion.secondaryX(); baseY = birthMotion.secondaryY();
                } else if (role == 1 && !transaction.main.crystals.isEmpty()) {
                    Vector3f target = target(transaction, dust, time, motion, itemRotation);
                    // target is transformed into secondary item coordinates.
                    double pull = smooth((p - .08) / (.65 - .08));
                    double bend = Math.sin(Math.PI * pull) * (1 - pull);
                    point.x += (target.x - point.x) * pull + Math.cos(dust.angle) * .024 * bend;
                    point.y += (target.y - point.y) * pull + dust.bendY * .034 * bend;
                    point.z += (target.z - point.z) * pull + dust.swirl * .020 * bend;
                    baseX = motion.secondaryX(); baseY = motion.secondaryY();
                }
                point.rotate(rotation).add((float) baseX, (float) baseY, 0);
                double alpha = dust.alpha * smooth(age / 1.7) * Math.pow(1 - p, 1.45);
                double size = dust.size * (1 - .9 * smooth(p));
                pose.pushPose(); pose.translate(point.x, point.y, point.z); pose.mulPose(rotation);
                float[] color = palette.grain(dust.x, dust.y, time);
                cube(pose, core, 0, 0, 0, size, color[0], color[1], color[2],
                        Math.min(1, alpha * 1.35), overlay);
                pose.popPose();
            }
        }
    }

    private Vector3f target(Transaction transaction, Dust dust, double time,
                            ScrollRevealMotion.Sample motion, Quaternionf rotation) {
        Crystal anchor = transaction.main.crystals.get(Math.floorMod(dust.index * 17,
                transaction.main.crystals.size()));
        scratch.set((float) anchor.x, (float) anchor.y, (float) anchor.z).rotate(rotation);
        scratch.add((float) (motion.mainX() - motion.secondaryX()),
                (float) (motion.mainY() - motion.secondaryY()), 0);
        return scratch.rotate(new Quaternionf(rotation).conjugate());
    }

    private void completion(double tick, Palette palette, Quaternionf billboard, PoseStack pose,
                            MultiBufferSource buffers, int overlay, int stride) {
        double elapsed = (tick - 76) / 20; // Original effect runs on the accepted logical clock.
        for (int index = 0; index < COMPLETION.length; index += stride) {
            Completion row = COMPLETION[index];
            double age = (elapsed - row.born) * 20;
            if (age < 0 || age >= row.life) continue;
            double p = age / row.life, rise = interpolate(row.path, age);
            double angle = row.angle + age / 20 * row.spin, radius = row.radius + p * .30;
            int frame = Math.max(0, Math.min(3, (int) Math.floor(age * 3 / row.life)));
            pose.pushPose();
            pose.translate(Math.cos(angle) * radius, row.y + rise - .25, Math.sin(angle) * radius);
            pose.mulPose(billboard);
            double size = row.size * clamp(age / row.life * 32);
            float fade = (float) (1 - smooth((p - .82) / .18));
            VertexConsumer core = buffers.getBuffer(ChamberGlowRenderTypes.core(WHITE));
            for (ParticlePixel pixel : particlePixels[palette == Palette.COOL ? 1 : 0][frame]) {
                float r = pixel.r, g = pixel.g, b = pixel.b;
                if (palette == Palette.COOL) {
                    float value = Math.max(r, Math.max(g, b));
                    r = r * .42F + value * palette.particle[0] * .58F;
                    g = g * .42F + value * palette.particle[1] * .58F;
                    b = b * .42F + value * palette.particle[2] * .58F;
                }
                quad(pose, core, pixel.left * size, pixel.bottom * size,
                        pixel.right * size, pixel.top * size, 0,
                        Math.min(1, r * 1.15F), Math.min(1, g * 1.15F), Math.min(1, b * 1.15F),
                        pixel.alpha * fade, overlay);
            }
            // The native pixel core supplies the glow. A separate radial mesh
            // becomes a visible polygon through shader-pack emissive passes.
            pose.popPose();
        }
    }

    private void runes(double tick, Palette palette, Quaternionf billboard, PoseStack pose,
                       MultiBufferSource buffers, int overlay, int stride) {
        VertexConsumer core = buffers.getBuffer(ChamberGlowRenderTypes.core(WHITE));
        for (int index = 0; index < RUNES.length; index += stride) {
            Rune row = RUNES[index];
            double age = tick - 76 - row.born;
            if (age < 0 || age >= row.life) continue;
            double p = age / row.life, rise = interpolate(row.path, age);
            double angle = row.angle + age / 20 * row.spin, radius = row.radius + p * .30;
            double grow = smooth(age / 3), fade = 1 - smooth((p - .64) / .36);
            double height = row.height * (.66 + .34 * grow) * (1 - .20 * (1 - fade));
            double unit = height / 11, width = unit * 8;
            pose.pushPose();
            pose.translate(Math.cos(angle) * radius, row.y + rise, Math.sin(angle) * radius);
            pose.mulPose(billboard);
            pose.mulPose(new Quaternionf().rotationZ((float) (row.roll + Math.sin(age / 11) * .045)));
            for (int y = 0; y < 11; y++) for (int x = 0; x < 8; x++) if (GLYPHS[row.glyph][y][x])
                quad(pose, core, x * unit - width / 2, height / 2 - (y + 1) * unit,
                        (x + 1) * unit - width / 2, height / 2 - y * unit, 0,
                        palette.rune[0], palette.rune[1], palette.rune[2], .92 * grow * fade, overlay);
            pose.popPose();
        }
    }

    private int clip(Vertex[] original, Layout layout, double time, boolean above, double scale) {
        int count = 0;
        Vertex previous = original[3];
        double previousValue = previous.y * scale - layout.front(previous.x * scale, time);
        boolean previousIn = above ? previousValue >= 0 : previousValue <= 0;
        for (Vertex current : original) {
            double currentValue = current.y * scale - layout.front(current.x * scale, time);
            boolean currentIn = above ? currentValue >= 0 : currentValue <= 0;
            if (currentIn != previousIn) {
                double f = previousValue / (previousValue - currentValue);
                clipA[count++] = previous.lerp(current, f);
            }
            if (currentIn) clipA[count++] = current;
            previous = current; previousValue = currentValue; previousIn = currentIn;
        }
        return count;
    }

    private static void emitPolygon(PoseStack pose, VertexConsumer buffer, Vertex[] vertices, int count,
                                    double scale, float r, float g, float b, double alpha, int light, int overlay) {
        if (count == 4) for (int i = 0; i < 4; i++) vertex(pose, buffer, vertices[i], scale, r, g, b, alpha, light, overlay);
        else for (int i = 1; i + 1 < count; i++) for (int index : new int[]{0, i, i + 1, i + 1})
            vertex(pose, buffer, vertices[index], scale, r, g, b, alpha, light, overlay);
    }

    private static void vertex(PoseStack pose, VertexConsumer buffer, Vertex v, double scale,
                               float r, float g, float b, double alpha, int light, int overlay) {
        buffer.vertex(pose.last().pose(), (float) (v.x * scale), (float) (v.y * scale), (float) (v.z * scale))
                .color(r, g, b, (float) clamp(alpha)).uv((float) v.u, (float) v.v).overlayCoords(overlay)
                .uv2(light).normal(pose.last().normal(), 0, 0, 1).endVertex();
    }

    private static void quad(PoseStack pose, VertexConsumer buffer, double left, double bottom,
                             double right, double top, double z, float r, float g, float b,
                             double alpha, int overlay) {
        rawVertex(pose, buffer, left, top, z, 0, 0, r, g, b, alpha, overlay);
        rawVertex(pose, buffer, left, bottom, z, 0, 1, r, g, b, alpha, overlay);
        rawVertex(pose, buffer, right, bottom, z, 1, 1, r, g, b, alpha, overlay);
        rawVertex(pose, buffer, right, top, z, 1, 0, r, g, b, alpha, overlay);
    }

    private static void rawVertex(PoseStack pose, VertexConsumer buffer, double x, double y, double z,
                                  float u, float v, float r, float g, float b, double alpha, int overlay) {
        buffer.vertex(pose.last().pose(), (float) x, (float) y, (float) z)
                .color(r, g, b, (float) clamp(alpha)).uv(u, v).overlayCoords(overlay)
                .uv2(LightTexture.FULL_BRIGHT).normal(pose.last().normal(), 0, 0, 1).endVertex();
    }

    private static final int[][] CUBE_FACES = {{4, 5, 6, 7}, {1, 0, 3, 2}, {0, 4, 7, 3},
            {5, 1, 2, 6}, {3, 7, 6, 2}, {0, 1, 5, 4}};
    private static final float[] FACE_SHADES = {.95F, .92F, .85F, .90F, 1F, .78F};

    private static void cube(PoseStack pose, VertexConsumer buffer, double x, double y, double z,
                             double edge, float r, float g, float b, double alpha, int overlay) {
        double h = edge / 2;
        for (int side = 0; side < 6; side++) {
            float shade = FACE_SHADES[side];
            for (int k = 0; k < 4; k++) {
                int c = CUBE_FACES[side][k];
                double vx = x + ((c == 1 || c == 2 || c == 5 || c == 6) ? h : -h);
                double vy = y + ((c == 2 || c == 3 || c == 6 || c == 7) ? h : -h);
                double vz = z + (c >= 4 ? h : -h);
                buffer.vertex(pose.last().pose(), (float) vx, (float) vy, (float) vz)
                        .color(r * shade, g * shade, b * shade, (float) alpha).uv(.5F, .5F)
                        .overlayCoords(overlay).uv2(LightTexture.FULL_BRIGHT)
                        .normal(pose.last().normal(), 0, 0, 1).endVertex();
            }
        }
    }

    private record ParticlePixel(double left, double bottom, double right, double top,
                                 float r, float g, float b, float alpha) {}

    @SuppressWarnings("unchecked")
    private static List<ParticlePixel>[][] loadParticlePixels() {
        List<ParticlePixel>[][] result = (List<ParticlePixel>[][]) new List<?>[2][4];
        for (int palette = 0; palette < 2; palette++) for (int frame = 0; frame < 4; frame++) {
            ResourceLocation id = ArcaneIndustry.id("textures/particle/ascension_cleanse_"
                    + (palette == 0 ? "gold" : "blue") + "_" + (frame + 1) + ".png");
            List<ParticlePixel> pixels = new ArrayList<>();
            try (var stream = Minecraft.getInstance().getResourceManager().open(id);
                 NativeImage image = NativeImage.read(stream)) {
                int width = image.getWidth(), height = image.getHeight();
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int rgba = image.getPixelRGBA(x, y);
                    int alpha = rgba >>> 24;
                    if (alpha == 0) continue;
                    pixels.add(new ParticlePixel(x / (double) width - .5, .5 - (y + 1.) / height,
                            (x + 1.) / width - .5, .5 - y / (double) height,
                            (rgba & 255) / 255F, ((rgba >>> 8) & 255) / 255F,
                            ((rgba >>> 16) & 255) / 255F, alpha / 255F));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read installed-dependency completion pixels " + id, exception);
            }
            result[palette][frame] = List.copyOf(pixels);
        }
        return result;
    }

    private record Vertex(double x, double y, double z, double u, double v) {
        Vertex lerp(Vertex other, double t) { return new Vertex(x + (other.x - x) * t,
                y + (other.y - y) * t, z + (other.z - z) * t,
                u + (other.u - u) * t, v + (other.v - v) * t); }
    }
    private static Vertex bilinear(Vertex[] v, double u, double w) { return v[0].lerp(v[1], u).lerp(v[3].lerp(v[2], u), w); }
    private record Piece(Vertex[] vertices, Direction normal, int tint) {}
    private record Pixel(double x, double y, int row, int col) {}
    private static final class ItemKey {
        final ItemStack stack;
        ItemKey(ItemStack stack) {
            this.stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (!this.stack.isEmpty()) this.stack.setCount(1);
        }
        boolean matches(ItemStack other) { return ItemStack.isSameItemSameTags(stack, other); }
        @Override public boolean equals(Object value) { return value instanceof ItemKey other && matches(other.stack); }
        @Override public int hashCode() { return 31 * stack.getItem().hashCode() + (stack.hasTag() ? stack.getTag().hashCode() : 0); }
    }
    private static final class ItemGeometry {
        static final ItemGeometry EMPTY = new ItemGeometry(List.of(), new boolean[GRID][GRID]);
        final List<Piece> pieces;
        final List<Pixel> pixels = new ArrayList<>();
        ItemGeometry(List<Piece> pieces, boolean[][] occupied) {
            this.pieces = pieces;
            for (int y = 0; y < GRID; y++) for (int x = 0; x < GRID; x++) if (occupied[y][x])
                pixels.add(new Pixel((x + .5) / GRID - .5, .5 - (y + .5) / GRID, y, x));
        }
        double minimumY(Quaternionf rotation, double scale) {
            double minimum = Double.POSITIVE_INFINITY;
            Vector3f point = new Vector3f();
            for (Piece piece : pieces) for (Vertex vertex : piece.vertices) {
                point.set((float) (vertex.x * scale), (float) (vertex.y * scale), (float) (vertex.z * scale));
                point.rotate(rotation);
                minimum = Math.min(minimum, point.y);
            }
            return Double.isFinite(minimum) ? minimum : -scale / 2;
        }
    }
    private static final class Transaction {
        final long seed;
        final ItemKey primary, secondary, output;
        final ItemGeometry mainGeometry, secondaryGeometry, outputGeometry;
        final Layout main, sub;
        final ScrollRevealMotion.PresentationClock clock = new ScrollRevealMotion.PresentationClock();
        int traceTick = -1;
        double traceCandidate;
        boolean traceCompleted;
        Transaction(long seed, ItemStack primary, ItemStack secondary, ItemStack output,
                    ItemGeometry mainGeometry, ItemGeometry secondaryGeometry, ItemGeometry outputGeometry) {
            this.seed = seed; this.primary = new ItemKey(primary); this.secondary = new ItemKey(secondary);
            this.output = new ItemKey(output); this.mainGeometry = mainGeometry;
            this.secondaryGeometry = secondaryGeometry; this.outputGeometry = outputGeometry;
            main = new Layout(mainGeometry.pixels, .66, (int) seed, false);
            sub = new Layout(secondaryGeometry.pixels, .32, (int) seed ^ 0x91af, true);
        }
    }
    private record Crystal(double x, double y, double z, double size, double birth, boolean free) {}
    private record Dust(double x, double y, double z, double size, double birth, double life,
                        double distance, double angle, double drag, double swirl, double bendX, double bendY,
                        double lift, double depth, double alpha, int wave, int index, boolean absorb) {
        Dust absorbing(boolean absorb) { return new Dust(x,y,z,size,birth,life,distance,angle,drag,swirl,bendX,bendY,lift,depth,alpha,wave,index,absorb); }
    }
    private static final class Layout {
        final double size, start, end, minY, maxY, phase;
        final int seed;
        final boolean secondary;
        final List<Crystal> crystals = new ArrayList<>();
        final List<Dust> dust = new ArrayList<>();
        Layout(List<Pixel> pixels, double size, int seed, boolean secondary) {
            this.size = size; this.seed = seed; this.secondary = secondary;
            start = secondary ? 8 : 10; end = secondary ? 38 : 58;
            minY = pixels.stream().mapToDouble(p -> (p.y - .5 / GRID) * size).min().orElse(-size / 2);
            maxY = pixels.stream().mapToDouble(p -> (p.y + .5 / GRID) * size).max().orElse(size / 2);
            phase = noise(901) * TAU;
            int target = Math.min(pixels.size(), Math.min(secondary ? 38 : 110,
                    Math.max(secondary ? 12 : 50, (int) Math.round(pixels.size() * .59 * Math.pow(size / .66, 2)) + 2)));
            boolean[] used = new boolean[pixels.size()];
            double[] nearest = new double[pixels.size()]; Arrays.fill(nearest, Double.POSITIVE_INFINITY);
            int first = pixels.isEmpty() ? 0 : (int) Math.floor(noise(17) * pixels.size());
            for (int k = 0; k < target; k++) {
                int best = first; double score = -1;
                if (k > 0) for (int i = 0; i < pixels.size(); i++) if (!used[i]) {
                    Pixel candidate = pixels.get(i);
                    double s = nearest[i] * (.82 + .36 * noise(candidate.row * GRID + candidate.col + 1000));
                    if (s > score) { score = s; best = i; }
                }
                used[best] = true; Pixel pixel = pixels.get(best);
                double x = pixel.x * size + (noise(k + 401) - .5) * size / GRID * .22;
                double y = pixel.y * size + (noise(k + 503) - .5) * size / GRID * .22;
                double lo = start, hi = end;
                for (int i = 0; i < 16; i++) { double mid = (lo + hi) / 2; if (front(x, mid) < y) lo = mid; else hi = mid; }
                double birth = Math.min(end - .4, Math.max(start, (lo + hi) / 2 + (noise(k + 701) - .5) * 1.4));
                crystals.add(new Crystal(x, y, (noise(k + 607) - .5) * .044,
                        .045 + noise(k + 311) * .037, birth, false));
                for (int i = 0; i < pixels.size(); i++) {
                    Pixel q = pixels.get(i); double dx = q.x - pixel.x, dy = q.y - pixel.y;
                    nearest[i] = Math.min(nearest[i], (dx * dx + dy * dy) * size * size);
                }
            }
            for (int i = 0; i < Math.min(3, target); i++) {
                Crystal anchor = crystals.get((int) Math.floor(noise(i + 1301) * target));
                double angle = noise(i + 1401) * TAU;
                crystals.add(new Crystal(anchor.x + Math.cos(angle) * .02, anchor.y + Math.sin(angle) * .018,
                        anchor.z + (noise(i + 1501) - .5) * .050, .045 + noise(i + 1601) * .037,
                        anchor.birth + (noise(i + 1701) - .5) * 2, true));
            }
            if (pixels.isEmpty()) return;
            int count = secondary ? 40 : 28;
            double[] centers = secondary ? new double[]{8.7,16.1,23.2,30.4} : new double[]{12.8,25,38.5,51};
            for (int i = 0; i < count; i++) {
                int wave = i % 4;
                double birth = Math.max(secondary ? 8 : 12, Math.min(secondary ? 32 : 52, centers[wave] + (noise(i + 2401) - .5) * 3));
                List<Pixel> consumed = new ArrayList<>(), near = new ArrayList<>(), contour = new ArrayList<>();
                for (int attempt = 0; attempt < 33; attempt++) {
                    consumed.clear(); near.clear(); contour.clear();
                    for (Pixel pixel : pixels) {
                        double distance = front(pixel.x * size, birth) - (pixel.y - .5 / GRID) * size;
                        if (distance >= -.001 * size / GRID) {
                            consumed.add(pixel);
                            if (distance < size / GRID * 3) {
                                near.add(pixel);
                                if (isContour(pixel, pixels)) contour.add(pixel);
                            }
                        }
                    }
                    if (!consumed.isEmpty()) break;
                    birth += .10;
                }
                if (consumed.isEmpty()) continue;
                List<Pixel> pool = !secondary && !contour.isEmpty() && noise(i + 2151) < .85 ? contour
                        : !near.isEmpty() && noise(i + 2151) < .85 ? near : consumed;
                Pixel source = pool.get((int) Math.floor(noise(i + 2101) * pool.size()));
                double x = source.x * size + (noise(i + 2601) - .5) * size / GRID * .88;
                double lower = (source.y - .4999 / GRID) * size;
                for (int attempt = 0; attempt < 8 && front(x, birth) < lower + size / GRID * .0002; attempt++) birth += .05;
                double upper = Math.max(lower, Math.min((source.y + .4999 / GRID) * size, front(x, birth) - size / GRID * .0001));
                dust.add(new Dust(x, lower + (upper - lower) * (.08 + noise(i + 2701) * .84),
                        (noise(i + 2801) - .5) * .036, .006 + noise(i + 2901) * (secondary ? .012 : .008),
                        birth, Math.min(16 + noise(i + 2501) * 9, (secondary ? 57 : 76) - birth),
                        (secondary ? .03 : .05) + noise(i + 2301) * (secondary ? .11 : .09),
                        noise(i + 2201) * TAU, 1.1 + noise(i + 3001) * 2.1, (noise(i + 3101) - .5) * 2.2,
                        (noise(i + 3201) - .5) * .85, (noise(i + 3301) - .5) * .55,
                        .18 + noise(i + 3401) * .48, (noise(i + 3501) - .5) * .52,
                        (secondary ? .48 : .38) + noise(i + 3601) * (secondary ? .30 : .22),
                        wave, target + Math.min(3, target) + i, false));
            }
            if (secondary) for (int wave = 0; wave < 4; wave++) {
                final int selectedWave = wave;
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < dust.size(); i++) if (dust.get(i).wave == selectedWave) indices.add(i);
                indices.sort((a, b) -> Double.compare(noise(dust.get(a).index + 4901), noise(dust.get(b).index + 4901)));
                for (int rank = 0; rank < indices.size() / 2; rank++) {
                    int index = indices.get(rank); dust.set(index, dust.get(index).absorbing(true));
                }
            }
        }
        double front(double x, double time) {
            double p = clamp((time - start) / (end - start));
            double shape = .50 * Math.sin(x / size * 5 + phase) + .22 * Math.sin(x / size * 9 - phase * .60);
            return minY + (maxY - minY) * p + size / GRID * shape * Math.sin(Math.PI * p);
        }
        double noise(int index) {
            int n = seed ^ (index + 101) * 374761393;
            n = (n ^ (n >>> 13)) * 1274126177;
            return Integer.toUnsignedLong(n ^ (n >>> 16)) / 4294967296.;
        }
        private static boolean isContour(Pixel pixel, List<Pixel> pixels) {
            int adjacent = 0;
            for (Pixel p : pixels) if (Math.abs(p.row - pixel.row) + Math.abs(p.col - pixel.col) == 1) adjacent++;
            return adjacent < 4;
        }
    }
    private static final class Palette {
        static final Palette GOLD = new Palette(new float[]{1,.84F,.42F},
                new float[]{1,1,1}, new float[]{1,.84F,.42F},
                new float[]{248/255F,186/255F,39/255F}, new float[]{1,240/255F,174/255F});
        static final Palette COOL = new Palette(new float[]{.56F,.68F,1},
                new float[]{.80F,.56F,.92F}, new float[]{.62F,.67F,1},
                new float[]{104/255F,116/255F,220/255F}, new float[]{211/255F,213/255F,1});
        final float[] core, particle, rune, low, high;
        private final float[] color = new float[3];
        Palette(float[] core, float[] particle, float[] rune, float[] low, float[] high) {
            this.core = core; this.particle = particle; this.rune = rune; this.low = low; this.high = high;
        }
        float[] grain(double x, double y, double tick) {
            double dx = tick * .004, dy = -tick * .002;
            double n = .65 * valueNoise(x * 28 + dx, y * 28 + dy)
                    + .35 * valueNoise(x * 8 - dx * .6 + 8.7, y * 8 - dy * .6 + 3.1);
            // More readable colour grain in the non-bloom game renderer. Smooth
            // value noise changes hue/luminance, never draws a dark cube outline.
            for (int i = 0; i < 3; i++) color[i] = (float) (core[i] * .48
                    + (low[i] + (high[i] - low[i]) * n) * .52);
            double originalLuma = core[0] * .2126 + core[1] * .7152 + core[2] * .0722;
            double luma = color[0] * .2126 + color[1] * .7152 + color[2] * .0722;
            double gain = originalLuma / Math.max(.001, luma) * (.90 + .20 * n);
            for (int i = 0; i < 3; i++) color[i] = (float) Math.max(0, Math.min(1, color[i] * gain));
            return color;
        }
    }
    private record Completion(double born, int life, double size, double angle, double radius, double y, double spin, double[] path) {}
    private record Rune(int glyph, double born, int life, double height, double angle, double radius, double y, double spin, double roll, double[] path) {}
    private static final class Lcg { int state; Lcg(int seed) { state = seed; } double next() { state = state * 1664525 + 1013904223; return Integer.toUnsignedLong(state) / 4294967296.; } }
    private static Completion[] completionRows() {
        Lcg random = new Lcg(43043); Completion[] rows = new Completion[48];
        for (int i = 0; i < rows.length; i++) {
            int life = (int) Math.floor(12 / (.5 + random.next() * .5));
            double[] path = rise(life, .012 + random.next() * .012);
            double size = .225 + random.next() * .225, angle = random.next() * TAU;
            double radius = .12 + random.next() * .18, y = 1.25 + random.next() * .25;
            random.next();
            rows[i] = new Completion(i / 47. * 1.4, life, size, angle, radius, y, 4.2 + random.next() * 2.8, path);
        }
        return rows;
    }
    private static Rune[] runeRows() {
        Lcg random = new Lcg(78117); Rune[] rows = new Rune[12];
        for (int i = 0; i < rows.length; i++) {
            int glyph = (i * 7 + (int) (random.next() * 4)) % 16;
            int life = 20 + (int) (random.next() * 6);
            double[] path = rise(life, .012 + random.next() * .012);
            double born = 1 + i / 11. * 22 + random.next() * .6;
            rows[i] = new Rune(glyph, born, life, .22 + random.next() * .065,
                    random.next() * TAU, .17 + random.next() * .13, 1.03 + random.next() * .20,
                    4.2 + random.next() * 2.8, (random.next() - .5) * .22, path);
        }
        return rows;
    }
    private static double[] rise(int life, double velocity) {
        double[] path = new double[life + 2]; double y = 0;
        for (int i = 0; i <= life; i++) { velocity += .004; y += velocity; velocity *= .96; path[i + 1] = y; }
        return path;
    }
    private static double interpolate(double[] values, double t) {
        int index = Math.max(0, Math.min(values.length - 2, (int) Math.floor(t)));
        double fraction = t - index; return values[index] * (1 - fraction) + values[index + 1] * fraction;
    }
    private static Quaternionf quaternion(ScrollRevealMotion.Quaternion q) { return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w()); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double smooth(double value) { double t = clamp(value); return t * t * (3 - 2 * t); }
    private static double hash(double x, double y) { double value = Math.sin(x * 127.1 + y * 311.7) * 43758.5453; return value - Math.floor(value); }
    private static double valueNoise(double x, double y) {
        double ix = Math.floor(x), iy = Math.floor(y), fx = smooth(x - ix), fy = smooth(y - iy);
        double a = hash(ix, iy) * (1 - fx) + hash(ix + 1, iy) * fx;
        double b = hash(ix, iy + 1) * (1 - fx) + hash(ix + 1, iy + 1) * fx;
        return a * (1 - fy) + b * fy;
    }
    private static boolean[][][] glyphs() {
        double[][][] strokes = {
                {{3,2.8,3,12.1},{3,6,8.5,1.8},{3,9,8.5,4.6}},
                {{2,12.1,2,2},{2,2,9,5.5},{9,5.5,9,12.1}},
                {{3,2.8,3,12.1},{3,4.5,8.5,7.5},{8.5,7.5,3,10.5}},
                {{3,2.8,3,12.1},{3,3.5,8.5,6.4},{3,8,8.5,11}},
                {{3,2.1,3,12.1},{3,2.1,8.5,5.4},{8.5,5.4,3,8.5},{3,8.5,8.5,12.7}},
                {{8.5,2.7,4,7.5},{4,7.5,8.5,12.1}},
                {{2,2.8,8,12.1},{8,2.8,2,12.1}},
                {{3,2.1,3,12.1},{3,2.1,8.5,5.4},{8.5,5.4,3,8.5}},
                {{2,2.8,2,12.1},{9,2.8,9,12.1},{2,5.5,9,11}},
                {{5,2.8,5,12.1},{3,5,8.5,9}},
                {{5,2.8,5,12.1}},
                {{6,2.8,6,12.1},{2,2.8,6,7.5},{9,2.8,6,7.5}},
                {{5.6,2.7,5.6,12.1},{2,6.7,5.6,2.7},{5.6,2.7,9,6.7}},
                {{2,2,2,13},{2,2,8.5,5.3},{8.5,5.3,2,7.7},{2,7.7,8.5,10.2},{8.5,10.2,2,13}},
                {{3,2.1,3,12.1},{3,2.1,8.5,6.4}},
                {{5.5,2.7,2,5.5},{2,5.5,5.5,8.5},{5.5,8.5,9,5.5},{9,5.5,5.5,2.7},{5.5,8.5,2,12.1},{5.5,8.5,9,12.1}}
        };
        boolean[][][] masks = new boolean[16][11][8];
        for (int glyph = 0; glyph < 16; glyph++) for (double[] stroke : strokes[glyph]) {
            int x = (int) Math.floor(stroke[0] / 11 * 8), y = (int) Math.floor(stroke[1] / 15 * 11);
            int x1 = (int) Math.floor(stroke[2] / 11 * 8), y1 = (int) Math.floor(stroke[3] / 15 * 11);
            int dx = Math.abs(x1 - x), dy = -Math.abs(y1 - y), sx = x < x1 ? 1 : -1, sy = y < y1 ? 1 : -1, error = dx + dy;
            for (;;) {
                mark(masks[glyph], x, y); if (x == x1 && y == y1) break;
                int twice = 2 * error, px = x, py = y;
                if (twice >= dy) { error += dy; x += sx; } if (twice <= dx) { error += dx; y += sy; }
                if (x != px && y != py) mark(masks[glyph], x, py);
            }
        }
        masks[0][3][6] = false;
        return masks;
    }
    private static void mark(boolean[][] mask, int x, int y) { if (x >= 0 && x < 8 && y >= 0 && y < 11) mask[y][x] = true; }
}
