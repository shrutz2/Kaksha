package com.kaksha.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kaksha.config.ScreeningProperties;
import com.kaksha.domain.ConjunctionEvent;
import com.kaksha.domain.ScreeningRun;
import com.kaksha.domain.SpaceObject;
import com.kaksha.repository.ConjunctionEventRepository;
import com.kaksha.repository.ScreeningRunRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.web.dto.EventDto;
import com.kaksha.web.dto.PredictionDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventServiceTest {

    private ConjunctionEventRepository events;
    private ScreeningRunRepository runs;
    private SpaceObjectRepository objects;
    private EventService service;

    @BeforeEach
    void setUp() {
        events = mock(ConjunctionEventRepository.class);
        runs = mock(ScreeningRunRepository.class);
        objects = mock(SpaceObjectRepository.class);
        service = new EventService(events, runs, objects, new ScreeningProperties(10, 60, 24, 48, 500, 50, 5, 0.01, false));

        when(objects.findAllById(any())).thenReturn(List.of(
                object(1, "CARTOSAT-3", "PAY"),
                object(2, "DEBRIS A", "DEB"),
                object(3, "DEBRIS B", "DEB"),
                object(4, "ROCKET BODY", "R/B")));
    }

    private static SpaceObject object(long id, String name, String type) {
        // Object 1 is the Indian satellite. The others belong to the United States.
        String owner = id == 1 ? "IND" : "US";
        return new SpaceObject(id, name, "2000-001A", type, "+", owner, null, 500.0, 520.0);
    }

    private static ConjunctionEvent event(long secondaryId, double missKm, String risk) {
        return new ConjunctionEvent(7L, 1L, secondaryId, LocalDateTime.of(2026, 10, 23, 12, 0),
                missKm, 10.0, risk);
    }

    private static ScreeningRun runWithId(long id) {
        ScreeningRun run = mock(ScreeningRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getRunAt()).thenReturn(LocalDateTime.of(2026, 10, 23, 2, 30));
        return run;
    }

    @Test
    void eventsAreSortedByRiskThenByMissDistance() {
        ScreeningRun run = runWithId(7);
        when(runs.findFirstByModeAndRunAtBetweenOrderByRunAtDesc(eq("DAILY"), any(), any()))
                .thenReturn(Optional.of(run));
        when(events.findByRunIdOrderByMissKmAsc(7L)).thenReturn(List.of(
                event(2, 0.5, "HIGH"),
                event(3, 2.0, "MEDIUM"),
                event(4, 4.0, "LOW"),
                event(2, 0.2, "HIGH")));

        List<EventDto> result = service.eventsForDate(LocalDate.of(2026, 10, 23));

        assertEquals(List.of("HIGH", "HIGH", "MEDIUM", "LOW"), result.stream().map(EventDto::risk).toList());
        assertEquals(0.2, result.get(0).missKm());
        assertEquals(0.5, result.get(1).missKm());
    }

    @Test
    void namesAndTypesAreFilledIn() {
        ScreeningRun run = runWithId(7);
        when(runs.findFirstByModeAndRunAtBetweenOrderByRunAtDesc(eq("DAILY"), any(), any()))
                .thenReturn(Optional.of(run));
        when(events.findByRunIdOrderByMissKmAsc(7L)).thenReturn(List.of(event(4, 0.9, "HIGH")));

        EventDto dto = service.eventsForDate(LocalDate.of(2026, 10, 23)).get(0);

        assertEquals("CARTOSAT-3", dto.primaryName());
        assertEquals("ROCKET BODY", dto.secondaryName());
        assertEquals("R/B", dto.secondaryType());
        assertEquals("United States", dto.secondaryOwner());
        assertEquals("Rocket body", dto.secondaryKind());
    }

    @Test
    void aDayWithoutADailyRunHasNoEvents() {
        when(runs.findFirstByModeAndRunAtBetweenOrderByRunAtDesc(eq("DAILY"), any(), any()))
                .thenReturn(Optional.empty());

        assertTrue(service.eventsForDate(LocalDate.of(2026, 1, 1)).isEmpty());
    }

    @Test
    void eventsBeyondTheShownWindowAreKeptForTomorrowButNotListed() {
        ScreeningRun run = runWithId(7);   // started on the 23rd at 02:30, so the shown 24 hours end on the 24th at 02:30
        when(runs.findFirstByModeAndRunAtBetweenOrderByRunAtDesc(eq("DAILY"), any(), any()))
                .thenReturn(Optional.of(run));
        ConjunctionEvent today = new ConjunctionEvent(7L, 1L, 2L, LocalDateTime.of(2026, 10, 23, 20, 0), 0.5, 10.0, "HIGH");
        ConjunctionEvent preview = new ConjunctionEvent(7L, 1L, 2L, LocalDateTime.of(2026, 10, 24, 20, 0), 0.4, 10.0, "HIGH");
        when(events.findByRunIdOrderByMissKmAsc(7L)).thenReturn(List.of(preview, today));

        List<EventDto> result = service.eventsForDate(LocalDate.of(2026, 10, 23));

        assertEquals(1, result.size());
        assertEquals(0.5, result.get(0).missKm());
    }

    private static ConjunctionEvent dailyEvent(long runId, double missKm, LocalDateTime tca) {
        return new ConjunctionEvent(runId, 1L, 2L, tca, missKm, 10.0, "HIGH", true, 1, null, null);
    }

    private static ScreeningRun runAt(long id, LocalDateTime at) {
        ScreeningRun run = mock(ScreeningRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getRunAt()).thenReturn(at);
        return run;
    }

    @Test
    void predictionHistoryListsEveryRunThatFoundTheApproachOldestFirst() {
        LocalDateTime tca = LocalDateTime.of(2026, 10, 25, 6, 0);
        ConjunctionEvent latest = dailyEvent(9, 0.48, tca.plusSeconds(10));
        when(events.findById(40L)).thenReturn(Optional.of(latest));
        when(events.findPredictionHistory(eq(9L), eq(1L), eq(2L), any(), any())).thenReturn(List.of(
                dailyEvent(5, 0.90, tca), dailyEvent(7, 0.61, tca.plusSeconds(4)), latest));
        ScreeningRun first = runAt(5, LocalDateTime.of(2026, 10, 24, 2, 30));
        ScreeningRun second = runAt(7, LocalDateTime.of(2026, 10, 24, 8, 30));
        ScreeningRun third = runAt(9, LocalDateTime.of(2026, 10, 24, 14, 30));
        when(runs.findAllById(any())).thenReturn(List.of(first, second, third));

        List<PredictionDto> history = service.predictionHistory(40L);

        assertEquals(List.of(0.90, 0.61, 0.48), history.stream().map(p -> p.missKm()).toList());
        assertEquals("2026-10-24T08:30:00Z", history.get(1).madeAt().toString());
    }

    @Test
    void anOnDemandEventHasASinglePointAndIsNotCompared() {
        ConjunctionEvent onDemand = new ConjunctionEvent(11L, 1L, 2L, LocalDateTime.of(2026, 10, 25, 6, 0), 0.7, 10.0, "HIGH");
        when(events.findById(41L)).thenReturn(Optional.of(onDemand));
        ScreeningRun run = runAt(11, LocalDateTime.of(2026, 10, 24, 9, 0));
        when(runs.findAllById(any())).thenReturn(List.of(run));

        assertEquals(1, service.predictionHistory(41L).size());
    }

    @Test
    void predictionHistoryOfAnUnknownEventIsNotFound() {
        when(events.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.predictionHistory(99L));
    }

    @Test
    void satelliteWithNoRunsHasNoEvents() {
        when(runs.findFirstByModeOrderByRunAtDesc("DAILY")).thenReturn(Optional.empty());
        when(runs.findFirstByModeAndPrimaryIdOrderByRunAtDesc(eq("ON_DEMAND"), any()))
                .thenReturn(Optional.empty());

        assertTrue(service.eventsForSatellite(1L).isEmpty());
    }
}
