package com.kaksha.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the globe needs to draw one event: a ring for each object's orbit and the point where
 * they meet. All positions are in kilometres in the TEME frame, the frame SGP4 works in.
 *
 * @param primaryRing   one full revolution of the Indian satellite, starting at the time of closest approach
 * @param secondaryRing the same for the other object
 * @param meeting       where the primary is at the time of closest approach
 */
public record GeometryDto(
        Instant tca,
        List<double[]> primaryRing,
        List<double[]> secondaryRing,
        double[] meeting) {
}
