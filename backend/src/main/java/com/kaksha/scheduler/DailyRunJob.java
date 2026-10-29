package com.kaksha.scheduler;

import com.kaksha.repository.ScreeningRunRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.service.CatalogService;
import com.kaksha.service.ScreeningService;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * The daily routine: refresh the catalog from CelesTrak, then screen every Indian satellite.
 *
 * It runs at the time given by kaksha.refresh-cron. A scheduled job that was missed because the
 * server was off at that time is not run later by Spring, so at start-up this class checks
 * whether today's run is missing and, if so, does it right away. It also loads the catalog once
 * if the database is still empty.
 */
@Component
@Order(1)
public class DailyRunJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailyRunJob.class);

    private final CatalogService catalog;
    private final ScreeningService screening;
    private final SpaceObjectRepository objects;
    private final ScreeningRunRepository runs;
    private final CronExpression schedule;

    public DailyRunJob(CatalogService catalog, ScreeningService screening, SpaceObjectRepository objects,
                       ScreeningRunRepository runs, @Value("${kaksha.refresh-cron}") String refreshCron) {
        this.catalog = catalog;
        this.screening = screening;
        this.objects = objects;
        this.runs = runs;
        this.schedule = CronExpression.parse(refreshCron);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (objects.count() == 0) {
            refreshCatalog();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (todaysRunMissed(now, hasRunOn(now.toLocalDate()))) {
            log.info("Today's scheduled run was missed, running it now");
            // In the background, so start-up is not held up by a screening that takes a minute.
            CompletableFuture.runAsync(this::runDaily);
        }
    }

    /**
     * True if the schedule has already fired today but no daily run exists for today.
     * Package-private so it can be tested without a database.
     */
    boolean todaysRunMissed(LocalDateTime now, boolean hasRunToday) {
        if (hasRunToday) {
            return false;
        }
        LocalDateTime firstFiringToday = schedule.next(now.toLocalDate().atStartOfDay().minusSeconds(1));
        return firstFiringToday != null
                && firstFiringToday.toLocalDate().equals(now.toLocalDate())
                && !now.isBefore(firstFiringToday);
    }

    private boolean hasRunOn(LocalDate day) {
        return runs.findFirstByModeAndRunAtBetweenOrderByRunAtDesc(ScreeningService.MODE_DAILY,
                day.atStartOfDay(), day.plusDays(1).atStartOfDay().minusNanos(1)).isPresent();
    }

    @Scheduled(cron = "${kaksha.refresh-cron}")
    public void runDaily() {
        // If the download fails, the older catalog is still screened, just with a later start time.
        refreshCatalog();
        try {
            screening.screenAllIndian();
        } catch (RuntimeException e) {
            log.error("Daily screening failed", e);
        }
    }

    private void refreshCatalog() {
        try {
            catalog.refresh();
        } catch (IOException e) {
            log.error("Catalog refresh failed", e);
        }
    }
}
