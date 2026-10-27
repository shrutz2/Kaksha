package com.kaksha.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaksha.config.ScreeningProperties;
import com.kaksha.domain.OrbitalElements;
import com.kaksha.propagation.OrekitData;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Runs the whole pipeline on made-up objects whose distances are known in advance.
 */
class ScreeningPipelineTest {

    private static final LocalDateTime EPOCH = LocalDateTime.of(2026, 10, 22, 0, 0);
    private static final Instant START = EPOCH.plusHours(1).toInstant(ZoneOffset.UTC);

    private final ScreeningProperties props = new ScreeningProperties(
            10, 60, 24, 48, 500, 50, 5, 0.01, false);

    @BeforeAll
    static void loadOrekitData() {
        OrekitData.load(Path.of("data/orekit-data"));
    }

    /** A nearly circular orbit about 490 km high. Only the position along the orbit differs. */
    private static TrackedObject object(long id, double meanMotion, double meanAnomalyDeg) {
        OrbitalElements e = new OrbitalElements(id, EPOCH, meanMotion, 0.0001, 97.5, 40, 90,
                meanAnomalyDeg, 0.0, 0.0, 0.0, 1, 1, 0, "U");
        return TrackedObject.of(id, "OBJ-" + id, e);
    }

    @Test
    void neighbourOnTheSameOrbitIsFoundWithTheRightDistance() {
        TrackedObject primary = object(1, 15.2, 10.0);
        // Same orbit, 0.02 degrees further along: about 2.4 km ahead for the whole day.
        TrackedObject neighbour = object(2, 15.2, 10.02);

        ScreeningResult result = new ScreeningPipeline(props).run(List.of(primary), List.of(neighbour), START);

        assertFalse(result.events().isEmpty(), "the neighbour should be flagged");
        for (Conjunction c : result.events()) {
            assertEquals(1, c.primaryId());
            assertEquals(2, c.secondaryId());
            assertTrue(c.missKm() > 1.5 && c.missKm() < 3.5, "miss was " + c.missKm());
            assertTrue(c.relativeSpeedKmS() < 0.1, "same orbit, so almost no relative speed");
        }
    }

    @Test
    void objectOnADifferentAltitudeIsRemovedByTheAltitudeFilter() {
        TrackedObject primary = object(1, 15.2, 10.0);
        TrackedObject higher = object(3, 13.0, 10.0); // far higher orbit

        ScreeningResult result = new ScreeningPipeline(props).run(List.of(primary), List.of(higher), START);

        StageStats altitude = result.stages().get(0);
        assertEquals(1, altitude.itemsIn());
        assertEquals(0, altitude.itemsOut());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void objectIsNeverPairedWithItself() {
        TrackedObject primary = object(1, 15.2, 10.0);

        ScreeningResult result = new ScreeningPipeline(props).run(List.of(primary), List.of(primary), START);

        assertEquals(0, result.stages().get(0).itemsIn());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void pairCountsNeverGrowFromOneStageToTheNext() {
        TrackedObject primary = object(1, 15.2, 10.0);
        List<TrackedObject> secondaries = List.of(object(2, 15.2, 10.02), object(3, 13.0, 10.0),
                object(4, 15.2, 200.0));

        ScreeningResult result = new ScreeningPipeline(props).run(List.of(primary), secondaries, START);

        List<StageStats> s = result.stages();
        assertEquals(3, s.get(0).itemsIn());
        assertTrue(s.get(0).itemsOut() <= s.get(0).itemsIn());
        assertTrue(s.get(1).itemsOut() <= s.get(1).itemsIn());
        assertTrue(s.get(3).itemsOut() <= s.get(3).itemsIn());
        assertEquals(result.events().size(), s.get(3).itemsOut());
    }
}
