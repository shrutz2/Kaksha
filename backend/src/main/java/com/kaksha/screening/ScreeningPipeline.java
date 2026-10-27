package com.kaksha.screening;

import com.kaksha.config.ScreeningProperties;
import com.kaksha.propagation.Sgp4Propagator;
import com.kaksha.propagation.StateVector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds close approaches between "primary" objects (the satellites we care about) and
 * "secondary" objects (everything else in orbit) over the next few hours.
 *
 * Checking every pair at every moment would take far too long, so the work is cut down in
 * stages. Each stage is cheap compared to the next one and removes most of the pairs:
 *
 *   1. Altitude filter   - drop pairs whose altitude ranges never overlap.
 *   2. Coarse scan       - predict both objects every minute and note where they get closest.
 *   3. Refinement        - zoom in on each of those spots to find the exact time and distance.
 *   4. Flagging          - keep approaches under the event limit and label their risk.
 *
 * The pipeline itself does not touch the database, which keeps it easy to test.
 */
public class ScreeningPipeline {

    private static final Logger log = LoggerFactory.getLogger(ScreeningPipeline.class);

    private final ScreeningProperties props;

    public ScreeningPipeline(ScreeningProperties props) {
        this.props = props;
    }

    /** Screens for the horizon of an on-demand run, props.horizonHours(). */
    public ScreeningResult run(List<TrackedObject> primaries, List<TrackedObject> secondaries, Instant start) {
        return run(primaries, secondaries, start, props.horizonHours());
    }

    public ScreeningResult run(List<TrackedObject> primaries, List<TrackedObject> secondaries, Instant start,
                               int horizonHours) {
        long runStarted = System.nanoTime();
        AtomicInteger propagationFailures = new AtomicInteger();

        // ---------------- Stage 1: altitude filter ----------------
        long stageStarted = System.nanoTime();
        Set<Long> primaryIds = new HashSet<>();
        primaries.forEach(p -> primaryIds.add(p.noradId()));

        // For every secondary, the primaries it still has to be compared with.
        Map<Integer, List<Integer>> primariesBySecondary = new HashMap<>();
        long totalPairs = 0;
        long pairsAfterAltitude = 0;
        for (int p = 0; p < primaries.size(); p++) {
            for (int s = 0; s < secondaries.size(); s++) {
                if (!isNewPair(primaries.get(p), secondaries.get(s), primaryIds)) {
                    continue;
                }
                totalPairs++;
                if (AltitudeFilter.mayApproach(primaries.get(p).range(), secondaries.get(s).range(),
                        props.altitudeMarginKm())) {
                    pairsAfterAltitude++;
                    primariesBySecondary.computeIfAbsent(s, k -> new ArrayList<>()).add(p);
                }
            }
        }
        StageStats stage1 = new StageStats("1 altitude filter", totalPairs, pairsAfterAltitude, millisSince(stageStarted));

        // ---------------- Stage 2: coarse scan ----------------
        stageStarted = System.nanoTime();
        int horizonSeconds = horizonHours * 3600;
        int samples = horizonSeconds / props.stepSeconds() + 1;

        // Each primary is predicted once and shared by all its pairs.
        double[][] primaryTracks = new double[primaries.size()][];
        for (int p = 0; p < primaries.size(); p++) {
            try {
                primaryTracks[p] = new Sgp4Propagator(primaries.get(p).elements())
                        .track(start, props.stepSeconds(), samples);
            } catch (RuntimeException e) {
                propagationFailures.incrementAndGet();
                log.warn("Could not propagate primary {}: {}", primaries.get(p).noradId(), e.toString());
            }
        }

        // Secondaries are independent of each other, so they are scanned in parallel.
        List<Candidate> candidates = primariesBySecondary.entrySet().parallelStream()
                .flatMap(entry -> scanSecondary(entry.getKey(), entry.getValue(), secondaries,
                        primaryTracks, start, samples, propagationFailures).stream())
                .toList();

        Set<Long> pairsWithCandidate = new HashSet<>();
        for (Candidate c : candidates) {
            pairsWithCandidate.add((long) c.primary() * secondaries.size() + c.secondary());
        }
        StageStats stage2 = new StageStats("2 coarse scan", pairsAfterAltitude, pairsWithCandidate.size(), millisSince(stageStarted));

        // ---------------- Stage 3: refinement ----------------
        stageStarted = System.nanoTime();
        List<Refined> refined = candidates.parallelStream()
                .map(c -> refine(c, primaries, secondaries, start, horizonSeconds, propagationFailures))
                .filter(Objects::nonNull)
                .filter(r -> r.missKm() < props.screeningVolumeKm())
                .toList();
        StageStats stage3 = new StageStats("3 refinement", candidates.size(), refined.size(), millisSince(stageStarted));

        // ---------------- Stage 4: flagging ----------------
        stageStarted = System.nanoTime();
        List<Conjunction> events = refined.stream()
                .filter(r -> r.missKm() < props.eventThresholdKm())
                .map(r -> new Conjunction(r.primaryId(), r.secondaryId(), r.tca(), r.missKm(),
                        r.relativeSpeedKmS(), RiskLevel.fromMissKm(r.missKm())))
                .sorted(Comparator.comparingDouble(Conjunction::missKm))
                .toList();
        StageStats stage4 = new StageStats("4 flagging", refined.size(), events.size(), millisSince(stageStarted));

        long totalMillis = millisSince(runStarted);
        ScreeningResult result = new ScreeningResult(primaries.size(), secondaries.size(),
                List.of(stage1, stage2, stage3, stage4), totalMillis, propagationFailures.get(), events);
        logBenchmark(result);
        return result;
    }

    /**
     * A pair is only screened once. An object is never paired with itself, and when both
     * objects are primaries the pair is counted for the one with the lower id only.
     */
    private static boolean isNewPair(TrackedObject primary, TrackedObject secondary, Set<Long> primaryIds) {
        if (primary.noradId() == secondary.noradId()) {
            return false;
        }
        return !(primaryIds.contains(secondary.noradId()) && secondary.noradId() < primary.noradId());
    }

    /**
     * Predicts one secondary every step and compares it with each primary it was paired with.
     *
     * The step is 60 seconds, and two objects in low orbit can close in on each other at up
     * to about 15 km/s, so they move about 900 km relative to each other between two samples.
     * A pass that is really 5 km wide can therefore show up as 450 km on the grid. For that
     * reason the grid test cannot use the 5 km (or 50 km) limit directly. It only looks for
     * local minima of the sampled distance and keeps those under a generous threshold; the
     * exact distance is worked out in stage 3.
     */
    private List<Candidate> scanSecondary(int s, List<Integer> pairedPrimaries, List<TrackedObject> secondaries,
                                          double[][] primaryTracks, Instant start, int samples,
                                          AtomicInteger propagationFailures) {
        double[] secondaryTrack;
        try {
            secondaryTrack = new Sgp4Propagator(secondaries.get(s).elements())
                    .track(start, props.stepSeconds(), samples);
        } catch (RuntimeException e) {
            // Some catalog entries cannot be propagated (for example elements of decayed objects).
            propagationFailures.incrementAndGet();
            return List.of();
        }

        List<Candidate> found = new ArrayList<>();
        double[] distance = new double[samples];
        for (int p : pairedPrimaries) {
            double[] primaryTrack = primaryTracks[p];
            if (primaryTrack == null) {
                continue;
            }
            for (int i = 0; i < samples; i++) {
                double dx = primaryTrack[3 * i] - secondaryTrack[3 * i];
                double dy = primaryTrack[3 * i + 1] - secondaryTrack[3 * i + 1];
                double dz = primaryTrack[3 * i + 2] - secondaryTrack[3 * i + 2];
                distance[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            for (int i = 0; i < samples; i++) {
                boolean notHigherThanBefore = i == 0 || distance[i] <= distance[i - 1];
                boolean notHigherThanAfter = i == samples - 1 || distance[i] <= distance[i + 1];
                if (notHigherThanBefore && notHigherThanAfter && distance[i] < props.coarseGridThresholdKm()) {
                    found.add(new Candidate(p, s, i));
                }
            }
        }
        return found;
    }

    /** Searches the time window around a coarse minimum for the exact closest approach. */
    private Refined refine(Candidate c, List<TrackedObject> primaries, List<TrackedObject> secondaries,
                           Instant start, int horizonSeconds, AtomicInteger propagationFailures) {
        TrackedObject primary = primaries.get(c.primary());
        TrackedObject secondary = secondaries.get(c.secondary());
        try {
            // Each task builds its own propagators, so nothing is shared between threads.
            Sgp4Propagator a = new Sgp4Propagator(primary.elements());
            Sgp4Propagator b = new Sgp4Propagator(secondary.elements());

            // The true minimum lies between the sample before and the sample after the coarse one.
            double windowStart = Math.max(0, (double) (c.sampleIndex() - 1) * props.stepSeconds());
            double windowEnd = Math.min(horizonSeconds, (double) (c.sampleIndex() + 1) * props.stepSeconds());

            DoubleUnaryOperator distanceAt = seconds -> {
                Instant t = offset(start, seconds);
                return a.propagate(t).distanceTo(b.propagate(t));
            };
            TcaRefiner.Approach approach = TcaRefiner.refine(distanceAt, windowStart, windowEnd,
                    props.refineToleranceSeconds());

            Instant tca = offset(start, approach.secondsFromStart());
            StateVector stateA = a.propagate(tca);
            StateVector stateB = b.propagate(tca);
            return new Refined(primary.noradId(), secondary.noradId(), tca, approach.missKm(),
                    stateA.relativeSpeedTo(stateB));
        } catch (RuntimeException e) {
            propagationFailures.incrementAndGet();
            return null;
        }
    }

    private static Instant offset(Instant start, double seconds) {
        return start.plusNanos(Math.round(seconds * 1e9));
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private void logBenchmark(ScreeningResult r) {
        log.info("Screening finished in {} ms: {} primaries, {} secondaries, {} events, {} propagation failures",
                r.totalMillis(), r.primaries(), r.secondaries(), r.events().size(), r.propagationFailures());
        for (StageStats s : r.stages()) {
            log.info("  stage {}: {} in, {} out, {} ms", s.name(), s.itemsIn(), s.itemsOut(), s.millis());
        }
    }

    /** A spot on the coarse grid where a pair was at a local minimum of distance. */
    private record Candidate(int primary, int secondary, int sampleIndex) {
    }

    /** A candidate after the exact closest approach has been found. */
    private record Refined(long primaryId, long secondaryId, Instant tca, double missKm, double relativeSpeedKmS) {
    }
}
