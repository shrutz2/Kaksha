package com.kaksha.service;

import com.kaksha.alert.AlertService;
import com.kaksha.config.ScreeningProperties;
import com.kaksha.domain.ConjunctionEvent;
import com.kaksha.domain.OrbitalElements;
import com.kaksha.domain.ScreeningRun;
import com.kaksha.domain.SpaceObject;
import com.kaksha.repository.ConjunctionEventRepository;
import com.kaksha.repository.OrbitalElementsRepository;
import com.kaksha.repository.ScreeningRunRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.screening.Conjunction;
import com.kaksha.screening.ScreeningPipeline;
import com.kaksha.screening.ScreeningResult;
import com.kaksha.screening.StageStats;
import com.kaksha.screening.TrackedObject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Connects the screening pipeline to the database: loads the objects, saves the results. */
@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    public static final String MODE_DAILY = "DAILY";
    public static final String MODE_ON_DEMAND = "ON_DEMAND";

    /** Two predictions count as the same approach when their times of closest approach are this close. */
    static final long SAME_APPROACH_MINUTES = 15;

    private final SpaceObjectRepository objects;
    private final OrbitalElementsRepository elements;
    private final ScreeningRunRepository runs;
    private final ConjunctionEventRepository events;
    private final ScreeningProperties props;
    private final AlertService alerts;

    public ScreeningService(SpaceObjectRepository objects, OrbitalElementsRepository elements,
                            ScreeningRunRepository runs, ConjunctionEventRepository events,
                            ScreeningProperties props, AlertService alerts) {
        this.objects = objects;
        this.elements = elements;
        this.runs = runs;
        this.events = events;
        this.props = props;
        this.alerts = alerts;
    }

    /** Screens every Indian satellite against the whole catalog. */
    public ScreeningRun screenAllIndian() {
        List<TrackedObject> all = loadTrackedObjects();
        Map<Long, SpaceObject> byId = indexById();
        List<TrackedObject> primaries = all.stream()
                .filter(t -> byId.get(t.noradId()).isIndianPayload())
                .toList();
        if (primaries.isEmpty()) {
            // Nothing to screen, for example before the first catalog download has worked.
            // No run is saved, so an empty run never shows up as the "last run".
            throw new NoSuchElementException("No Indian satellites with orbital data in the catalog yet");
        }
        return runAndStore(primaries, all, MODE_DAILY, null);
    }

    /** Screens one satellite against the whole catalog. */
    public ScreeningRun screenOne(long noradId) {
        List<TrackedObject> all = loadTrackedObjects();
        List<TrackedObject> primaries = all.stream().filter(t -> t.noradId() == noradId).toList();
        if (primaries.isEmpty()) {
            throw new NoSuchElementException("No orbital data for object " + noradId);
        }
        return runAndStore(primaries, all, MODE_ON_DEMAND, noradId);
    }

    private ScreeningRun runAndStore(List<TrackedObject> primaries, List<TrackedObject> all, String mode,
                                  Long primaryId) {
        Instant start = Instant.now();
        boolean daily = MODE_DAILY.equals(mode);
        int horizonHours = daily ? props.dailyHorizonHours() : props.horizonHours();
        ScreeningResult result = new ScreeningPipeline(props).run(primaries, all, start, horizonHours);

        StageStats altitude = result.stages().get(0);
        StageStats coarse = result.stages().get(1);
        StageStats refine = result.stages().get(2);
        StageStats flag = result.stages().get(3);

        ScreeningRun run = runs.save(new ScreeningRun(
                LocalDateTime.ofInstant(start, ZoneOffset.UTC), mode, primaryId, horizonHours,
                result.primaries(), result.secondaries(),
                altitude.itemsIn(), altitude.itemsOut(), coarse.itemsOut(),
                refine.itemsIn(), refine.itemsOut(), result.events().size(),
                altitude.millis(), coarse.millis(), refine.millis(), flag.millis(),
                result.totalMillis(), result.propagationFailures()));

        List<ConjunctionEvent> rows = new ArrayList<>();
        for (Conjunction c : result.events()) {
            rows.add(toRow(run.getId(), c, daily));
        }
        List<ConjunctionEvent> saved = events.saveAll(rows);
        try {
            alerts.sendHighRiskAlerts(saved);
        } catch (RuntimeException e) {
            // A failing mail server must not make the screening itself look failed.
            log.warn("Sending alerts failed: {}", e.toString());
        }
        return run;
    }

    /**
     * Turns a found approach into a database row. For the daily run it also looks for the same
     * approach in earlier daily runs. If one is found, the row remembers how many times the
     * approach has now been predicted and what the earlier prediction said. The change between
     * two predictions made from different element sets shows how much the result can be trusted.
     */
    ConjunctionEvent toRow(Long runId, Conjunction c, boolean daily) {
        LocalDateTime tca = LocalDateTime.ofInstant(c.tca(), ZoneOffset.UTC);
        if (!daily) {
            return new ConjunctionEvent(runId, c.primaryId(), c.secondaryId(), tca, c.missKm(),
                    c.relativeSpeedKmS(), c.risk().name());
        }
        List<ConjunctionEvent> earlier = events.findEarlierPredictions(runId, c.primaryId(), c.secondaryId(),
                tca.minusMinutes(SAME_APPROACH_MINUTES), tca.plusMinutes(SAME_APPROACH_MINUTES));
        if (earlier.isEmpty()) {
            return new ConjunctionEvent(runId, c.primaryId(), c.secondaryId(), tca, c.missKm(),
                    c.relativeSpeedKmS(), c.risk().name(), true, 1, null, null);
        }
        ConjunctionEvent previous = earlier.get(0);
        int seen = (previous.getTimesSeen() == null ? 1 : previous.getTimesSeen()) + 1;
        return new ConjunctionEvent(runId, c.primaryId(), c.secondaryId(), tca, c.missKm(),
                c.relativeSpeedKmS(), c.risk().name(), true, seen, previous.getMissKm(), previous.getTca());
    }

    /** Joins each stored object with its latest elements. Objects without elements are left out. */
    private List<TrackedObject> loadTrackedObjects() {
        Map<Long, OrbitalElements> elementsById = new HashMap<>();
        for (OrbitalElements e : elements.findAll()) {
            elementsById.put(e.getNoradId(), e);
        }
        List<TrackedObject> tracked = new ArrayList<>();
        for (SpaceObject o : objects.findAll()) {
            OrbitalElements e = elementsById.get(o.getNoradId());
            if (e != null) {
                tracked.add(TrackedObject.of(o.getNoradId(), o.getName(), e));
            }
        }
        return tracked;
    }

    private Map<Long, SpaceObject> indexById() {
        Map<Long, SpaceObject> byId = new HashMap<>();
        for (SpaceObject o : objects.findAll()) {
            byId.put(o.getNoradId(), o);
        }
        return byId;
    }
}
