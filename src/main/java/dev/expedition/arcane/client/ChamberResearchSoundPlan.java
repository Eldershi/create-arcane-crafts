package dev.expedition.arcane.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Resource names and cue decisions are pure so timing can be checked without launching Minecraft. */
final class ChamberResearchSoundPlan {
    static final double SECONDS_PER_REVEAL_TICK = 6.8 / 112;
    private static final double MAX_CUE_LATENESS = .20 / SECONDS_PER_REVEAL_TICK;

    private ChamberResearchSoundPlan() {}

    static final class Cursor {
        private final List<Cue> cues;
        private double tick = Double.NaN;
        Cursor(boolean crimson) { cues = cues(crimson); }

        List<Cue> update(double logicalTicks, int rarityOrdinal, boolean running) {
            if (!Double.isFinite(logicalTicks)) return List.of();
            if (!running || logicalTicks < tick - .01) { tick = logicalTicks; return List.of(); }
            if (Double.isNaN(tick)) tick = logicalTicks <= 2 ? -.001 : logicalTicks;
            double from = Math.max(tick, logicalTicks - MAX_CUE_LATENESS);
            List<Cue> due = cues.stream().filter(cue -> cue.tick > from && cue.tick <= logicalTicks
                    && cue.accepts(rarityOrdinal)).toList();
            tick = logicalTicks;
            return due;
        }
    }

    record Cue(String event, double tick, float gain, float pitch,
               double duration, double attack, double release, int minimumRarity, int maximumRarity) {
        boolean accepts(int rarity) { return rarity >= minimumRarity && rarity <= maximumRarity; }
    }

    static List<Cue> cues(boolean crimson) {
        List<Cue> cues = new ArrayList<>();
        float pitch = crimson ? .94F : 1.02F;
        // Gains include the preview master/equal-power centre; the game handles spatial attenuation.
        cue(cues, "irons_spellbooks:magic_spell_reverse_3", .035 / SECONDS_PER_REVEAL_TICK,
                .20F, .79F * pitch, Math.min(2.65, 2 / (.79 * pitch)), .17, .65, -1, 4);
        cue(cues, "irons_spellbooks:spell.summon_swords.charge", 16,
                .12F, .97F * pitch, Math.min(2.36, 2.177778 / (.97 * pitch)), .42, .68, -1, 4);
        Random random = new Random(86317);
        for (double tick : new double[]{8, 11.6, 15.5, 19.3, 24.8, 29.2, 34.5, 39.2, 45.6, 51.3}) {
            double at = tick + (random.nextDouble() - .5) * 1.15;
            float gain = (float) (.075 + random.nextDouble() * .035) * .65F;
            float speed = (float) (1.18 + random.nextDouble() * .48) * pitch;
            double duration = .20 + random.nextDouble() * .12;
            cue(cues, "minecraft:block.amethyst_block.place", at, gain, speed, duration, .012, .16, -1, 4);
        }
        cue(cues, "minecraft:block.amethyst_block.place", 42, .085F, .75F * pitch,
                .75, .035, .40, -1, 4);
        // The high-tier arrival begins with the delayed vortex, keeping the full native tail.
        cue(cues, "minecraft:block.beacon.activate", 76, .44F, 1, 2.9533, .035, .28, 3, 3);
        cue(cues, "minecraft:block.beacon.activate", 76, .49F, 1, 2.9533, .035, .28, 4, 4);
        for (int rarity = 3; rarity <= 4; rarity++) {
            int count = rarity == 4 ? 7 : 4;
            for (int index = 0; index < count; index++) {
                double at = 76 + index * 4.1 + random.nextDouble() * 1.1;
                float gain = (rarity == 4 ? .052F : .041F) * (1 - index / (float) (count + 2)) * .65F;
                float speed = (float) (1.42 + random.nextDouble() * .35) * pitch;
                double duration = .28 + random.nextDouble() * .13;
                cue(cues, "minecraft:block.amethyst_block.place", at, gain, speed,
                        duration, .04, .22, rarity, rarity);
            }
        }
        cue(cues, "minecraft:block.enchantment_table.use", 95.5, .145F, pitch,
                1.5627 / pitch, .035, .34, 0, 2);
        return List.copyOf(cues);
    }

    private static void cue(List<Cue> cues, String event, double tick, float gain, float pitch,
                            double duration, double attack, double release, int min, int max) {
        cues.add(new Cue(event, tick, gain, pitch, duration, attack, release, min, max));
    }

}
