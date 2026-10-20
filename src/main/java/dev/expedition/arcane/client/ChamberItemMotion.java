package dev.expedition.arcane.client;

/** Native 3.5-second work motion and the approved C2 return to repeated-upgrade idle. */
public final class ChamberItemMotion {
    public static final double IDLE_AMPLITUDE = .035;
    public static final double IDLE_PERIOD_SECONDS = 5.6;
    public static final double COMPLETION_SETTLE_SECONDS = 1.2;
    public static final double BASE_HEIGHT = 1.52;
    public static final double WORK_HEIGHT = BASE_HEIGHT + .16;
    public static final double BOOK_BASE_HEIGHT = 1.34;
    public static final double BOOK_WORK_HEIGHT = BOOK_BASE_HEIGHT + .16;
    public static final double BOOK_REST_TILT_DEGREES = 100;
    public static final double BOOK_OPEN_TILT_DEGREES = 10;
    public static final double SLOT_HORIZONTAL = .40;
    /** The cage is one block high; reserve visible air above its highest rim. */
    public static final double CLEARANCE_FLOOR = 1.06;
    public static final double WORK_SECONDS = 3.5;
    public static final double MAIN_PHASE = 0;
    public static final double SECONDARY_PHASE = 1.1;
    public static final double ANGULAR_RATE = 100 * Math.PI / 180;
    private static final double OMEGA = 2 * Math.PI / IDLE_PERIOD_SECONDS;

    private ChamberItemMotion() {}

    public static Kinematic idle(double seconds) { return idle(seconds, MAIN_PHASE); }

    /** Shared by item rendering and server-validated picking. */
    public static double idleHeight(double seconds, boolean secondary) {
        return idleHeight(seconds, secondary, false);
    }

    public static double idleHeight(double seconds, boolean secondary, boolean book) {
        return (book ? BOOK_BASE_HEIGHT : BASE_HEIGHT)
                + idle(seconds, secondary ? SECONDARY_PHASE : MAIN_PHASE).y;
    }

    /** The player's near edge dips ten degrees as the articulated book opens. */
    public static ScrollRevealMotion.Quaternion bookOrientation(double yaw, double open, double impact) {
        double tilt = BOOK_REST_TILT_DEGREES + BOOK_OPEN_TILT_DEGREES * ScrollRevealMotion.ease(open)
                + impact * .7;
        return ScrollRevealMotion.Quaternion.eulerXYZ(0, yaw + Math.PI / 2, 0)
                .multiply(ScrollRevealMotion.Quaternion.eulerXYZ(0, 0, Math.toRadians(tilt)));
    }

    /** Resource-pack meshes may extend beyond a vanilla sprite's nominal box. */
    public static double clearHeight(double proposed, double localLowestPoint) {
        return Math.max(proposed, CLEARANCE_FLOOR - localLowestPoint);
    }

    /** Offset from the original item height, with analytic first and second derivatives. */
    public static Kinematic idle(double seconds, double phase) {
        double angle = OMEGA * seconds + phase;
        return new Kinematic(IDLE_AMPLITUDE * Math.sin(angle),
                IDLE_AMPLITUDE * OMEGA * Math.cos(angle),
                -IDLE_AMPLITUDE * OMEGA * OMEGA * Math.sin(angle));
    }

    public static Kinematic processing(double seconds, boolean operation, boolean secondary) {
        return processing(seconds, BASE_HEIGHT, WORK_HEIGHT, operation, secondary);
    }

    /** Carry the existing absolute idle phase into the work lift, without a placement jump. */
    public static Kinematic processing(double seconds, double absoluteSeconds, boolean operation, boolean secondary) {
        return processing(seconds, absoluteSeconds, operation, secondary, false);
    }

    public static Kinematic processing(double seconds, double absoluteSeconds, boolean operation,
                                       boolean secondary, boolean book) {
        Kinematic work = processing(seconds, book ? BOOK_BASE_HEIGHT : BASE_HEIGHT,
                book ? BOOK_WORK_HEIGHT : WORK_HEIGHT, operation, secondary);
        double u = Math.max(0, Math.min(1, seconds / .75));
        double gate = 1 - ScrollRevealMotion.smooth5(u);
        double gateV = seconds > 0 && seconds < .75 ? -30 * u * u * (u - 1) * (u - 1) / .75 : 0;
        double gateA = seconds > 0 && seconds < .75 ? -60 * u * (2 * u * u - 3 * u + 1) / (.75 * .75) : 0;
        Kinematic idle = idle(absoluteSeconds, secondary ? SECONDARY_PHASE : MAIN_PHASE);
        return new Kinematic(work.y + idle.y * gate, work.vy + idle.vy * gate + idle.y * gateV,
                work.ay + idle.ay * gate + 2 * idle.vy * gateV + idle.y * gateA);
    }

    public static Kinematic processing(double seconds, double base, double working,
                                       boolean operation, boolean secondary) {
        double u = Math.max(0, Math.min(1, seconds / .75)), lift = u * u * (3 - 2 * u);
        boolean moving = seconds > 0 && seconds < .75;
        double dl = moving ? 6 * u * (1 - u) / .75 : 0;
        double ddl = moving ? (6 - 12 * u) / (.75 * .75) : 0;
        double frequency = secondary || operation ? 1 : 2;
        double phase = secondary ? Math.PI : 0, gain = secondary || operation ? .08 : .04;
        double angle = seconds * frequency + phase, height = working - base + gain * Math.sin(angle);
        return new Kinematic(base + height * lift,
                height * dl + gain * frequency * Math.cos(angle) * lift,
                height * ddl + 2 * gain * frequency * Math.cos(angle) * dl
                        - gain * frequency * frequency * Math.sin(angle) * lift);
    }

    public static Kinematic completed(double seconds, double absoluteSeconds, boolean operation, boolean secondary) {
        return completed(seconds, absoluteSeconds, operation, secondary, false);
    }

    public static Kinematic completed(double seconds, double absoluteSeconds, boolean operation,
                                      boolean secondary, boolean book) {
        return completed(seconds, absoluteSeconds, book ? BOOK_BASE_HEIGHT : BASE_HEIGHT,
                book ? BOOK_WORK_HEIGHT : WORK_HEIGHT, WORK_SECONDS,
                COMPLETION_SETTLE_SECONDS, operation, secondary);
    }

    /**
     * Match the work endpoint's position, velocity and acceleration to the
     * future idle sine. The absolute clock never resets when a recipe ends.
     */
    public static Kinematic completed(double seconds, double absoluteSeconds, double base, double working,
                                      double workSeconds, double settleSeconds,
                                      boolean operation, boolean secondary) {
        double phase = secondary ? SECONDARY_PHASE : MAIN_PHASE;
        if (seconds >= settleSeconds) return idle(absoluteSeconds, phase).offset(base);
        Kinematic start = processing(workSeconds, base, working, operation, secondary);
        Kinematic end = idle(absoluteSeconds - seconds + settleSeconds, phase).offset(base);
        return transition(Math.max(0, seconds), settleSeconds, start, end);
    }

    /** Quintic Hermite interpolation, evaluated without an incremental integrator. */
    public static Kinematic transition(double seconds, double duration, Kinematic start, Kinematic end) {
        if (!(duration > 0)) return end;
        double u = Math.max(0, Math.min(1, seconds / duration)), T = duration;
        double c0 = start.y, c1 = start.vy * T, c2 = start.ay * T * T / 2;
        double delta = end.y - c0 - c1 - c2;
        double velocity = end.vy * T - c1 - 2 * c2;
        double acceleration = end.ay * T * T - 2 * c2;
        double c3 = 10 * delta - 4 * velocity + acceleration / 2;
        double c4 = -15 * delta + 7 * velocity - acceleration, c5 = 6 * delta - 3 * velocity + acceleration / 2;
        return new Kinematic(c0 + u * (c1 + u * (c2 + u * (c3 + u * (c4 + u * c5)))),
                (c1 + u * (2 * c2 + u * (3 * c3 + u * (4 * c4 + u * 5 * c5)))) / T,
                (2 * c2 + u * (6 * c3 + u * (12 * c4 + u * 20 * c5))) / (T * T));
    }

    /** 350 degrees and 100 degrees/second gently pass 360, then settle at 360. */
    public static Kinematic completionRotation(double seconds) {
        if (seconds >= COMPLETION_SETTLE_SECONDS - 1e-12) return new Kinematic(2 * Math.PI, 0, 0);
        return transition(Math.max(0, seconds), COMPLETION_SETTLE_SECONDS,
                new Kinematic(WORK_SECONDS * ANGULAR_RATE, ANGULAR_RATE, 0),
                new Kinematic(2 * Math.PI, 0, 0));
    }

    public static Pose pose(double phaseSeconds, double absoluteSeconds, boolean completed, boolean operation) {
        return pose(phaseSeconds, absoluteSeconds, completed, operation, false);
    }

    public static Pose pose(double phaseSeconds, double absoluteSeconds, boolean completed, boolean operation, boolean book) {
        Kinematic vertical = completed ? completed(phaseSeconds, absoluteSeconds, operation, false, book)
                : processing(phaseSeconds, absoluteSeconds, operation, false, book);
        Kinematic angular = completed ? completionRotation(phaseSeconds)
                : new Kinematic(phaseSeconds * ANGULAR_RATE, ANGULAR_RATE, 0);
        return new Pose(vertical, angular, completed,
                completed && phaseSeconds >= COMPLETION_SETTLE_SECONDS - 1e-12,
                completed ? ScrollRevealMotion.smooth5(phaseSeconds / COMPLETION_SETTLE_SECONDS) : 0);
    }

    /** Match the native X/Z turn to a caller-tracked, sign-continuous camera target. */
    public static ScrollRevealMotion.Quaternion itemQuaternion(Pose pose, int slot,
                                                               ScrollRevealMotion.Quaternion stableRest) {
        if (pose.settled) return stableRest.normalized();
        ScrollRevealMotion.Quaternion work = ScrollRevealMotion.nativePose(pose.angular.y + slot * Math.PI);
        if (!pose.completed) return work;
        // Match the native preview's near-aligned normalized linear branch.
        if (work.dot(stableRest) >= .9995) {
            double t = pose.settleBlend, a = 1 - t;
            return new ScrollRevealMotion.Quaternion(work.x() * a + stableRest.x() * t,
                    work.y() * a + stableRest.y() * t, work.z() * a + stableRest.z() * t,
                    work.w() * a + stableRest.w() * t).normalized();
        }
        return work.interpolate(stableRest, pose.settleBlend);
    }

    public static ScrollRevealMotion.Quaternion initialCompletionTarget(int slot,
                                                                        ScrollRevealMotion.Quaternion camera) {
        return camera.normalized().hemisphere(ScrollRevealMotion.nativePose(WORK_SECONDS * ANGULAR_RATE + slot * Math.PI));
    }

    public record Kinematic(double y, double vy, double ay) {
        public Kinematic offset(double offset) { return new Kinematic(y + offset, vy, ay); }
    }
    public record Pose(Kinematic vertical, Kinematic angular, boolean completed,
                       boolean settled, double settleBlend) {}
}
