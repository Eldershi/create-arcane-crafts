package dev.expedition.arcane.client;

/**
 * Item-local math from the approved WebGL reveal (revisions 84, 89 and 90).
 * Logical ticks deliberately run at 112 / 6.8 per second, not at the game tick rate.
 * This class has no renderer, world, texture or audio dependencies.
 */
public final class ScrollRevealMotion {
    public static final double DURATION_TICKS = 132;
    public static final double SECONDS_PER_TICK = 6.8 / 112;
    public static final double GAME_TICKS_PER_LOGICAL_TICK = SECONDS_PER_TICK * 20;
    public static final double DURATION_SECONDS = DURATION_TICKS * SECONDS_PER_TICK;
    public static final int SERVER_DURATION_TICKS = (int) Math.ceil(DURATION_SECONDS * 20);
    public static final double GAME_DURATION_SECONDS = SERVER_DURATION_TICKS / 20.0;
    public static final double COMPLETION_START = 76;
    public static final double WORK_END = 58;
    public static final double OUTPUT_START = 58;
    public static final double SECONDARY_END = 44;
    public static final double IDLE_HEIGHT = ChamberItemMotion.BASE_HEIGHT;
    private static final double APPROVED_IDLE_HEIGHT = 1.12;
    private static final double DISPLAY_LIFT = IDLE_HEIGHT - APPROVED_IDLE_HEIGHT;
    public static final double EFFECT_RIG_YAW = Math.PI + .46;
    private static final double HOVER_ANCHOR_Y = 1.344;
    private static final double HOVER_REFERENCE_TICK = 4.55 / 6.8 * 112;
    private static final double MICRO_HOVER_AMPLITUDE = .008;
    private static final double MICRO_HOVER_PERIOD = 60;
    private static final double HOLD_START = 68, HOLD_END = 80;
    private static final double ORIENTATION_START = 8, ORIENTATION_END = 28;
    private static final double[] HOLD_POINTS = holdPoints();
    private static final Quaternion Y_TURN = Quaternion.eulerXYZ(0, Math.PI, 0);
    private static final Quaternion TURN_START = nativePose(rawSample(ORIENTATION_START).mainAngle);
    private static final Quaternion TURN_CONTROL = turnControl();

    private ScrollRevealMotion() {}

    /** Converts server-owned paid work ticks, including a render interpolation, to this clock. */
    public static double logicalTick(double paidGameTicks) {
        // The integer server clock ends at 161 ticks (8.05 seconds). Distribute
        // the 35.7 ms rounding uniformly so transaction and presentation agree.
        return Math.max(0, Math.min(DURATION_TICKS, finite(paidGameTicks) * DURATION_TICKS / SERVER_DURATION_TICKS));
    }

    /** Includes the final 90..132 tick return to the original height; excludes the idle sine. */
    public static Sample sample(double logicalTick) {
        Sample raw = rawSample(logicalTick);
        double progress = smooth5((raw.tick - 90) / 42);
        double correction = (APPROVED_IDLE_HEIGHT - HOVER_ANCHOR_Y) * progress;
        return raw.withPosition(raw.mainY + DISPLAY_LIFT + correction,
                raw.secondaryY + DISPLAY_LIFT + correction, progress);
    }

    /** Adds the same continuous, item-only idle sine used before and after repeated upgrades. */
    public static Sample sample(double logicalTick, double idleSeconds) {
        Sample value = sample(logicalTick);
        double gate = 1 - ease(value.tick / 12) + ease((value.tick - 88) / 22);
        return value.withPosition(value.mainY + ChamberItemMotion.idle(finite(idleSeconds)).y() * gate,
                value.secondaryY + ChamberItemMotion.idle(finite(idleSeconds),
                        ChamberItemMotion.SECONDARY_PHASE).y() * gate, value.returnProgress);
    }

    /**
     * A paid transaction cannot run backwards. Packet/time corrections may lower
     * a raw interpolated sample, but must never re-show a faded imprint or burst.
     * The owner creates a new cursor only when the immutable transaction changes.
     */
    public static final class PresentationClock {
        private double shown;
        private boolean complete;

        public double sample(double candidate, boolean completed) {
            complete |= completed;
            shown = complete ? DURATION_TICKS : Math.max(shown,
                    Math.max(0, Math.min(DURATION_TICKS, finite(candidate))));
            return shown;
        }
    }

    /**
     * The native XY turn settles through a spherical cubic Bezier; whole-item
     * pitch, yaw and roll continue through the arrival and luminous hover.
     * rest is item-local: inverse(rig rotation) * camera rotation * Y(PI).
     */
    public static Quaternion pose(Sample sample, Quaternion rest) {
        double u = clamp(sample.orientationCurveT);
        Quaternion result;
        if (u <= 0) result = nativePose(sample.mainAngle);
        else if (u >= 1) result = rest.normalized();
        else {
            Quaternion end = rest.normalized().hemisphere(TURN_CONTROL);
            Quaternion a = TURN_START.slerp(TURN_CONTROL, u);
            Quaternion b = TURN_CONTROL.slerp(end, u);
            Quaternion c = end.slerp(end, u);
            result = a.slerp(b, u).slerp(b.slerp(c, u), u).normalized();
        }
        return result.multiply(Quaternion.eulerXYZ(sample.angularPitch, sample.angularYaw,
                sample.angularRoll)).normalized();
    }

    public static Quaternion nativePose(double angle) {
        return Quaternion.eulerXYZ(angle, 0, angle).multiply(Y_TURN).normalized();
    }

    private static Quaternion turnControl() {
        double h = .01;
        Quaternion before = nativePose(rawSample(ORIENTATION_START - h).mainAngle).hemisphere(TURN_START);
        Quaternion after = nativePose(rawSample(ORIENTATION_START + h).mainAngle).hemisphere(TURN_START);
        Quaternion derivative = new Quaternion((after.x - before.x) / (2 * h),
                (after.y - before.y) / (2 * h), (after.z - before.z) / (2 * h),
                (after.w - before.w) / (2 * h));
        Quaternion delta = TURN_START.inverse().multiply(derivative);
        double length = Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        if (length <= 1e-12) return TURN_START;
        double angle = length * (ORIENTATION_END - ORIENTATION_START) / 3;
        double scale = Math.sin(angle) / length;
        return TURN_START.multiply(new Quaternion(delta.x * scale, delta.y * scale,
                delta.z * scale, Math.cos(angle))).normalized().hemisphere(TURN_START);
    }

    private static Sample rawSample(double tick) {
        tick = Math.max(0, finite(tick));
        double seconds = tick / 20, lift = ease(tick / 16);
        double mainSettle = ease((tick - 16) / 62), secondarySettle = ease((tick - 16) / 56);
        double orientation = clamp((tick - ORIENTATION_START) / (ORIENTATION_END - ORIENTATION_START));
        double floatU = clamp((tick - 58) / 38);
        double floatClock = tick <= 58 ? tick : 58 + 38 * floatU - 16 * floatU * floatU - 2 * floatU * floatU * floatU;
        double floatSettle = ease((floatClock - 16) / 62);
        double brake = clamp((tick - 24) / 18);
        double spinTicks = tick <= 24 ? tick : 24 + 18 * (brake - brake * brake + brake * brake * brake / 3);
        double mainWorkY = 1.12 + .30 * lift + .04 * Math.sin(floatClock / 20) * lift;
        double secondaryWorkY = 1.12 + .30 * lift + .04 * Math.sin(seconds + Math.PI) * lift;
        double approach = clamp((tick - 10) / 32), recoil = clamp((tick - 42) / 18);
        double overshoot = 18 * .005625 * recoil * (1 - recoil) * (1 - recoil);
        double mainX = tick < 42 ? -.40 * (1 - ease(approach))
                + 32 * .005625 * (approach * approach * approach - approach * approach) : overshoot;
        double merge = clamp(1 + mainX / .40);
        double inertiaRoll = -Math.PI / 180 * 16 * recoil * recoil * (1 - recoil) * (1 - recoil);
        double legacyY = mainWorkY * (1 - floatSettle) + 1.28 * floatSettle;
        double hoverBlend = ease((tick - 42) / 16), glow = 1 - ease((tick - 74) / 22);
        double hoverU = clamp((tick - 68) / 28);
        double hoverClock = tick <= 68 ? tick : 68 + 28 * (hoverU - hoverU * hoverU * hoverU
                + .5 * hoverU * hoverU * hoverU * hoverU);
        double hoverPhase = (hoverClock - 42) * Math.PI * 2 / 30;
        double hoverY = 1.28 + glow * (.050 + .028 * Math.sin(hoverPhase));
        double previousY = legacyY * (1 - hoverBlend) + hoverY * hoverBlend;
        Kinematic held = heldHover(tick);
        double mainY = tick <= HOLD_START ? previousY : tick < HOLD_END ? holdTransition(tick) : held.y;
        double turnAge = Math.max(0, tick - 26);
        double turnGate = ease(turnAge / 6) * (1 - ease((tick - 64) / 8));
        double angularRecoil = turnGate * Math.exp(-turnAge / 25) * Math.sin(turnAge * Math.PI / 12);
        double swayEnvelope = ease((tick - 54) / 12) * glow;
        double swayPhase = (tick - 42) * Math.PI * 2 / 40 + .5;
        double sidePhase = (tick - 42) * Math.PI * 2 / 52 + .5, radians = Math.PI / 180;
        double pitch = radians * (10 * angularRecoil + 4.2 * swayEnvelope * Math.sin(swayPhase + .9));
        double yaw = radians * (1.8 * angularRecoil + 1.4 * swayEnvelope * Math.sin(sidePhase - .35));
        double roll = inertiaRoll + radians * (6.5 * angularRecoil + 3.1 * swayEnvelope * Math.sin(sidePhase));
        double secondaryHomeY = secondaryWorkY * (1 - secondarySettle) + 1.12 * secondarySettle;
        double secondaryY = secondaryHomeY * (1 - merge) + mainY * merge;
        return new Sample(tick, mainX, mainY, -mainX, secondaryY, merge,
                spinTicks * Math.PI / 36, orientation, pitch, yaw, roll, glow,
                overshoot, inertiaRoll, angularRecoil, swayEnvelope, mainSettle, secondarySettle,
                floatClock, hoverClock, tick >= 96, 0);
    }

    private static Kinematic heldHover(double tick) {
        double u = clamp((tick - 74) / 22), envelope = 1 - smooth5(u);
        double envelopeV = -30 * u * u * (u - 1) * (u - 1) / 22;
        double envelopeA = -60 * u * (2 * u * u - 3 * u + 1) / (22 * 22);
        double omega = Math.PI * 2 / MICRO_HOVER_PERIOD;
        double phase = (tick - HOVER_REFERENCE_TICK) * omega;
        double sine = Math.sin(phase), cosine = Math.cos(phase), amplitude = MICRO_HOVER_AMPLITUDE;
        return new Kinematic(HOVER_ANCHOR_Y + amplitude * envelope * sine,
                amplitude * (envelopeV * sine + envelope * omega * cosine),
                amplitude * (envelopeA * sine + 2 * envelopeV * omega * cosine
                        - envelope * omega * omega * sine));
    }

    private static double[] holdPoints() {
        double span = HOLD_END - HOLD_START, omega = Math.PI * 2 / 30;
        double phase = (HOLD_START - 42) * omega;
        double y = 1.33 + .028 * Math.sin(phase), velocity = .028 * omega * Math.cos(phase);
        double acceleration = -.028 * omega * omega * Math.sin(phase);
        Kinematic end = heldHover(HOLD_END);
        return new double[]{y, y + velocity * span / 5,
                y + 2 * velocity * span / 5 + acceleration * span * span / 20,
                end.y - 2 * end.v * span / 5 + end.a * span * span / 20,
                end.y - end.v * span / 5, end.y};
    }

    private static double holdTransition(double tick) {
        double u = clamp((tick - HOLD_START) / (HOLD_END - HOLD_START)), v = 1 - u;
        return HOLD_POINTS[0] * Math.pow(v, 5) + 5 * HOLD_POINTS[1] * Math.pow(v, 4) * u
                + 10 * HOLD_POINTS[2] * Math.pow(v, 3) * u * u
                + 10 * HOLD_POINTS[3] * v * v * u * u * u
                + 5 * HOLD_POINTS[4] * v * Math.pow(u, 4) + HOLD_POINTS[5] * Math.pow(u, 5);
    }

    public static double ease(double value) {
        double t = clamp(value);
        return t * t * (3 - 2 * t);
    }

    public static double smooth5(double value) {
        double t = clamp(value);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double finite(double value) { return Double.isFinite(value) ? value : 0; }
    private record Kinematic(double y, double v, double a) {}

    public record Sample(double tick, double mainX, double mainY, double secondaryX, double secondaryY,
                         double mergeProgress, double mainAngle, double orientationCurveT,
                         double angularPitch, double angularYaw, double angularRoll, double imprintAlpha,
                         double inertiaOffset, double inertiaRoll, double angularRecoil,
                         double swayEnvelope, double mainSettle, double secondarySettle,
                         double floatClock, double hoverClock, boolean settled, double returnProgress) {
        public Sample withClearance(double mainLowestPoint, double secondaryLowestPoint) {
            return withPosition(ChamberItemMotion.clearHeight(mainY, mainLowestPoint),
                    ChamberItemMotion.clearHeight(secondaryY, secondaryLowestPoint), returnProgress);
        }
        private Sample withPosition(double main, double secondary, double returned) {
            return new Sample(tick, mainX, main, secondaryX, secondary, mergeProgress,
                    mainAngle, orientationCurveT, angularPitch, angularYaw, angularRoll, imprintAlpha,
                    inertiaOffset, inertiaRoll, angularRecoil, swayEnvelope, mainSettle, secondarySettle,
                    floatClock, hoverClock, settled, returned);
        }
    }

    /** Hamilton quaternion using the same multiplication and XYZ Euler order as Three.js. */
    public record Quaternion(double x, double y, double z, double w) {
        public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

        public static Quaternion eulerXYZ(double x, double y, double z) {
            double c1 = Math.cos(x / 2), c2 = Math.cos(y / 2), c3 = Math.cos(z / 2);
            double s1 = Math.sin(x / 2), s2 = Math.sin(y / 2), s3 = Math.sin(z / 2);
            return new Quaternion(s1 * c2 * c3 + c1 * s2 * s3,
                    c1 * s2 * c3 - s1 * c2 * s3, c1 * c2 * s3 + s1 * s2 * c3,
                    c1 * c2 * c3 - s1 * s2 * s3);
        }

        public Quaternion multiply(Quaternion q) {
            return new Quaternion(x * q.w + w * q.x + y * q.z - z * q.y,
                    y * q.w + w * q.y + z * q.x - x * q.z,
                    z * q.w + w * q.z + x * q.y - y * q.x,
                    w * q.w - x * q.x - y * q.y - z * q.z);
        }

        public double dot(Quaternion q) { return x * q.x + y * q.y + z * q.z + w * q.w; }
        public Quaternion negate() { return new Quaternion(-x, -y, -z, -w); }
        public Quaternion hemisphere(Quaternion reference) { return dot(reference) < 0 ? negate() : this; }

        public Quaternion normalized() {
            double length = Math.sqrt(dot(this));
            return length < 1e-15 ? IDENTITY : new Quaternion(x / length, y / length, z / length, w / length);
        }

        public Quaternion inverse() {
            double squaredLength = dot(this);
            return squaredLength < 1e-30 ? IDENTITY
                    : new Quaternion(-x / squaredLength, -y / squaredLength, -z / squaredLength, w / squaredLength);
        }

        public Quaternion slerp(Quaternion target, double t) {
            if (t <= 0) return this;
            if (t >= 1) return target;
            target = target.hemisphere(this);
            return interpolate(target, t);
        }

        /** Keeps the caller's stable hemisphere, for the native completion-to-camera transition. */
        public Quaternion interpolate(Quaternion target, double t) {
            double dot = Math.max(-1, Math.min(1, dot(target)));
            if (dot >= 1) return this;
            double squaredSine = 1 - dot * dot;
            if (squaredSine <= Math.ulp(1.0)) {
                return new Quaternion(x * (1 - t) + target.x * t, y * (1 - t) + target.y * t,
                        z * (1 - t) + target.z * t, w * (1 - t) + target.w * t).normalized();
            }
            double sine = Math.sqrt(squaredSine), angle = Math.atan2(sine, dot);
            double a = Math.sin((1 - t) * angle) / sine, b = Math.sin(t * angle) / sine;
            return new Quaternion(x * a + target.x * b, y * a + target.y * b,
                    z * a + target.z * b, w * a + target.w * b).normalized();
        }
    }
}
