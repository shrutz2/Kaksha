package com.kaksha.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaksha.domain.OrbitalElements;
import com.kaksha.propagation.OrekitData;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GeometryServiceTest {

    @BeforeAll
    static void loadOrekitData() {
        OrekitData.load(Path.of("data/orekit-data"));
    }

    private static double radius(double[] p) {
        return Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
    }

    @Test
    void ringOfANearlyCircularOrbitIsAClosedBandAroundTheEarth() {
        // 15.2 revolutions a day, nearly circular: about 6884 km from the centre of the Earth.
        OrbitalElements e = new OrbitalElements(1L, LocalDateTime.of(2026, 10, 23, 0, 0),
                15.2, 0.0001, 97.5, 40, 90, 10, 0, 0, 0, 1, 1, 0, "U");

        List<double[]> ring = GeometryService.ring(e, Instant.parse("2026-10-23T06:00:00Z"));

        assertEquals(181, ring.size());
        for (double[] p : ring) {
            assertEquals(6884, radius(p), 15);
        }
        // After one full period the object is back near where it started.
        double[] first = ring.get(0);
        double[] last = ring.get(ring.size() - 1);
        double gap = Math.sqrt(Math.pow(first[0] - last[0], 2) + Math.pow(first[1] - last[1], 2)
                + Math.pow(first[2] - last[2], 2));
        assertTrue(gap < 150, "ring should nearly close, gap was " + gap + " km");
    }
}
