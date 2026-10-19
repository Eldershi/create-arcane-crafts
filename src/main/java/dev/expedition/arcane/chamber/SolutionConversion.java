package dev.expedition.arcane.chamber;

/** Pure fill arithmetic: switching solution discards the previous liquid rather than exchanging it. */
public final class SolutionConversion {
    private SolutionConversion() {}

    /** A successful replacement contains only accepted incoming liquid; credit belongs to crimson work. */
    public record FillPlan(int acceptedMb, int storedMb, int crimsonCreditQuarterMb,
                           boolean replaced, int discardedMb) {}

    public static FillPlan plan(int storedMb, boolean storedCrimson, int crimsonCreditQuarterMb,
                                boolean incomingCrimson, int requestedMb, int capacity) {
        if (capacity < 0 || storedMb < 0 || storedMb > capacity
                || crimsonCreditQuarterMb < 0 || crimsonCreditQuarterMb > 3) {
            throw new IllegalArgumentException("Invalid solution tank or fractional credit");
        }
        if (requestedMb <= 0) return unchanged(storedMb, crimsonCreditQuarterMb);
        boolean foreign = storedMb > 0 && storedCrimson != incomingCrimson;
        int accepted = Math.min(requestedMb, foreign ? capacity : capacity - storedMb);
        if (accepted <= 0) return unchanged(storedMb, crimsonCreditQuarterMb);
        // A dry tank can still hold a crimson processing remainder. Blue never inherits it.
        boolean discardCredit = foreign || !incomingCrimson;
        return new FillPlan(accepted, foreign ? accepted : storedMb + accepted,
                discardCredit ? 0 : crimsonCreditQuarterMb,
                foreign || (discardCredit && crimsonCreditQuarterMb > 0), foreign ? storedMb : 0);
    }

    private static FillPlan unchanged(int storedMb, int credit) {
        return new FillPlan(0, storedMb, credit, false, 0);
    }
}
