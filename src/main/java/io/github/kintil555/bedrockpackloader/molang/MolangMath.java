package io.github.kintil555.bedrockpackloader.molang;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/** The {@code math.*} function library. Trigonometry works in degrees, as in Bedrock. */
final class MolangMath {
    @FunctionalInterface
    interface Fn {
        double apply(double[] a, Random random);
    }

    private static final Map<String, Fn> FUNCTIONS = new HashMap<>();

    private MolangMath() {
    }

    static Fn find(String name) {
        return FUNCTIONS.get(name);
    }

    private static double arg(double[] a, int i) {
        return i < a.length ? a[i] : 0.0;
    }

    private static void reg(String name, Fn fn) {
        FUNCTIONS.put(name, fn);
    }

    private static void unary(String name, DoubleUnaryOperator op) {
        reg(name, (a, r) -> op.applyAsDouble(arg(a, 0)));
    }

    private static double wrapDegrees(double v) {
        double r = v % 360.0;
        if (r >= 180.0) {
            r -= 360.0;
        } else if (r < -180.0) {
            r += 360.0;
        }
        return r;
    }

    private static void easing(String family, DoubleUnaryOperator in, DoubleUnaryOperator out, DoubleUnaryOperator inOut) {
        reg("ease_in_" + family, (a, r) -> arg(a, 0) + (arg(a, 1) - arg(a, 0)) * in.applyAsDouble(arg(a, 2)));
        reg("ease_out_" + family, (a, r) -> arg(a, 0) + (arg(a, 1) - arg(a, 0)) * out.applyAsDouble(arg(a, 2)));
        reg("ease_in_out_" + family, (a, r) -> arg(a, 0) + (arg(a, 1) - arg(a, 0)) * inOut.applyAsDouble(arg(a, 2)));
    }

    private static void polynomial(String family, int power) {
        easing(family,
                t -> Math.pow(t, power),
                t -> 1 - Math.pow(1 - t, power),
                t -> t < 0.5 ? Math.pow(2, power - 1) * Math.pow(t, power) : 1 - Math.pow(-2 * t + 2, power) / 2);
    }

    private static double bounceOut(double t) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            t -= 1.5 / d1;
            return n1 * t * t + 0.75;
        } else if (t < 2.5 / d1) {
            t -= 2.25 / d1;
            return n1 * t * t + 0.9375;
        }
        t -= 2.625 / d1;
        return n1 * t * t + 0.984375;
    }

    static {
        unary("abs", Math::abs);
        unary("ceil", Math::ceil);
        unary("floor", Math::floor);
        unary("trunc", v -> v < 0 ? Math.ceil(v) : Math.floor(v));
        unary("round", v -> Math.floor(v + 0.5));
        unary("sqrt", Math::sqrt);
        unary("exp", Math::exp);
        unary("ln", Math::log);
        unary("sign", Math::signum);
        unary("sin", v -> Math.sin(Math.toRadians(v)));
        unary("cos", v -> Math.cos(Math.toRadians(v)));
        unary("asin", v -> Math.toDegrees(Math.asin(v)));
        unary("acos", v -> Math.toDegrees(Math.acos(v)));
        unary("atan", v -> Math.toDegrees(Math.atan(v)));
        unary("min_angle", MolangMath::wrapDegrees);
        unary("hermite_blend", t -> 3 * t * t - 2 * t * t * t);
        reg("atan2", (a, r) -> Math.toDegrees(Math.atan2(arg(a, 0), arg(a, 1))));
        reg("pow", (a, r) -> Math.pow(arg(a, 0), arg(a, 1)));
        reg("mod", (a, r) -> arg(a, 1) == 0 ? 0 : arg(a, 0) % arg(a, 1));
        reg("min", (a, r) -> Math.min(arg(a, 0), arg(a, 1)));
        reg("max", (a, r) -> Math.max(arg(a, 0), arg(a, 1)));
        reg("clamp", (a, r) -> Math.min(Math.max(arg(a, 0), arg(a, 1)), arg(a, 2)));
        reg("copy_sign", (a, r) -> Math.copySign(Math.abs(arg(a, 0)), arg(a, 1)));
        reg("lerp", (a, r) -> arg(a, 0) + (arg(a, 1) - arg(a, 0)) * arg(a, 2));
        reg("lerprotate", (a, r) -> arg(a, 0) + wrapDegrees(arg(a, 1) - arg(a, 0)) * arg(a, 2));
        reg("random", (a, r) -> arg(a, 0) + (arg(a, 1) - arg(a, 0)) * r.nextDouble());
        reg("random_integer", (a, r) -> {
            long lo = (long) Math.floor(Math.min(arg(a, 0), arg(a, 1)));
            long hi = (long) Math.floor(Math.max(arg(a, 0), arg(a, 1)));
            return lo + (long) Math.floor(r.nextDouble() * (hi - lo + 1));
        });
        reg("die_roll", (a, r) -> {
            double sum = 0;
            for (int i = 0, n = (int) Math.min(arg(a, 0), 1024); i < n; i++) {
                sum += arg(a, 1) + (arg(a, 2) - arg(a, 1)) * r.nextDouble();
            }
            return sum;
        });
        reg("die_roll_integer", (a, r) -> {
            double sum = 0;
            long lo = (long) Math.floor(Math.min(arg(a, 1), arg(a, 2)));
            long hi = (long) Math.floor(Math.max(arg(a, 1), arg(a, 2)));
            for (int i = 0, n = (int) Math.min(arg(a, 0), 1024); i < n; i++) {
                sum += lo + (long) Math.floor(r.nextDouble() * (hi - lo + 1));
            }
            return sum;
        });

        easing("sine", t -> 1 - Math.cos(t * Math.PI / 2), t -> Math.sin(t * Math.PI / 2), t -> -(Math.cos(Math.PI * t) - 1) / 2);
        polynomial("quad", 2);
        polynomial("cubic", 3);
        polynomial("quart", 4);
        polynomial("quint", 5);
        easing("expo",
                t -> t == 0 ? 0 : Math.pow(2, 10 * t - 10),
                t -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t),
                t -> t == 0 ? 0 : t == 1 ? 1 : t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2);
        easing("circ",
                t -> 1 - Math.sqrt(1 - t * t),
                t -> Math.sqrt(1 - (t - 1) * (t - 1)),
                t -> t < 0.5 ? (1 - Math.sqrt(1 - 4 * t * t)) / 2 : (Math.sqrt(1 - (-2 * t + 2) * (-2 * t + 2)) + 1) / 2);
        double c1 = 1.70158;
        double c2 = c1 * 1.525;
        double c3 = c1 + 1;
        easing("back",
                t -> c3 * t * t * t - c1 * t * t,
                t -> 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2),
                t -> t < 0.5
                        ? (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                        : (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2);
        double c4 = 2 * Math.PI / 3;
        double c5 = 2 * Math.PI / 4.5;
        easing("elastic",
                t -> t == 0 ? 0 : t == 1 ? 1 : -Math.pow(2, 10 * t - 10) * Math.sin((10 * t - 10.75) * c4),
                t -> t == 0 ? 0 : t == 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((10 * t - 0.75) * c4) + 1,
                t -> t == 0 ? 0 : t == 1 ? 1 : t < 0.5
                        ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c5)) / 2
                        : Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c5) / 2 + 1);
        easing("bounce",
                t -> 1 - bounceOut(1 - t),
                MolangMath::bounceOut,
                t -> t < 0.5 ? (1 - bounceOut(1 - 2 * t)) / 2 : (1 + bounceOut(2 * t - 1)) / 2);
    }
}
