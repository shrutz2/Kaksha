package com.kaksha.screening;

import java.util.List;

/** Everything a screening run produces: the flagged events and the benchmark numbers. */
public record ScreeningResult(
        int primaries,
        int secondaries,
        List<StageStats> stages,
        long totalMillis,
        int propagationFailures,
        List<Conjunction> events) {
}
