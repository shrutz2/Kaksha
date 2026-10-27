package com.kaksha.screening;

import com.kaksha.catalog.GpParser;
import com.kaksha.catalog.GpRecord;
import com.kaksha.catalog.SatcatParser;
import com.kaksha.catalog.SatcatRecord;
import com.kaksha.config.ScreeningProperties;
import com.kaksha.domain.OrbitalElements;
import com.kaksha.domain.SpaceObject;
import com.kaksha.propagation.OrekitData;
import com.kaksha.propagation.Sgp4Propagator;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Compares the staged pipeline with a brute-force approach on the real catalog files.
 * It is skipped in a normal test run. To run it:
 *
 *   mvn test -Dtest=BaselineBenchmark -Dkaksha.benchmark=true
 *
 * Add -DargLine="-Djava.util.concurrent.ForkJoinPool.common.parallelism=0" to run the pipeline on a
 * single thread, which makes it comparable with the single-threaded brute-force estimates. With a
 * single thread, also add -Dkaksha.benchmark.skipOnDemand=true, otherwise the last part takes very long.
 *
 * The brute-force figures are estimates: the cost of one pair is measured on a small sample and
 * multiplied by the number of pairs. Nothing in this class is asserted, it only prints numbers.
 */
@EnabledIfSystemProperty(named = "kaksha.benchmark", matches = "true")
class BaselineBenchmark {

    private static final Path DATA = Path.of("data/celestrak");

    @Test
    void comparePipelineWithBruteForce() throws IOException {
        OrekitData.load(Path.of("data/orekit-data"));
        ScreeningProperties props = new ScreeningProperties(10, 60, 24, 48, 500, 50, 5, 0.01, false);
        int samples = props.horizonHours() * 3600 / props.stepSeconds() + 1;

        List<TrackedObject> all = new ArrayList<>();
        List<TrackedObject> primaries = new ArrayList<>();
        loadCatalog(all, primaries);
        Instant start = Instant.now();

        System.out.printf("%nCatalog: %d objects, %d Indian payloads, %d samples per track, threads available: %d%n",
                all.size(), primaries.size(), samples, Runtime.getRuntime().availableProcessors());

        // ---- The staged pipeline, as the application runs it ----
        ScreeningResult result = new ScreeningPipeline(props).run(primaries, all, start);
        long pairs = result.stages().get(0).itemsIn();

        Set<Long> primaryIds = new HashSet<>();
        primaries.forEach(p -> primaryIds.add(p.noradId()));
        Set<Integer> secondariesTracked = new HashSet<>();
        for (int s = 0; s < all.size(); s++) {
            for (TrackedObject p : primaries) {
                if (p.noradId() != all.get(s).noradId()
                        && AltitudeFilter.mayApproach(p.range(), all.get(s).range(), props.altitudeMarginKm())) {
                    secondariesTracked.add(s);
                    break;
                }
            }
        }

        // ---- Brute force: no altitude filter, and both tracks recomputed for every pair ----
        Random random = new Random(42);
        List<TrackedObject> sample = new ArrayList<>(all);
        Collections.shuffle(sample, random);
        sample = sample.subList(0, 150);
        List<TrackedObject> samplePrimaries = List.of(primaries.get(0), primaries.get(primaries.size() / 2));

        for (int i = 0; i < 20; i++) { // warm-up, not timed
            naivePair(samplePrimaries.get(0), sample.get(i), start, props.stepSeconds(), samples);
        }
        long t0 = System.nanoTime();
        int measuredPairs = 0;
        for (TrackedObject p : samplePrimaries) {
            for (TrackedObject s : sample) {
                naivePair(p, s, start, props.stepSeconds(), samples);
                measuredPairs++;
            }
        }
        double naivePairMs = (System.nanoTime() - t0) / 1e6 / measuredPairs;

        // Cost of comparing two tracks that are already computed.
        double[] a = new Sgp4Propagator(primaries.get(0).elements()).track(start, props.stepSeconds(), samples);
        double[] b = new Sgp4Propagator(sample.get(0).elements()).track(start, props.stepSeconds(), samples);
        long t1 = System.nanoTime();
        double sink = 0;
        int repeats = 20000;
        for (int r = 0; r < repeats; r++) {
            sink += compare(a, b, samples);
        }
        double comparePairMs = (System.nanoTime() - t1) / 1e6 / repeats;
        double trackMs = Math.max(0, (naivePairMs - comparePairMs) / 2);

        // Optional, takes a few minutes: the brute force really run for one satellite against every
        // object, instead of estimated from a small sample.
        if (Boolean.getBoolean("kaksha.benchmark.onePrimaryBruteForce")) {
            TrackedObject one = samplePrimaries.get(0);
            long t2 = System.nanoTime();
            int done = 0;
            for (TrackedObject s : all) {
                if (s.noradId() != one.noradId()) {
                    naivePair(one, s, start, props.stepSeconds(), samples);
                    done++;
                }
            }
            double seconds = (System.nanoTime() - t2) / 1e9;
            System.out.printf("MEASURED brute force, one satellite against %d objects, one thread: %.0f s%n", done, seconds);
            System.out.printf("  for all %d satellites that is %.1f hours%n", primaries.size(), seconds * primaries.size() / 3600);
        }

        double bruteForceSeconds = pairs * naivePairMs / 1000;
        double reuseOnlySeconds = ((double) (all.size() + primaries.size()) * trackMs + pairs * comparePairMs) / 1000;

        System.out.println();
        System.out.println("Pairs to screen (no filtering): " + pairs);
        System.out.printf("Measured on %d sample pairs: %.2f ms per pair when both tracks are recomputed%n",
                measuredPairs, naivePairMs);
        System.out.printf("  one 24 h track (1441 SGP4 positions): %.2f ms, comparing two tracks: %.3f ms%n",
                trackMs, comparePairMs);
        System.out.println();
        System.out.printf("Brute force, one thread (estimate):           %,.0f s = %.1f hours%n",
                bruteForceSeconds, bruteForceSeconds / 3600);
        System.out.printf("Tracks computed once each, no filter, one thread (estimate): %,.0f s = %.1f minutes%n",
                reuseOnlySeconds, reuseOnlySeconds / 60);
        System.out.printf("Staged pipeline, measured (%d secondaries tracked of %d): %.1f s%n",
                secondariesTracked.size(), all.size(), result.totalMillis() / 1000.0);
        System.out.printf("  speed-up over brute force: %,.0f times, over reuse-only: %.1f times%n",
                bruteForceSeconds * 1000 / result.totalMillis(), reuseOnlySeconds * 1000 / result.totalMillis());
        System.out.println("(sink " + sink + ")");

        if (!Boolean.getBoolean("kaksha.benchmark.skipOnDemand")) {
            onDemandWithAndWithoutFilter(primaries, all, start, props);
        }
    }

    /**
     * Screening one satellite, with and without the altitude filter. A margin of 100,000 km lets
     * every pair through, which is the same as having no filter. Ten satellites spread over the
     * list are used, and the median time is printed.
     */
    private static void onDemandWithAndWithoutFilter(List<TrackedObject> primaries, List<TrackedObject> all,
                                                     Instant start, ScreeningProperties props) {
        ScreeningProperties noFilter = new ScreeningProperties(100000, props.stepSeconds(), props.horizonHours(), props.dailyHorizonHours(),
                props.coarseGridThresholdKm(), props.screeningVolumeKm(), props.eventThresholdKm(),
                props.refineToleranceSeconds(), false);

        List<Long> withFilter = new ArrayList<>();
        List<Long> withoutFilter = new ArrayList<>();
        List<Integer> eventsWith = new ArrayList<>();
        List<Integer> eventsWithout = new ArrayList<>();
        for (int i = 0; i < primaries.size(); i += Math.max(1, primaries.size() / 10)) {
            List<TrackedObject> one = List.of(primaries.get(i));
            ScreeningResult on = new ScreeningPipeline(props).run(one, all, start);
            ScreeningResult off = new ScreeningPipeline(noFilter).run(one, all, start);
            withFilter.add(on.totalMillis());
            withoutFilter.add(off.totalMillis());
            eventsWith.add(on.events().size());
            eventsWithout.add(off.events().size());
        }
        System.out.println();
        System.out.printf("One satellite against the catalog (%d satellites tried):%n", withFilter.size());
        System.out.printf("  with altitude filter:    median %d ms, largest %d ms%n", median(withFilter), Collections.max(withFilter));
        System.out.printf("  without altitude filter: median %d ms, largest %d ms%n", median(withoutFilter), Collections.max(withoutFilter));
        System.out.println("  events found with filter:    " + eventsWith);
        System.out.println("  events found without filter: " + eventsWithout);
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    /** One pair done the slow way: both 24 h tracks are computed again, then compared. */
    private static void naivePair(TrackedObject p, TrackedObject s, Instant start, int step, int samples) {
        double[] a = new Sgp4Propagator(p.elements()).track(start, step, samples);
        double[] b = new Sgp4Propagator(s.elements()).track(start, step, samples);
        compare(a, b, samples);
    }

    /** Smallest distance on the grid, standing in for the scan of stage 2. */
    private static double compare(double[] a, double[] b, int samples) {
        double smallest = Double.MAX_VALUE;
        for (int i = 0; i < samples; i++) {
            double dx = a[3 * i] - b[3 * i];
            double dy = a[3 * i + 1] - b[3 * i + 1];
            double dz = a[3 * i + 2] - b[3 * i + 2];
            smallest = Math.min(smallest, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return smallest;
    }

    /** Reads the cached CelesTrak files the same way the application does. */
    static void loadCatalog(List<TrackedObject> all, List<TrackedObject> primaries) throws IOException {
        Map<Long, GpRecord> gpById = new HashMap<>();
        try (Reader r = Files.newBufferedReader(DATA.resolve("gp-active.csv"), StandardCharsets.UTF_8)) {
            for (GpRecord gp : GpParser.parse(r)) {
                gpById.put(gp.noradId(), gp);
            }
        }
        try (Reader r = Files.newBufferedReader(DATA.resolve("satcat.csv"), StandardCharsets.UTF_8)) {
            for (SatcatRecord s : SatcatParser.parse(r)) {
                GpRecord gp = gpById.get(s.noradId());
                if (gp == null || s.decayDate() != null) {
                    continue;
                }
                OrbitalElements e = new OrbitalElements(gp.noradId(), gp.epoch(), gp.meanMotion(),
                        gp.eccentricity(), gp.inclination(), gp.raan(), gp.argOfPericenter(),
                        gp.meanAnomaly(), gp.bstar(), gp.meanMotionDot(), gp.meanMotionDdot(),
                        gp.elementSetNo(), gp.revAtEpoch(), gp.ephemerisType(), gp.classification());
                TrackedObject tracked = TrackedObject.of(s.noradId(), s.name(), e);
                all.add(tracked);
                if (SpaceObject.OWNER_INDIA.equals(s.owner()) && SpaceObject.TYPE_PAYLOAD.equals(s.objectType())) {
                    primaries.add(tracked);
                }
            }
        }
    }
}
