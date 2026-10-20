package dev.expedition.arcane.client;

import dev.expedition.arcane.ArcaneIndustry;
import dev.expedition.arcane.chamber.ManualUpgradeRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Native resource-pack sounds only: CEI operation cues and the approved research score. */
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

    /** CEI 2.5.4b enchanter/forger completion events, category, volume and pitch range. */
    static SimpleSoundInstance complete(String operation, BlockPos pos) {
        SoundEvent event = completionEvent(operation);
        RandomSource random = RandomSource.create();
        SimpleSoundInstance instance = new SimpleSoundInstance(event, SoundSource.BLOCKS,
                1, .9F + random.nextFloat() * .1F, random, pos);
        Minecraft.getInstance().getSoundManager().play(instance);
        return instance;
    }

    static SoundEvent completionEvent(String operation) {
        return switch (operation) {
            case ManualUpgradeRecipes.UNIQUE_SPELL_UPGRADE, ManualUpgradeRecipes.SPELL_IMBUE,
                    ManualUpgradeRecipes.ORB_UPGRADE, ManualUpgradeRecipes.BOOK_SLOT_UPGRADE,
                    ManualUpgradeRecipes.AFFINITY_BIND, ManualUpgradeRecipes.SHRIVING -> SoundEvents.ANVIL_USE;
            default -> SoundEvents.ENCHANTMENT_TABLE_USE;
        };
    }

    static Research research(BlockPos pos, boolean crimson) { return new Research(pos, crimson); }

    /**
     * A local cue cursor, driven by the same paid-work clock as the reveal. Never catches up
     * old sounds when a chunk is first observed. All active tails belong to this session.
     */
    static final class Research {
        private final BlockPos pos;
        private final ChamberResearchSoundPlan.Cursor cueCursor;
        private final List<Clip> voices = new ArrayList<>();
        private double previousTicks = Double.NaN;
        private boolean stopped;

        Research(BlockPos pos, boolean crimson) {
            this.pos = pos.immutable();
            cueCursor = new ChamberResearchSoundPlan.Cursor(crimson);
        }

        void update(double logicalTicks, int rarityOrdinal, boolean running) {
            if (stopped || !Double.isFinite(logicalTicks)) return;
            voices.removeIf(Clip::isStopped);
            boolean active = running && Minecraft.getInstance().level != null;
            if (!active || logicalTicks < previousTicks - .01) silence();
            for (ChamberResearchSoundPlan.Cue cue : cueCursor.update(logicalTicks, rarityOrdinal, active)) {
                SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(cue.event()));
                if (event == null) continue;
                Clip voice = new Clip(event, pos, cue);
                voices.add(voice);
                Minecraft.getInstance().getSoundManager().play(voice);
            }
            previousTicks = logicalTicks;
        }

        void stop() { stopped = true; silence(); }

        private void silence() {
            var manager = Minecraft.getInstance().getSoundManager();
            for (Clip voice : voices) { voice.end(); manager.stop(voice); }
            voices.clear();
        }
    }

    private static final class Clip extends AbstractTickableSoundInstance {
        private final ChamberResearchSoundPlan.Cue cue;
        private int age;

        Clip(SoundEvent event, BlockPos pos, ChamberResearchSoundPlan.Cue cue) {
            super(event, SoundSource.BLOCKS, RandomSource.create());
            this.cue = cue;
            x = pos.getX() + .5; y = pos.getY() + 1.1; z = pos.getZ() + .5;
            pitch = cue.pitch();
            volume = 0;
            looping = false;
        }

        void end() { stop(); }

        @Override public boolean canStartSilent() { return true; }

        @Override public void tick() {
            double seconds = ++age / 20.0;
            if (seconds >= cue.duration() || Minecraft.getInstance().level == null) { stop(); return; }
            volume = cue.gain() * smooth(seconds / cue.attack()) * smooth((cue.duration() - seconds) / cue.release());
        }
    }

    private static float smooth(double value) {
        float t = Mth.clamp((float) value, 0, 1);
        return t * t * (3 - 2 * t);
    }

}
