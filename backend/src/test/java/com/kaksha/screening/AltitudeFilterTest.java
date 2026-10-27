package com.kaksha.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kaksha.domain.OrbitalElements;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AltitudeFilterTest {

    private static final double MARGIN = 10.0;

    @Test
    void overlappingRangesCanMeet() {
        OrbitRange a = new OrbitRange(500, 600);
        OrbitRange b = new OrbitRange(550, 800);

        assertTrue(AltitudeFilter.mayApproach(a, b, MARGIN));
    }

    @Test
    void oneRangeInsideTheOtherCanMeet() {
        OrbitRange wide = new OrbitRange(300, 2000);
        OrbitRange narrow = new OrbitRange(700, 710);

        assertTrue(AltitudeFilter.mayApproach(wide, narrow, MARGIN));
        assertTrue(AltitudeFilter.mayApproach(narrow, wide, MARGIN));
    }

    @Test
    void rangesSeparatedByMoreThanTheMarginAreDropped() {
        OrbitRange low = new OrbitRange(400, 500);
        OrbitRange high = new OrbitRange(520, 600); // 20 km above the top of the low orbit

        assertFalse(AltitudeFilter.mayApproach(low, high, MARGIN));
        assertFalse(AltitudeFilter.mayApproach(high, low, MARGIN));
    }

    @Test
    void rangesSeparatedByLessThanTheMarginAreKept() {
        OrbitRange low = new OrbitRange(400, 500);
        OrbitRange high = new OrbitRange(508, 600); // 8 km gap

        assertTrue(AltitudeFilter.mayApproach(low, high, MARGIN));
    }

    @Test
    void gapExactlyEqualToMarginIsKept() {
        OrbitRange low = new OrbitRange(400, 500);
        OrbitRange high = new OrbitRange(510, 600);

        assertTrue(AltitudeFilter.mayApproach(low, high, MARGIN));
    }

    @Test
    void geoSatelliteNeverMeetsLowOrbitObject() {
        OrbitRange geo = new OrbitRange(35780, 35795);
        OrbitRange leo = new OrbitRange(500, 520);

        assertFalse(AltitudeFilter.mayApproach(geo, leo, MARGIN));
    }

    @Test
    void rangeFromElementsMatchesAKnownOrbit() {
        // Worked out by hand: 15.2 revolutions a day is a period of 86400 / 15.2 = 5684 s.
        // Kepler's third law then gives a semi-major axis of about 6884 km, which is roughly
        // 505 km above the 6378 km equatorial radius.
        OrbitalElements e = new OrbitalElements(1L, LocalDateTime.of(2026, 1, 1, 0, 0),
                15.2, 0.0001, 97.5, 0, 0, 0, 0, 0, 0, 1, 1, 0, "U");

        OrbitRange range = OrbitRange.fromElements(e);

        assertEquals(505, range.perigeeKm(), 3);
        assertEquals(range.perigeeKm(), range.apogeeKm(), 2.0); // almost circular
    }
}
