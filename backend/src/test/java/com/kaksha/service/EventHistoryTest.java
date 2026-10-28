package com.kaksha.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaksha.alert.AlertService;
import com.kaksha.config.ScreeningProperties;
import com.kaksha.domain.ConjunctionEvent;
import com.kaksha.repository.ConjunctionEventRepository;
import com.kaksha.repository.OrbitalElementsRepository;
import com.kaksha.repository.ScreeningRunRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.screening.Conjunction;
import com.kaksha.screening.RiskLevel;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** How an event is compared with the earlier predictions of the same approach. */
class EventHistoryTest {

    private static final LocalDateTime TCA = LocalDateTime.of(2026, 10, 24, 6, 51, 4);

    private ConjunctionEventRepository events;
    private ScreeningService service;

    @BeforeEach
    void setUp() {
        events = mock(ConjunctionEventRepository.class);
        service = new ScreeningService(mock(SpaceObjectRepository.class), mock(OrbitalElementsRepository.class),
                mock(ScreeningRunRepository.class), events,
                new ScreeningProperties(10, 60, 24, 48, 500, 50, 5, 0.01, false), mock(AlertService.class));
    }

    private static Conjunction found(double missKm) {
        return new Conjunction(1L, 2L, Instant.parse("2026-10-24T06:51:04Z"), missKm, 11.3, RiskLevel.HIGH);
    }

    private static ConjunctionEvent earlier(int timesSeen, double missKm, LocalDateTime tca) {
        return new ConjunctionEvent(5L, 1L, 2L, tca, missKm, 11.3, "HIGH", true, timesSeen, null, null);
    }

    @Test
    void firstPredictionIsNew() {
        when(events.findEarlierPredictions(any(), any(), any(), any(), any())).thenReturn(List.of());

        ConjunctionEvent row = service.toRow(9L, found(0.48), true);

        assertEquals(1, row.getTimesSeen());
        assertNull(row.getPreviousMissKm());
        assertEquals(TCA, row.getTca());
    }

    @Test
    void approachFoundInAnEarlierDailyRunRemembersThatPrediction() {
        when(events.findEarlierPredictions(eq(9L), eq(1L), eq(2L), any(), any()))
                .thenReturn(List.of(earlier(1, 0.62, TCA.plusSeconds(20))));

        ConjunctionEvent row = service.toRow(9L, found(0.48), true);

        assertEquals(2, row.getTimesSeen());
        assertEquals(0.62, row.getPreviousMissKm());
        assertEquals(TCA.plusSeconds(20), row.getPreviousTca());
    }

    @Test
    void theCountKeepsGrowingWithEveryRepeat() {
        when(events.findEarlierPredictions(any(), any(), any(), any(), any()))
                .thenReturn(List.of(earlier(3, 0.5, TCA)));

        assertEquals(4, service.toRow(9L, found(0.48), true).getTimesSeen());
    }

    @Test
    void searchWindowIsAroundTheTimeOfClosestApproach() {
        when(events.findEarlierPredictions(any(), any(), any(), any(), any())).thenReturn(List.of());

        service.toRow(9L, found(0.48), true);

        verify(events).findEarlierPredictions(9L, 1L, 2L,
                TCA.minusMinutes(ScreeningService.SAME_APPROACH_MINUTES),
                TCA.plusMinutes(ScreeningService.SAME_APPROACH_MINUTES));
    }

    @Test
    void onDemandRunsAreNeverCompared() {
        ConjunctionEvent row = service.toRow(9L, found(0.48), false);

        assertNull(row.getDaily());
        assertNull(row.getTimesSeen());
        verify(events, never()).findEarlierPredictions(any(), any(), any(), any(), any());
    }

    @Test
    void stabilityLabelsFollowTheDocumentedLimits() {
        ConjunctionEvent first = new ConjunctionEvent(9L, 1L, 2L, TCA, 0.48, 11.3, "HIGH", true, 1, null, null);
        ConjunctionEvent steady = new ConjunctionEvent(9L, 1L, 2L, TCA, 0.48, 11.3, "HIGH", true, 2, 0.60, TCA.plusSeconds(30));
        ConjunctionEvent missMoved = new ConjunctionEvent(9L, 1L, 2L, TCA, 0.48, 11.3, "HIGH", true, 2, 1.20, TCA);
        ConjunctionEvent timeMoved = new ConjunctionEvent(9L, 1L, 2L, TCA, 0.48, 11.3, "HIGH", true, 2, 0.50, TCA.plusMinutes(3));
        ConjunctionEvent legacy = new ConjunctionEvent(9L, 1L, 2L, TCA, 0.48, 11.3, "HIGH");

        assertEquals("NEW", EventService.stability(first));
        assertEquals("STABLE", EventService.stability(steady));
        assertEquals("CHANGED", EventService.stability(missMoved));
        assertEquals("CHANGED", EventService.stability(timeMoved));
        assertEquals("NEW", EventService.stability(legacy));
    }
}
