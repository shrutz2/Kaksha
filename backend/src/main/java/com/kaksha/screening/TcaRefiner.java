package com.kaksha.screening;

import java.util.function.DoubleUnaryOperator;

/**
 * Stage 3 of the screening: finds the exact time of closest approach (TCA) near a rough
 * guess from the coarse scan.
 */
public final class TcaRefiner {

    /** @param secondsFromStart time of closest approach, @param missKm distance at that time */
    public record Approach(double secondsFromStart, double missKm) {
    }

    private TcaRefiner() {
    }

    /**
     * @param distanceAt        distance between the two objects, given seconds from the scan start
     * @param windowStartSeconds start of the window known to contain the closest point
     * @param windowEndSeconds   end of that window
     * @param toleranceSeconds   how precisely the time is wanted
     */
    public static Approach refine(DoubleUnaryOperator distanceAt, double windowStartSeconds,
                                  double windowEndSeconds, double toleranceSeconds) {
        double t = GoldenSection.minimize(distanceAt, windowStartSeconds, windowEndSeconds, toleranceSeconds);
        return new Approach(t, distanceAt.applyAsDouble(t));
    }
}
