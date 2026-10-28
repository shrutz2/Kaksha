package com.kaksha.service;

import com.kaksha.config.ScreeningProperties;
import com.kaksha.domain.ConjunctionEvent;
import com.kaksha.domain.ScreeningRun;
import com.kaksha.domain.SpaceObject;
import com.kaksha.repository.ConjunctionEventRepository;
import com.kaksha.repository.ScreeningRunRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.screening.RiskLevel;
import com.kaksha.web.dto.EventDto;
import com.kaksha.web.dto.PredictionDto;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Reads stored events and turns them into the objects the API returns. */
@Service
public class EventService {

    private static final Comparator<ConjunctionEvent> BY_RISK_THEN_MISS = Comparator
            .comparing((ConjunctionEvent e) -> RiskLevel.valueOf(e.getRisk()))
            .thenComparingDouble(ConjunctionEvent::getMissKm);

    private final ConjunctionEventRepository events;
    private final ScreeningRunRepository runs;
    private final SpaceObjectRepository objects;
    private final ScreeningProperties props;

    public EventService(ConjunctionEventRepository events, ScreeningRunRepository runs,
                        SpaceObjectRepository objects, ScreeningProperties props) {
        this.events = events;
        this.runs = runs;
        this.objects = objects;
        this.props = props;
    }

    /** Events found by the daily run that started on the given day (UTC), highest risk first. */
    public List<EventDto> eventsForDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay().minusNanos(1);
        Optional<ScreeningRun> run = runs.findFirstByModeAndRunAtBetweenOrderByRunAtDesc(
                ScreeningService.MODE_DAILY, from, to);
        if (run.isEmpty()) {
            return List.of();
        }
        // The daily run looks further ahead than it shows. The extra hours are kept so that the
        // next daily run can compare its prediction with this one.
        LocalDateTime shownUntil = run.get().getRunAt().plusHours(props.horizonHours());
        List<ConjunctionEvent> shown = events.findByRunIdOrderByMissKmAsc(run.get().getId()).stream()
                .filter(e -> e.getTca().isBefore(shownUntil))
                .toList();
        return toDtos(shown);
    }

    /**
     * Upcoming events of one satellite. The newest run that covered the satellite is used,
     * whether it was the daily run or a screening requested on demand.
     */
    public List<EventDto> eventsForSatellite(long noradId) {
        Optional<ScreeningRun> daily = runs.findFirstByModeOrderByRunAtDesc(ScreeningService.MODE_DAILY);
        Optional<ScreeningRun> onDemand = runs.findFirstByModeAndPrimaryIdOrderByRunAtDesc(
                ScreeningService.MODE_ON_DEMAND, noradId);

        Optional<ScreeningRun> newest = java.util.stream.Stream.of(daily, onDemand)
                .flatMap(Optional::stream)
                .max(Comparator.comparing(ScreeningRun::getRunAt));
        if (newest.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<ConjunctionEvent> upcoming = events.findByRunIdOrderByMissKmAsc(newest.get().getId()).stream()
                .filter(e -> e.getPrimaryId() == noradId)
                .filter(e -> e.getTca().isAfter(now))
                .toList();
        return toDtos(upcoming);
    }

    /**
     * Every prediction of this approach so far, oldest first, the latest one included. Each point
     * is one run, so the list shows how the miss distance moved as newer orbit data came in.
     * An event from an on-demand screening is not compared with earlier runs and has one point.
     */
    public List<PredictionDto> predictionHistory(long eventId) {
        ConjunctionEvent event = events.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Unknown event " + eventId));
        List<ConjunctionEvent> predictions = List.of(event);
        if (Boolean.TRUE.equals(event.getDaily())) {
            predictions = events.findPredictionHistory(event.getRunId(), event.getPrimaryId(), event.getSecondaryId(),
                    event.getTca().minusMinutes(ScreeningService.SAME_APPROACH_MINUTES),
                    event.getTca().plusMinutes(ScreeningService.SAME_APPROACH_MINUTES));
        }

        Map<Long, LocalDateTime> madeAt = runs.findAllById(predictions.stream().map(ConjunctionEvent::getRunId).toList())
                .stream().collect(Collectors.toMap(ScreeningRun::getId, ScreeningRun::getRunAt));
        return predictions.stream()
                .filter(p -> madeAt.containsKey(p.getRunId()))
                .map(p -> new PredictionDto(madeAt.get(p.getRunId()).toInstant(ZoneOffset.UTC), p.getMissKm(),
                        p.getTca().toInstant(ZoneOffset.UTC)))
                .toList();
    }

    /** All events of one run, used for the result of an on-demand screening. */
    public List<EventDto> eventsOfRun(long runId) {
        return toDtos(events.findByRunIdOrderByMissKmAsc(runId));
    }

    private List<EventDto> toDtos(List<ConjunctionEvent> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(e -> {
            ids.add(e.getPrimaryId());
            ids.add(e.getSecondaryId());
        });
        Map<Long, SpaceObject> byId = objects.findAllById(ids).stream()
                .collect(Collectors.toMap(SpaceObject::getNoradId, o -> o));

        return rows.stream()
                .sorted(BY_RISK_THEN_MISS)
                .map(e -> toDto(e, byId))
                .toList();
    }

    private static EventDto toDto(ConjunctionEvent e, Map<Long, SpaceObject> byId) {
        SpaceObject primary = byId.get(e.getPrimaryId());
        SpaceObject secondary = byId.get(e.getSecondaryId());
        Instant tca = e.getTca().toInstant(ZoneOffset.UTC);
        Instant previousTca = e.getPreviousTca() == null ? null : e.getPreviousTca().toInstant(ZoneOffset.UTC);
        return new EventDto(e.getId(),
                e.getPrimaryId(), primary == null ? null : primary.getName(),
                e.getSecondaryId(), secondary == null ? null : secondary.getName(),
                secondary == null ? null : secondary.getObjectType(),
                secondary == null ? null : ObjectLabels.ownerName(secondary.getOwner()),
                secondary == null ? null : ObjectLabels.kind(secondary),
                tca, e.getMissKm(), e.getRelativeSpeedKmS(), e.getRisk(),
                e.getTimesSeen() == null ? 1 : e.getTimesSeen(), e.getPreviousMissKm(), previousTca,
                stability(e));
    }

    /** A repeat prediction is called stable if the miss distance moved by at most this much (km)... */
    static final double STABLE_MISS_CHANGE_KM = 0.5;
    /** ...and the time of closest approach by at most this much (seconds). */
    static final long STABLE_TIME_CHANGE_SECONDS = 60;

    /** These limits are a judgement call, not a statistical result. The raw numbers are in the response too. */
    static String stability(ConjunctionEvent e) {
        if (e.getTimesSeen() == null || e.getTimesSeen() < 2 || e.getPreviousMissKm() == null
                || e.getPreviousTca() == null) {
            return "NEW";
        }
        double missChange = Math.abs(e.getMissKm() - e.getPreviousMissKm());
        long timeChange = Math.abs(Duration.between(e.getPreviousTca(), e.getTca()).getSeconds());
        return missChange <= STABLE_MISS_CHANGE_KM && timeChange <= STABLE_TIME_CHANGE_SECONDS ? "STABLE" : "CHANGED";
    }
}
