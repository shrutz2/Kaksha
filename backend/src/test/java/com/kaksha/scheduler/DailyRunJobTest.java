package com.kaksha.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.kaksha.repository.ScreeningRunRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.service.CatalogService;
import com.kaksha.service.ScreeningService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Deciding at start-up whether the scheduled run of the day was missed. */
class DailyRunJobTest {

    private static DailyRunJob jobWithSchedule(String cron) {
        return new DailyRunJob(mock(CatalogService.class), mock(ScreeningService.class),
                mock(SpaceObjectRepository.class), mock(ScreeningRunRepository.class), cron);
    }

    private final DailyRunJob job = jobWithSchedule("0 30 2 * * *");

    @Test
    void startedAfterTheScheduledTimeWithoutARunTodayMeansItWasMissed() {
        assertTrue(job.todaysRunMissed(LocalDateTime.of(2026, 10, 24, 12, 0), false));
        assertTrue(job.todaysRunMissed(LocalDateTime.of(2026, 10, 24, 2, 30), false));
    }

    @Test
    void startedBeforeTheScheduledTimeIsNotMissedYet() {
        assertFalse(job.todaysRunMissed(LocalDateTime.of(2026, 10, 24, 1, 15), false));
        assertFalse(job.todaysRunMissed(LocalDateTime.of(2026, 10, 24, 0, 0), false));
    }

    @Test
    void aRunThatAlreadyHappenedTodayIsNeverRepeated() {
        assertFalse(job.todaysRunMissed(LocalDateTime.of(2026, 10, 24, 12, 0), true));
    }

    @Test
    void worksForOtherSchedules() {
        DailyRunJob evening = jobWithSchedule("0 0 18 * * *");

        assertFalse(evening.todaysRunMissed(LocalDateTime.of(2026, 10, 24, 12, 0), false));
        assertTrue(evening.todaysRunMissed(LocalDateTime.of(2026, 10, 24, 19, 0), false));
    }
}
