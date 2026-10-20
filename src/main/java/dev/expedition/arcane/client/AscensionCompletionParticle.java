package dev.expedition.arcane.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** Native 8x8 cleanse flecks, with the accepted low gold/blue completion spiral. */
final class AscensionCompletionParticle extends TextureSheetParticle {
    private static SpriteSet gold, blue;
    private final SpriteSet sprites;
    private final double centerX, centerZ, angle, radius, angularSpeed;

    static void sprites(boolean isBlue, SpriteSet value) { if (isBlue) blue = value; else gold = value; }

    static void emit(ClientLevel level, BlockPos pos, int count, boolean isBlue) {
        ParticleStatus setting = Minecraft.getInstance().options.particles().get();
        if (setting == ParticleStatus.MINIMAL) return;
        for (int i = 0; i < count; i++) {
            if (setting == ParticleStatus.DECREASED && level.random.nextBoolean()) continue;
            SpriteSet sprites = isBlue ? blue : gold;
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
        alpha = 1 - (float) Mth.smoothstep(Mth.clamp((age / (float) lifetime - .45F) / .55F, 0, 1));
        setSpriteFromAge(sprites);
    }

    @Override public int getLightColor(float partialTick) { return LightTexture.FULL_BRIGHT; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public float getQuadSize(float partialTick) {
        float life = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        return quadSize * Mth.clamp(life * 32, 0, 1) * (1 - .65F * life * life);
    }
}
