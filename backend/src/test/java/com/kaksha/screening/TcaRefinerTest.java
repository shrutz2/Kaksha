package com.kaksha.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TcaRefinerTest {

    @Test
    void goldenSectionFindsTheBottomOfAParabola() {
        double x = GoldenSection.minimize(t -> (t - 3.0) * (t - 3.0) + 1.0, -10, 10, 1e-6);

        assertEquals(3.0, x, 1e-5);
    }

    @Test
    void goldenSectionWorksWhenTheMinimumIsNearTheEdge() {
        double x = GoldenSection.minimize(t -> Math.abs(t - 0.2), 0, 100, 1e-6);

        assertEquals(0.2, x, 1e-5);
    }

    /**
     * Two objects flying in straight lines. The relative position is r0 + v*t, so the exact
     * time of closest approach and the miss distance can be worked out by hand:
     *   t* = -(r0 . v) / |v|^2
     */
    @Test
    void refinementMatchesTheAnswerWorkedOutByHand() {
        double[] r0 = {-600.0, 8.0, 3.0};   // km, relative position at t = 0
        double[] v = {10.0, 0.5, -0.2};     // km/s, relative velocity

        double speedSquared = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        double expectedTca = -(r0[0] * v[0] + r0[1] * v[1] + r0[2] * v[2]) / speedSquared;
        double expectedMiss = Math.sqrt(
                sq(r0[0] + v[0] * expectedTca) + sq(r0[1] + v[1] * expectedTca) + sq(r0[2] + v[2] * expectedTca));

        TcaRefiner.Approach approach = TcaRefiner.refine(
                t -> Math.sqrt(sq(r0[0] + v[0] * t) + sq(r0[1] + v[1] * t) + sq(r0[2] + v[2] * t)),
                expectedTca - 60, expectedTca + 60, 0.001);

        assertEquals(expectedTca, approach.secondsFromStart(), 0.01);
        assertEquals(expectedMiss, approach.missKm(), 1e-3);
    }

    @Test
    void refinementKeepsToTheRequestedWindow() {
        // The real minimum (t = 50) is outside the window, so the answer must be its edge.
        TcaRefiner.Approach approach = TcaRefiner.refine(t -> Math.abs(t - 50), 0, 10, 1e-4);

        assertEquals(10.0, approach.secondsFromStart(), 1e-3);
        assertEquals(40.0, approach.missKm(), 1e-3);
    }

    private static double sq(double x) {
        return x * x;
    }
}
