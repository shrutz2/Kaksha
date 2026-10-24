package com.kaksha.catalog;

import java.time.LocalDateTime;

/**
 * One row of the CelesTrak GP data: the mean orbital elements of an object at one epoch.
 * These are the numbers SGP4 needs to predict where the object will be. Epoch is in UTC.
 */
public record GpRecord(
        long noradId,
        LocalDateTime epoch,
        double meanMotion,
        double eccentricity,
        double inclination,
        double raan,
        double argOfPericenter,
        double meanAnomaly,
        double bstar,
        double meanMotionDot,
        double meanMotionDdot,
        int elementSetNo,
        int revAtEpoch,
        int ephemerisType,
        String classification) {
}
