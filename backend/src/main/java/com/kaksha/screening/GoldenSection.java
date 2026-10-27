package com.kaksha.screening;

import java.util.function.DoubleUnaryOperator;

/**
 * Golden-section search: finds the lowest point of a function that has a single dip
 * inside the search window, without needing its derivative.
 *
 * Each step looks at two inner points and throws away the outer piece that cannot hold the
 * minimum. The points are placed at the golden ratio so one of them can be reused in the
 * next step, which means only one new function evaluation per step.
 */
public final class GoldenSection {

    private static final double INVERSE_GOLDEN_RATIO = (Math.sqrt(5) - 1) / 2; // about 0.618

    private GoldenSection() {
    }

    /**
     * @param f         the function to minimise
     * @param low       start of the search window
     * @param high      end of the search window
     * @param tolerance stop when the window is narrower than this
     * @return the position of the minimum
     */
    public static double minimize(DoubleUnaryOperator f, double low, double high, double tolerance) {
        double c = high - INVERSE_GOLDEN_RATIO * (high - low);
        double d = low + INVERSE_GOLDEN_RATIO * (high - low);
        double fc = f.applyAsDouble(c);
        double fd = f.applyAsDouble(d);

        while (high - low > tolerance) {
            if (fc < fd) {
                // The minimum is in [low, d]. Old c becomes the new d.
                high = d;
                d = c;
                fd = fc;
                c = high - INVERSE_GOLDEN_RATIO * (high - low);
                fc = f.applyAsDouble(c);
            } else {
                // The minimum is in [c, high]. Old d becomes the new c.
                low = c;
                c = d;
                fc = fd;
                d = low + INVERSE_GOLDEN_RATIO * (high - low);
                fd = f.applyAsDouble(d);
            }
        }
        return (low + high) / 2;
    }
}
