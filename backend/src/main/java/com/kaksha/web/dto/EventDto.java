package com.kaksha.web.dto;

import java.time.Instant;

/**
 * One flagged close approach, as the frontend table and globe expect it.
 *
 * @param secondaryOwner country or organisation that owns the other object
 * @param secondaryKind  what the other object is: active satellite, inactive satellite, rocket body, debris
 * @param timesSeen      how many daily runs have predicted this approach
 * @param previousMissKm miss distance of the previous prediction, empty if this is the first
 * @param previousTca    time of closest approach of the previous prediction
 * @param stability      NEW (first prediction), STABLE (predicted again with almost the same result)
 *                       or CHANGED (predicted again, but the miss distance or time moved noticeably)
 */
public record EventDto(
        Long id,
        Long primaryId,
        String primaryName,
        Long secondaryId,
        String secondaryName,
        String secondaryType,
        String secondaryOwner,
        String secondaryKind,
        Instant tca,
        double missKm,
        double relVelKmS,
        String risk,
        int timesSeen,
        Double previousMissKm,
        Instant previousTca,
        String stability) {
}
