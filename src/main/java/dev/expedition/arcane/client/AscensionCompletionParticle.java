package dev.expedition.arcane.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** Native 8x8 cleanse flecks, with the accepted low gold/blue completion spiral. */
final class AscensionCompletionParticle extends TextureSheetParticle {
    private static SpriteSet gold, blue;
    private final SpriteSet sprites;
    private final double centerX, centerZ, angle, radius, angularSpeed;

    static void sprites(boolean isBlue, SpriteSet value) { if (isBlue) blue = value; else gold = value; }

    static void emit(ClientLevel level, BlockPos pos, int count) {
        for (int i = 0; i < count; i++) {
            SpriteSet sprites = level.random.nextBoolean() ? gold : blue;
            if (sprites != null) Minecraft.getInstance().particleEngine.add(new AscensionCompletionParticle(level, pos, sprites));
        }
    }

    private AscensionCompletionParticle(ClientLevel level, BlockPos pos, SpriteSet sprites) {
        super(level, pos.getX() + .5, pos.getY() + 1.0 + level.random.nextDouble() * .25, pos.getZ() + .5);
        this.sprites = sprites;
        centerX = x; centerZ = z;
        angle = random.nextDouble() * Math.PI * 2;
        radius = .12 + random.nextDouble() * .18;
        angularSpeed = 4.2 + random.nextDouble() * 2.8;
        quadSize *= 1.125F;
        lifetime = Math.max(12, (int) (12 / Mth.randomBetween(random, .5F, 1)));
        yd = .012 + random.nextDouble() * .012;
        hasPhysics = false;
        x = xo = centerX + Math.cos(angle) * radius;
        z = zo = centerZ + Math.sin(angle) * radius;
        setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        double phase = angle + age / 20.0 * angularSpeed;
        double r = radius + .30 * age / lifetime;
        x = centerX + Math.cos(phase) * r;
        z = centerZ + Math.sin(phase) * r;
        yd += .004;
        y += yd;
        yd *= .96;
        setSpriteFromAge(sprites);
    }

    @Override public int getLightColor(float partialTick) { return 240; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }
    @Override public float getQuadSize(float partialTick) {
        return quadSize * Mth.clamp((age + partialTick) / lifetime * 32, 0, 1);
    }
}
