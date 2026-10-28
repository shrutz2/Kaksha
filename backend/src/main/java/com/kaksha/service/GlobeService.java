package com.kaksha.service;

import com.kaksha.domain.OrbitalElements;
import com.kaksha.domain.SpaceObject;
import com.kaksha.propagation.Sgp4Propagator;
import com.kaksha.propagation.StateVector;
import com.kaksha.repository.OrbitalElementsRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.web.dto.GlobeSnapshotDto;
import com.kaksha.web.dto.GlobeSnapshotDto.GlobeObject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Supplies the positions shown on the live globe: every Indian satellite plus an even
 * sample of the other objects. Drawing all 16,000 would only slow the browser down.
 *
 * Building a propagator costs more than using it, so the propagators are kept in memory
 * and rebuilt once an hour, which picks up a freshly downloaded catalog.
 */
@Service
public class GlobeService {

    private static final Logger log = LoggerFactory.getLogger(GlobeService.class);
    private static final Duration REBUILD_AFTER = Duration.ofHours(1);

    private final SpaceObjectRepository objects;
    private final OrbitalElementsRepository elements;
    private final int sampleSize;

    private List<Tracked> tracked = List.of();
    private Instant builtAt = Instant.MIN;

    public GlobeService(SpaceObjectRepository objects, OrbitalElementsRepository elements,
                        @Value("${kaksha.globe.sample-size}") int sampleSize) {
        this.objects = objects;
        this.elements = elements;
        this.sampleSize = sampleSize;
    }

    /** Propagators are not safe to share between threads, so requests take turns. */
    public synchronized GlobeSnapshotDto snapshot() {
        Instant now = Instant.now();
        if (Duration.between(builtAt, now).compareTo(REBUILD_AFTER) > 0 || tracked.isEmpty()) {
            tracked = build();
            builtAt = now;
        }

        List<GlobeObject> result = new ArrayList<>(tracked.size());
        for (Tracked t : tracked) {
            try {
                StateVector s = t.propagator().propagate(now);
                result.add(new GlobeObject(t.id(), t.name(), t.indian(),
                        new double[] {round(s.x(), 10), round(s.y(), 10), round(s.z(), 10)},
                        new double[] {round(s.vx(), 10000), round(s.vy(), 10000), round(s.vz(), 10000)}));
            } catch (RuntimeException e) {
                // An object whose elements cannot be propagated is simply left off the globe.
            }
        }
        return new GlobeSnapshotDto(now, result);
    }

    private List<Tracked> build() {
        Map<Long, OrbitalElements> elementsById = new HashMap<>();
        for (OrbitalElements e : elements.findAll()) {
            elementsById.put(e.getNoradId(), e);
        }

        List<SpaceObject> indian = new ArrayList<>();
        List<SpaceObject> others = new ArrayList<>();
        for (SpaceObject o : objects.findAll()) {
            if (!elementsById.containsKey(o.getNoradId())) {
                continue;
            }
            (o.isIndianPayload() ? indian : others).add(o);
        }

        // Every n-th object by id gives an even sample that stays the same between rebuilds.
        others.sort(Comparator.comparing(SpaceObject::getNoradId));
        int step = Math.max(1, (int) Math.ceil(others.size() / (double) Math.max(1, sampleSize)));

        List<Tracked> result = new ArrayList<>();
        indian.forEach(o -> addTracked(result, o, true, elementsById));
        for (int i = 0; i < others.size(); i += step) {
            addTracked(result, others.get(i), false, elementsById);
        }
        log.info("Globe objects built: {} Indian, {} others", indian.size(), result.size() - indian.size());
        return result;
    }

    private static void addTracked(List<Tracked> list, SpaceObject o, boolean indian,
                                   Map<Long, OrbitalElements> elementsById) {
        try {
            list.add(new Tracked(o.getNoradId(), o.getName(), indian,
                    new Sgp4Propagator(elementsById.get(o.getNoradId()))));
        } catch (RuntimeException e) {
            // skip objects whose elements Orekit rejects
        }
    }

    private static double round(double value, double factor) {
        return Math.round(value * factor) / factor;
    }

    private record Tracked(long id, String name, boolean indian, Sgp4Propagator propagator) {
    }
}
