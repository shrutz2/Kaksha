package com.kaksha.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Positions of the objects drawn on the live globe, all taken at the same moment.
 * The browser moves each object forward from this moment using its velocity and asks for
 * a fresh snapshot every half minute.
 */
public record GlobeSnapshotDto(Instant generatedAt, List<GlobeObject> objects) {

    /** @param p position in km, @param v velocity in km/s, both in the TEME frame */
    public record GlobeObject(long id, String name, boolean indian, double[] p, double[] v) {
    }
}
