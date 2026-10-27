package com.kaksha.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "kaksha.screening" in application.yml.
 *
 * @param altitudeMarginKm      stage 1: pairs whose altitude ranges are further apart than this are dropped
 * @param stepSeconds           stage 2: time between positions in the coarse scan
 * @param horizonHours          how far ahead an on-demand screening looks, and how many hours of a daily run are shown
 * @param dailyHorizonHours     how far ahead the daily run looks. It is longer than horizonHours so that most events are
 *                              found again by the next daily run, which makes it possible to compare the two predictions
 * @param coarseGridThresholdKm stage 2: a local minimum on the time grid is kept if it is below this distance
 * @param screeningVolumeKm     stage 3: after refinement, approaches closer than this are kept
 * @param eventThresholdKm      stage 4: approaches closer than this are flagged as events
 * @param refineToleranceSeconds how precisely the time of closest approach is searched
 * @param runOnStartup          run a full screening right after the application starts
 */
@ConfigurationProperties(prefix = "kaksha.screening")
public record ScreeningProperties(
        double altitudeMarginKm,
        int stepSeconds,
        int horizonHours,
        int dailyHorizonHours,
        double coarseGridThresholdKm,
        double screeningVolumeKm,
        double eventThresholdKm,
        double refineToleranceSeconds,
        boolean runOnStartup) {
}
