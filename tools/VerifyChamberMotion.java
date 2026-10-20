import dev.expedition.arcane.client.ChamberItemMotion;
import dev.expedition.arcane.client.ScrollRevealMotion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/** A numeric bridge, fed by verify-chamber-motion.cjs's original WebGL fixtures. */
public final class VerifyChamberMotion {
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
            String row;
            while ((row = input.readLine()) != null) {
                String[] fields = row.split(",");
                switch (fields[0]) {
                    case "scroll" -> {
                        var s = fields.length > 2 ? ScrollRevealMotion.sample(number(fields, 1), number(fields, 2))
                                : ScrollRevealMotion.sample(number(fields, 1));
                        print(s.mainX(), s.mainY(), s.secondaryX(), s.secondaryY(), s.mergeProgress(),
                                s.mainAngle(), s.orientationCurveT(), s.angularPitch(), s.angularYaw(), s.angularRoll(),
                                s.imprintAlpha(), s.inertiaOffset(), s.inertiaRoll(), s.angularRecoil(), s.swayEnvelope(),
                                s.mainSettle(), s.secondarySettle(), s.floatClock(), s.hoverClock(), s.returnProgress());
                    }
                    case "quaternion" -> {
                        var q = ScrollRevealMotion.pose(ScrollRevealMotion.sample(number(fields, 1)),
                                new ScrollRevealMotion.Quaternion(number(fields, 2), number(fields, 3),
                                        number(fields, 4), number(fields, 5)));
                        print(q.x(), q.y(), q.z(), q.w());
                    }
                    case "idle" -> {
                        var p = ChamberItemMotion.idle(number(fields, 1), number(fields, 2));
                        print(p.y(), p.vy(), p.ay());
                    }
                    case "processing" -> {
                        var p = ChamberItemMotion.processing(number(fields, 1), Boolean.parseBoolean(fields[2]),
                                Boolean.parseBoolean(fields[3]));
                        print(p.y(), p.vy(), p.ay());
                    }
                    case "completed" -> {
                        var p = ChamberItemMotion.completed(number(fields, 1), number(fields, 2),
                                Boolean.parseBoolean(fields[3]), Boolean.parseBoolean(fields[4]));
                        print(p.y(), p.vy(), p.ay());
                    }
                    case "rotation" -> {
                        var p = ChamberItemMotion.completionRotation(number(fields, 1));
                        print(p.y(), p.vy(), p.ay());
                    }
                    case "itemQuaternion" -> {
                        int slot = Integer.parseInt(fields[3]);
                        var camera = new ScrollRevealMotion.Quaternion(number(fields, 4), number(fields, 5),
                                number(fields, 6), number(fields, 7));
                        var target = ChamberItemMotion.initialCompletionTarget(slot, camera);
                        var q = ChamberItemMotion.itemQuaternion(
                                ChamberItemMotion.pose(number(fields, 1), number(fields, 2), true, true), slot, target);
                        print(q.x(), q.y(), q.z(), q.w());
                    }
                    default -> throw new IllegalArgumentException(row);
                }
            }
        }
    }

    private static double number(String[] fields, int i) { return Double.parseDouble(fields[i]); }
    private static void print(double... values) {
        StringBuilder row = new StringBuilder();
        for (double value : values) {
            if (row.length() > 0) row.append(',');
            row.append(Double.toString(value));
        }
        System.out.println(row);
    }
}
