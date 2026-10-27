package com.kaksha.screening;

import java.time.Instant;

/** A refined close approach between a primary and a secondary object. */
public record Conjunction(
        long primaryId,
        long secondaryId,
        Instant tca,
        double missKm,
        double relativeSpeedKmS,
        RiskLevel risk) {
}
