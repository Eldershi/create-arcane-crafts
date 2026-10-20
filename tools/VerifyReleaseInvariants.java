package dev.expedition.arcane.client;

import dev.expedition.arcane.chamber.ResearchLottery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Portable Java 17 contract checks. No Minecraft classes, assets, sound device,
 * local installation, WebGL fixture or third-party test library is needed.
 */
public final class VerifyReleaseInvariants {
    private static final Map<String, Integer> checks = new LinkedHashMap<>();
    private static String group;

    private VerifyReleaseInvariants() {}

    public static void main(String[] args) throws Exception {
        clock();
        idleAndReturn();
        reveal();
        quaternions();
        lottery();
        group = "soundTimeline";
        VerifyChamberSounds.main(new String[0]);
        soundClock();
        StringBuilder report = new StringBuilder("{\n  \"status\": \"PASS\",\n  \"scope\": \"headless contracts; game rendering and transactions require in-game acceptance\",\n  \"checks\": {");
        int index = 0;
        for (var entry : checks.entrySet()) {
            report.append(index++ == 0 ? '\n' : ",\n").append("    \"").append(entry.getKey())
                    .append("\": ").append(entry.getValue());
        }
        report.append("\n  }\n}\n");
        if (args.length > 0) {
            Path output = Path.of(args[0]);
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, report, StandardCharsets.UTF_8);
        }
        System.out.print(report);
    }

    private static void clock() {
        group = "authoritativeClock";
        check(ScrollRevealMotion.SERVER_DURATION_TICKS == 161, "paid research lasts 161 server ticks");
        close(ScrollRevealMotion.logicalTick(0), 0, 0, "start");
        close(ScrollRevealMotion.logicalTick(161), 132, 0, "transaction and presentation share an endpoint");
        close(ScrollRevealMotion.logicalTick(-20), 0, 0, "negative clock clamps");
        close(ScrollRevealMotion.logicalTick(Double.NaN), 0, 0, "invalid clock clamps");
        close(ScrollRevealMotion.logicalTick(200), 132, 0, "complete clock clamps");
        double previous = -1;
        for (int frame = 0; frame <= 161 * 16; frame++) {
            double gameTick = frame / 16.0, logical = ScrollRevealMotion.logicalTick(gameTick);
            check(logical >= previous && logical <= 132, "clock is monotone and bounded");
            close(logical / 132, gameTick / 161, 2e-16, "uniform server rounding");
            previous = logical;
        }
        check(ScrollRevealMotion.WORK_END < ScrollRevealMotion.COMPLETION_START
                && ScrollRevealMotion.COMPLETION_START < 132, "completion follows work and precedes unlock");
    }

    private static void idleAndReturn() {
        group = "idleAndC2Return";
        double h = 1e-4;
        for (double t = 0; t < 9.6; t += .037) {
            for (double phase : new double[]{0, ChamberItemMotion.SECONDARY_PHASE}) {
                var value = ChamberItemMotion.idle(t, phase);
                var before = ChamberItemMotion.idle(t - h, phase);
                var after = ChamberItemMotion.idle(t + h, phase);
                check(Math.abs(value.y()) <= .00800000001, "idle stays within eight milliblocks");
                close(value.y(), ChamberItemMotion.idle(t + 4.8, phase).y(), 2e-16, "idle period");
                close((after.y() - before.y()) / (2 * h), value.vy(), 4e-11, "analytic idle velocity");
                close((after.vy() - before.vy()) / (2 * h), value.ay(), 5e-11, "analytic idle acceleration");
            }
        }
        for (boolean operation : new boolean[]{false, true}) {
            for (boolean secondary : new boolean[]{false, true}) {
                var work = ChamberItemMotion.processing(3.5, operation, secondary);
                var start = ChamberItemMotion.completed(0, 13.25, operation, secondary);
                same(start, work, 2e-14, "completion matches all three work endpoint derivatives");
                double endTime = 13.25 + 1.2;
                var idle = ChamberItemMotion.idle(endTime, secondary ? 1.1 : 0).offset(1.12);
                var end = ChamberItemMotion.completed(1.2, endTime, operation, secondary);
                same(end, idle, 2e-14, "completion matches idle endpoint");
                var before = ChamberItemMotion.completed(1.2 - 1e-6, endTime - 1e-6, operation, secondary);
                close(before.y(), end.y() - end.vy() * 1e-6, 2e-12, "no return position snap");
                close(before.vy(), end.vy() - end.ay() * 1e-6, 2e-10, "no return velocity snap");
                close(before.ay(), end.ay(), 2e-5, "return acceleration is continuous");
                for (double t = 0; t < 2; t += .013) {
                    var value = ChamberItemMotion.completed(t, 13.25 + t, operation, secondary);
                    var a = ChamberItemMotion.completed(t + h, 13.25 + t + h, operation, secondary);
                    var b = ChamberItemMotion.completed(t + 2 * h, 13.25 + t + 2 * h, operation, secondary);
                    check(value.y() > 1 && value.y() < 1.45, "return height remains near chamber");
                    close((-3 * value.y() + 4 * a.y() - b.y()) / (2 * h), value.vy(), 4e-7,
                            "return velocity matches sampled motion");
                }
            }
        }
        var angularStart = ChamberItemMotion.completionRotation(0);
        close(angularStart.y(), Math.toRadians(350), 2e-15, "angular entry position");
        close(angularStart.vy(), Math.toRadians(100), 2e-15, "angular entry velocity");
        var angularEnd = ChamberItemMotion.completionRotation(1.2);
        close(angularEnd.y(), 2 * Math.PI, 0, "angular rest position");
        close(angularEnd.vy(), 0, 0, "angular rest velocity");
        close(angularEnd.ay(), 0, 0, "angular rest acceleration");
        double maximum = 0;
        for (double t = 0; t <= 1.2; t += .001) maximum = Math.max(maximum, ChamberItemMotion.completionRotation(t).y());
        check(maximum > Math.toRadians(370) && maximum < Math.toRadians(380), "small inertial overshoot without another full spin");
        // Exercise the interpolation primitive with unrelated endpoints, rather than
        // comparing a second copy of its polynomial coefficients.
        var a = new ChamberItemMotion.Kinematic(-3.2, .42, -.7);
        var b = new ChamberItemMotion.Kinematic(2.1, -.2, 1.3);
        same(ChamberItemMotion.transition(0, 2.7, a, b), a, 2e-14, "general Hermite start");
        same(ChamberItemMotion.transition(2.7, 2.7, a, b), b, 3e-14, "general Hermite end");
    }

    private static void reveal() {
        group = "revealEnvelope";
        double previousMerge = -1, previousReturn = -1;
        for (double t = 0; t <= 132; t += .125) {
            var value = ScrollRevealMotion.sample(t);
            check(Double.isFinite(value.mainY()) && Double.isFinite(value.secondaryY()), "finite item heights");
            check(value.mainY() >= 1.11 && value.mainY() < 1.48, "approved lift stays near the chamber");
            check(value.imprintAlpha() >= 0 && value.imprintAlpha() <= 1, "imprint alpha bounded");
            check(value.mergeProgress() >= previousMerge - .05 && value.mergeProgress() <= 1, "bounded convergence with small inertia");
            check(value.returnProgress() >= previousReturn && value.returnProgress() <= 1, "final descent is monotone");
            close(value.mainX(), -value.secondaryX(), 0, "pair approaches the same center");
            previousMerge = value.mergeProgress(); previousReturn = value.returnProgress();
        }
        var rest = ScrollRevealMotion.sample(132);
        close(rest.mainX(), 0, 0, "no final horizontal residue");
        close(rest.mainY(), 1.12, 2e-15, "repeat upgrade idle height");
        close(rest.imprintAlpha(), 0, 0, "glow fully fades");
        close(rest.angularPitch(), 0, 0, "pitch settles");
        close(rest.angularYaw(), 0, 0, "yaw settles");
        close(rest.angularRoll(), 0, 0, "roll settles");
        for (double clock : new double[]{0, .3, 12, 471.2}) {
            close(ScrollRevealMotion.sample(132, clock).mainY(), 1.12 + ChamberItemMotion.idle(clock).y(),
                    2e-15, "finished reveal rejoins continuous idle clock");
        }
        // The late lowering is C2 at its endpoints; compare one-sided differences.
        for (double boundary : new double[]{80, 90, 96, 132}) {
            double h = 1e-3;
            double m2 = ScrollRevealMotion.sample(boundary - 2 * h).mainY();
            double m1 = ScrollRevealMotion.sample(boundary - h).mainY();
            double at = ScrollRevealMotion.sample(boundary).mainY();
            double p1 = ScrollRevealMotion.sample(boundary + h).mainY();
            double p2 = ScrollRevealMotion.sample(boundary + 2 * h).mainY();
            close((3 * at - 4 * m1 + m2) / (2 * h), (-3 * at + 4 * p1 - p2) / (2 * h),
                    2e-9, "no late vertical velocity discontinuity at " + boundary);
            close((at - 2 * m1 + m2) / (h * h), (p2 - 2 * p1 + at) / (h * h),
                    2e-6, "no late vertical acceleration discontinuity at " + boundary);
        }
    }

    private static void quaternions() {
        group = "quaternionContinuity";
        var targets = new ScrollRevealMotion.Quaternion[]{ScrollRevealMotion.Quaternion.IDENTITY,
                ScrollRevealMotion.Quaternion.eulerXYZ(.24, Math.PI + .46, 0),
                ScrollRevealMotion.Quaternion.eulerXYZ(-.3, -1.2, .12),
                ScrollRevealMotion.Quaternion.eulerXYZ(.6, 2.9, -.2)};
        for (var target : targets) {
            ScrollRevealMotion.Quaternion previous = null;
            for (double t = 0; t <= 132; t += .125) {
                var pose = ScrollRevealMotion.pose(ScrollRevealMotion.sample(t), target);
                close(pose.dot(pose), 1, 6e-15, "unit reveal quaternion");
                if (previous != null) check(Math.abs(previous.dot(pose)) > .999, "no instantaneous reveal flip");
                previous = pose;
            }
            close(Math.abs(previous.dot(target)), 1, 5e-15, "reveal reaches camera-facing rest");
            for (int slot = 0; slot < 2; slot++) {
                var stable = ChamberItemMotion.initialCompletionTarget(slot, target);
                previous = null;
                for (double seconds = 0; seconds <= 1.5; seconds += .0025) {
                    var pose = ChamberItemMotion.itemQuaternion(ChamberItemMotion.pose(seconds, 15 + seconds, true, true), slot, stable);
                    close(pose.dot(pose), 1, 8e-15, "unit repeated-upgrade quaternion");
                    if (previous != null) check(previous.dot(pose) > .999, "stable quaternion hemisphere during return");
                    previous = pose;
                }
                close(Math.abs(previous.dot(target)), 1, 5e-15, "native item reaches idle heading");
            }
        }
    }

    private static void lottery() {
        group = "researchLottery";
        for (boolean crimson : new boolean[]{false, true}) for (boolean headwear : new boolean[]{false, true}) {
            int[] expected = crimson ? new int[]{6500, 2200, 800, 400, 100} : new int[]{7000, 2000, 700, 250, 50};
            if (headwear) { expected[0] -= 500; expected[1] += 300; expected[2] += 200; }
            int[] actual = new int[5];
            for (int ticket = 0; ticket < 10_000; ticket++) actual[ResearchLottery.rollTier(ticket, crimson, headwear)]++;
            check(Arrays.equals(actual, expected), "exhaustive ticket distribution");
            int[] detached = ResearchLottery.weights(crimson, headwear); detached[0] = -100;
            check(Arrays.equals(ResearchLottery.weights(crimson, headwear), expected), "weights cannot mutate future draws");
        }
        for (int mask = 0; mask < 32; mask++) {
            boolean[] pools = new boolean[5]; for (int tier = 0; tier < 5; tier++) pools[tier] = (mask & 1 << tier) != 0;
            for (int drawn = 0; drawn < 5; drawn++) {
                int result = ResearchLottery.fallbackTier(drawn, pools);
                if (!pools[0]) check(result == -1, "empty common pool is invalid");
                else {
                    check(result >= 0 && result <= drawn && pools[result], "fallback uses an existing lower or equal tier");
                    for (int tier = result + 1; tier <= drawn; tier++) check(!pools[tier], "fallback is the closest lower tier");
                }
            }
        }
        for (int invalid : new int[]{-1, 10_000, Integer.MAX_VALUE}) {
            boolean rejected = false;
            try { ResearchLottery.rollTier(invalid, false, false); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "out-of-range lottery ticket rejected");
        }
    }

    private static void soundClock() {
        group = "soundTimeline";
        for (boolean crimson : new boolean[]{false, true}) for (int rarity = 0; rarity < 5; rarity++) {
            var cursor = new ChamberResearchSoundPlan.Cursor(crimson);
            int beacon = 0, normal = 0;
            for (int gameTick = 0; gameTick <= 161; gameTick++) {
                double logical = ScrollRevealMotion.logicalTick(gameTick);
                for (var cue : cursor.update(logical, rarity, true)) {
                    check(cue.tick() <= logical && cue.accepts(rarity), "sound never runs ahead of paid progress");
                    if (cue.event().equals("minecraft:block.beacon.activate")) {
                        beacon++;
                        check(logical >= 76 && logical < 77, "09 starts on the first vortex game tick");
                        check(cue.tick() * 161 / 132 / 20 + cue.duration() < 161 / 20.0,
                                "full beacon tail fits before unlock");
                    }
                    if (cue.event().equals("minecraft:block.enchantment_table.use")) normal++;
                }
            }
            check(beacon == (rarity >= 3 ? 1 : 0) && normal == (rarity < 3 ? 1 : 0),
                    "only one completion sound family for each tier");
        }
    }

    private static void same(ChamberItemMotion.Kinematic a, ChamberItemMotion.Kinematic b,
                             double tolerance, String message) {
        close(a.y(), b.y(), tolerance, message + " position");
        close(a.vy(), b.vy(), tolerance, message + " velocity");
        close(a.ay(), b.ay(), tolerance, message + " acceleration");
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": " + actual + " != " + expected + " (tolerance " + tolerance + ")");
    }

    private static void check(boolean condition, String message) {
        checks.merge(group, 1, Integer::sum);
        if (!condition) throw new AssertionError(group + ": " + message);
    }
}
