package com.kaksha.screening;

/**
 * Stage 1 of the screening: a cheap test that throws away pairs that can never meet.
 *
 * Every orbit stays between its perigee and apogee altitude. If one object always flies
 * below the other, with a gap bigger than the margin, they cannot come close, whatever
 * their other orbit details are.
 */
public final class AltitudeFilter {

    private AltitudeFilter() {
    }

    /**
     * @return true if the two altitude ranges overlap, or are no further apart than marginKm
     */
    public static boolean mayApproach(OrbitRange a, OrbitRange b, double marginKm) {
        // The gap is the highest "floor" minus the lowest "ceiling". It is negative when
        // the ranges overlap and positive when there is empty space between them.
        double gap = Math.max(a.perigeeKm(), b.perigeeKm()) - Math.min(a.apogeeKm(), b.apogeeKm());
        return gap <= marginKm;
    }
}
