package com.kaksha.propagation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaksha.domain.OrbitalElements;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the propagator against the published SGP4 verification case from Vallado, Crawford,
 * Hujsak and Kelso, "Revisiting Spacetrack Report #3" (satellite 00005). Their expected
 * positions and velocities are the standard answer every SGP4 implementation is tested with.
 *
 * The element set of that case, as a TLE:
 *   1 00005U 58002B   00179.78495062  .00000023  00000-0  28098-4 0  4753
 *   2 00005  34.2682 348.7242 1859667 331.7664  19.3264 10.82419157413667
 */
class Sgp4PropagatorTest {

    // Epoch: year 2000, day-of-year 179.78495062 (27 June). Built from the day fraction the
    // same way a TLE reader does, so the epoch matches the reference exactly.
    private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 6, 27, 0, 0)
            .plusNanos(Math.round(0.78495062 * 86400e9));

    // Allowed difference from the published numbers: 1 metre.
    private static final double TOLERANCE_KM = 1e-3;
    private static final double TOLERANCE_KM_S = 1e-6;

    private static Sgp4Propagator propagator;

    @BeforeAll
    static void setUp() {
        OrekitData.load(Path.of("data/orekit-data"));

        OrbitalElements elements = new OrbitalElements(
                5L, EPOCH,
                10.82419157,       // revolutions per day
                0.1859667,         // eccentricity
                34.2682,           // inclination, degrees
                348.7242,          // right ascension of ascending node, degrees
                331.7664,          // argument of perigee, degrees
                19.3264,           // mean anomaly, degrees
                2.8098e-5,         // drag term
                0.00000023,        // first derivative of mean motion
                0.0,
                475, 41366, 0, "U");
        propagator = new Sgp4Propagator(elements);
    }

    private static Instant epochPlusMinutes(int minutes) {
        return EPOCH.toInstant(ZoneOffset.UTC).plus(Duration.ofMinutes(minutes));
    }

    @Test
    void matchesReferenceAtEpoch() {
        StateVector s = propagator.propagate(epochPlusMinutes(0));

        assertEquals(7022.46529266, s.x(), TOLERANCE_KM);
        assertEquals(-1400.08296755, s.y(), TOLERANCE_KM);
        assertEquals(0.03995155, s.z(), TOLERANCE_KM);
        assertEquals(1.893841015, s.vx(), TOLERANCE_KM_S);
        assertEquals(6.405893759, s.vy(), TOLERANCE_KM_S);
        assertEquals(4.534807250, s.vz(), TOLERANCE_KM_S);
    }

    @Test
    void matchesReferenceSixHoursLater() {
        StateVector s = propagator.propagate(epochPlusMinutes(360));

        assertEquals(-7154.03120202, s.x(), TOLERANCE_KM);
        assertEquals(-3783.17682504, s.y(), TOLERANCE_KM);
        assertEquals(-3536.19412294, s.z(), TOLERANCE_KM);
        assertEquals(4.741887409, s.vx(), TOLERANCE_KM_S);
        assertEquals(-4.151817765, s.vy(), TOLERANCE_KM_S);
        assertEquals(-2.093935425, s.vz(), TOLERANCE_KM_S);
    }

    @Test
    void stateVectorHelpersAgreeWithPlainGeometry() {
        StateVector a = new StateVector(3, 0, 0, 0, 1, 0);
        StateVector b = new StateVector(0, 4, 0, 0, 1, 4);

        assertEquals(5.0, a.distanceTo(b), 1e-12);
        assertEquals(4.0, a.relativeSpeedTo(b), 1e-12);
        assertTrue(Math.abs(a.radius() - 3.0) < 1e-12);
    }
}
