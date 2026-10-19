package dev.expedition.arcane.chamber;

/** Exact basis-point lottery, independent of game state so its boundaries can be audited offline. */
public final class ResearchLottery {
    public static final int TICKETS = 10_000;
    private static final int[] BLUE = {7000, 2000, 700, 250, 50};
    private static final int[] RED = {6500, 2200, 800, 400, 100};
    private static final int[] HEADWEAR_DELTA = {-500, 300, 200, 0, 0};

    private ResearchLottery() {}

    /** Common through legendary, before empty-tier fallback; callers receive their own copy. */
    public static int[] weights(boolean crimson, boolean headwear) {
        int[] result = (crimson ? RED : BLUE).clone();
        if (headwear) for (int tier = 0; tier < result.length; tier++) result[tier] += HEADWEAR_DELTA[tier];
        return result;
    }

    public static int rollTier(int ticket, boolean crimson, boolean headwear) {
        if (ticket < 0 || ticket >= TICKETS) throw new IllegalArgumentException("Invalid research ticket");
        int[] weights = weights(crimson, headwear);
        for (int tier = 0; tier < weights.length; tier++) {
            if (ticket < weights[tier]) return tier;
            ticket -= weights[tier];
        }
        throw new IllegalStateException("Research weights must total 10000");
    }

    /** A missing common pool is invalid. Missing higher tiers only fall to the closest lower tier. */
    public static int fallbackTier(int drawnTier, boolean[] nonempty) {
        if (nonempty.length != 5 || drawnTier < 0 || drawnTier >= 5 || !nonempty[0]) return -1;
        for (int tier = drawnTier; tier >= 0; tier--) if (nonempty[tier]) return tier;
        return -1;
    }
}
