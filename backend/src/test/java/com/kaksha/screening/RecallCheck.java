package com.kaksha.screening;

import com.kaksha.config.ScreeningProperties;
import com.kaksha.propagation.OrekitData;
import com.kaksha.propagation.Sgp4Propagator;
import com.kaksha.propagation.StateVector;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Checks that the staged pipeline does not miss events.
 *
 * The pipeline is fast because it takes shortcuts: it drops pairs with an altitude filter, and it
 * looks at the sky only once a minute. This test does the same job the slow and careful way for a
 * few satellites: no altitude filter at all, and a look every 5 seconds. Then it compares the two
 * lists of events. Anything the careful scan finds and the pipeline does not is a missed event.
 *
 * It is skipped in a normal run and takes a few minutes. To run it:
 *
 *   mvn test -Dtest=RecallCheck -Dkaksha.recall=true
 *
 * Nothing is asserted, the numbers are printed.
 */
@EnabledIfSystemProperty(named = "kaksha.recall", matches = "true")
class RecallCheck {

    private static final int FINE_STEP_SECONDS = 5;
    // Two objects closing in at 15 km/s move 75 km between samples, so a pass under 5 km can show
    // up as 37 km on this grid. 60 km leaves some room.
    private static final double FINE_GRID_LIMIT_KM = 60;
    private static final long SAME_EVENT_SECONDS = 120;

    @Test
    void pipelineFindsWhatAFineScanFinds() throws IOException {
        OrekitData.load(Path.of("data/orekit-data"));
        ScreeningProperties props = new ScreeningProperties(10, 60, 24, 48, 500, 50, 5, 0.01, false);

        List<TrackedObject> all = new ArrayList<>();
        List<TrackedObject> primaries = new ArrayList<>();
        BaselineBenchmark.loadCatalog(all, primaries);
        Instant start = Instant.now();

        // The normal pipeline, for every Indian satellite
        ScreeningResult normal = new ScreeningPipeline(props).run(primaries, all, start);
        Map<Long, List<Conjunction>> pipelineEvents = new HashMap<>();
        for (Conjunction c : normal.events()) {
            pipelineEvents.computeIfAbsent(c.primaryId(), k -> new ArrayList<>()).add(c);
        }
        System.out.printf("%nPipeline: %d events for %d satellites in %.1f s%n",
                normal.events().size(), primaries.size(), normal.totalMillis() / 1000.0);

        // The satellites with most events, and one with none, to also test for false alarms
        List<TrackedObject> chosen = new ArrayList<>(primaries);
        chosen.sort(Comparator.comparingInt((TrackedObject p) ->
                pipelineEvents.getOrDefault(p.noradId(), List.of()).size()).reversed());
        List<TrackedObject> toCheck = new ArrayList<>(chosen.subList(0, 3));
        chosen.stream().filter(p -> !pipelineEvents.containsKey(p.noradId())).findFirst().ifPresent(toCheck::add);

        int missedTotal = 0;
        int fineTotal = 0;
        int bothTotal = 0;
        for (TrackedObject primary : toCheck) {
            long t0 = System.nanoTime();
            List<Conjunction> fine = fineScan(primary, all, start, props);
            List<Conjunction> fromPipeline = pipelineEvents.getOrDefault(primary.noradId(), List.of());

            int both = 0;
            List<Conjunction> missed = new ArrayList<>();
            for (Conjunction f : fine) {
                if (containsSame(fromPipeline, f)) {
                    both++;
                } else {
                    missed.add(f);
                }
            }
            List<Conjunction> extra = new ArrayList<>();
            for (Conjunction p : fromPipeline) {
                if (!containsSame(fine, p)) {
                    extra.add(p);
                }
            }
            System.out.printf("%n%s (%d): pipeline %d events, fine scan %d events, found by both %d, "
                            + "missed by the pipeline %d, only in the pipeline %d   [fine scan took %.0f s]%n",
                    primary.name(), primary.noradId(), fromPipeline.size(), fine.size(), both,
                    missed.size(), extra.size(), (System.nanoTime() - t0) / 1e9);
            missed.stream().limit(5).forEach(m -> System.out.printf("   missed: object %d, %s, %.2f km%n",
                    m.secondaryId(), m.tca(), m.missKm()));
            extra.stream().limit(5).forEach(m -> System.out.printf("   only in the pipeline: object %d, %s, %.2f km%n",
                    m.secondaryId(), m.tca(), m.missKm()));
            missedTotal += missed.size();
            fineTotal += fine.size();
            bothTotal += both;
        }
        System.out.printf("%nTotal over %d satellites: the fine scan found %d events, the pipeline found %d of them, "
                + "missed %d%n", toCheck.size(), fineTotal, bothTotal, missedTotal);
    }

    private static boolean containsSame(List<Conjunction> events, Conjunction wanted) {
        for (Conjunction e : events) {
            if (e.secondaryId() == wanted.secondaryId()
                    && Math.abs(e.tca().getEpochSecond() - wanted.tca().getEpochSecond()) <= SAME_EVENT_SECONDS) {
                return true;
            }
        }
        return false;
    }

    /** One satellite against every object: a position every 5 seconds, no altitude filter. */
    private static List<Conjunction> fineScan(TrackedObject primary, List<TrackedObject> all, Instant start,
                                              ScreeningProperties props) {
        int horizonSeconds = props.horizonHours() * 3600;
        int samples = horizonSeconds / FINE_STEP_SECONDS + 1;
        double[] primaryTrack = new Sgp4Propagator(primary.elements()).track(start, FINE_STEP_SECONDS, samples);

        return all.parallelStream()
                .filter(secondary -> secondary.noradId() != primary.noradId())
                .flatMap(secondary -> scanOne(primary, secondary, primaryTrack, start, samples, props))
                .toList();
    }

    private static Stream<Conjunction> scanOne(TrackedObject primary, TrackedObject secondary,
                                               double[] primaryTrack, Instant start, int samples,
                                               ScreeningProperties props) {
        double[] track;
        try {
            track = new Sgp4Propagator(secondary.elements()).track(start, FINE_STEP_SECONDS, samples);
        } catch (RuntimeException e) {
            return Stream.empty();
        }
        double[] distance = new double[samples];
        for (int i = 0; i < samples; i++) {
            double dx = primaryTrack[3 * i] - track[3 * i];
            double dy = primaryTrack[3 * i + 1] - track[3 * i + 1];
            double dz = primaryTrack[3 * i + 2] - track[3 * i + 2];
            distance[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        List<Conjunction> found = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            boolean notHigherThanBefore = i == 0 || distance[i] <= distance[i - 1];
            boolean notHigherThanAfter = i == samples - 1 || distance[i] <= distance[i + 1];
            if (notHigherThanBefore && notHigherThanAfter && distance[i] < FINE_GRID_LIMIT_KM) {
                Sgp4Propagator a = new Sgp4Propagator(primary.elements());
                Sgp4Propagator b = new Sgp4Propagator(secondary.elements());
                double low = Math.max(0, (double) (i - 1) * FINE_STEP_SECONDS);
                double high = Math.min(props.horizonHours() * 3600.0, (double) (i + 1) * FINE_STEP_SECONDS);
                TcaRefiner.Approach approach = TcaRefiner.refine(seconds -> {
                    Instant t = start.plusNanos(Math.round(seconds * 1e9));
                    return a.propagate(t).distanceTo(b.propagate(t));
                }, low, high, props.refineToleranceSeconds());

                if (approach.missKm() < props.eventThresholdKm()) {
                    Instant tca = start.plusNanos(Math.round(approach.secondsFromStart() * 1e9));
                    StateVector sa = a.propagate(tca);
                    StateVector sb = b.propagate(tca);
                    found.add(new Conjunction(primary.noradId(), secondary.noradId(), tca, approach.missKm(),
                            sa.relativeSpeedTo(sb), RiskLevel.fromMissKm(approach.missKm())));
                }
            }
        }
        return found.stream();
    }
}
