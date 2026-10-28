package com.kaksha.web.dto;

import java.time.Instant;
import java.util.List;

/** Benchmark numbers of one screening run: how many pairs were left after each stage. */
public record StatsDto(
        Instant runAt,
        String mode,
        int primaries,
        int secondaries,
        int horizonHours,
        long totalRuntimeMs,
        int propagationFailures,
        List<Stage> stages) {

    /** @param pairs items left after this stage, @param ms time this stage took */
    public record Stage(String name, long pairs, long ms) {
    }
}
