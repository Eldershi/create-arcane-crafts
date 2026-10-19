package dev.expedition.arcane.client;

import dev.expedition.arcane.ArcaneIndustry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

/** One native sample per cue; the last second of the 70-tick operation is deliberately silent. */
final class ChamberManualSounds {
    private ChamberManualSounds() {}

    static SimpleSoundInstance play(String key, BlockPos pos, float volume, float pitch) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(ArcaneIndustry.id(key));
        if (sound == null) return null;
        SimpleSoundInstance instance = new SimpleSoundInstance(
                sound, SoundSource.BLOCKS, volume, pitch, RandomSource.create(), pos);
        Minecraft.getInstance().getSoundManager().play(instance);
        return instance;
    }

    static Work work(BlockPos pos, boolean superheated, int elapsedTicks) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(ArcaneIndustry.id("work"));
        if (sound == null) return null;
        Work instance = new Work(sound, pos, superheated, elapsedTicks);
        Minecraft.getInstance().getSoundManager().play(instance);
        return instance;
    }

    static final class Work extends AbstractTickableSoundInstance {
        private int age;
        private int fade = -1;
        private float fadeVolume;
        Work(SoundEvent sound, BlockPos pos, boolean superheated, int elapsedTicks) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            x = pos.getX() + .5; y = pos.getY() + 1.1; z = pos.getZ() + .5;
            pitch = .8F * (superheated ? .94F : 1);
            volume = 0;
            age = elapsedTicks;
            looping = false;
        }
        void fadeOut() { if (fade < 0) { fade = 0; fadeVolume = volume; } }
        void end() { stop(); }
        @Override public void tick() {
            if (fade >= 0) {
                volume = fadeVolume * (1 - Mth.clamp(++fade / 6F, 0, 1));
                if (fade >= 6) stop();
                return;
            }
            ++age;
            volume = .088F * Mth.clamp(age / 2.4F, 0, 1) * Mth.clamp((50 - age) / 5.6F, 0, 1);
            if (age >= 50 || Minecraft.getInstance().level == null) stop();
        }
    }
}
