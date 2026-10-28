package com.kaksha.service;

import com.kaksha.domain.ScreeningRun;
import com.kaksha.repository.ScreeningRunRepository;
import com.kaksha.web.dto.StatsDto;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Builds the benchmark numbers of the latest daily screening run. */
@Service
public class StatsService {

    private final ScreeningRunRepository runs;

    public StatsService(ScreeningRunRepository runs) {
        this.runs = runs;
    }

    public StatsDto lastRun() {
        ScreeningRun run = runs.findFirstByModeOrderByRunAtDesc(ScreeningService.MODE_DAILY)
                .orElseThrow(() -> new NoSuchElementException("No daily screening run has finished yet"));

        List<StatsDto.Stage> stages = List.of(
                new StatsDto.Stage("All pairs", run.getPairsTotal(), 0),
                new StatsDto.Stage("Altitude filter", run.getPairsAfterAltitude(), run.getAltitudeMillis()),
                new StatsDto.Stage("Coarse scan", run.getPairsAfterCoarse(), run.getCoarseMillis()),
                new StatsDto.Stage("Refined, within 50 km", run.getEncountersInVolume(), run.getRefineMillis()),
                new StatsDto.Stage("Flagged, under 5 km", run.getEventsFlagged(), run.getFlagMillis()));

        return new StatsDto(run.getRunAt().toInstant(ZoneOffset.UTC), run.getMode(), run.getPrimaries(),
                run.getSecondaries(), run.getHorizonHours(), run.getTotalMillis(), run.getPropagationFailures(), stages);
    }
}
