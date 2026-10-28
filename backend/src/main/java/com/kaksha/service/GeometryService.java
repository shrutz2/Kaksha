package com.kaksha.service;

import com.kaksha.domain.ConjunctionEvent;
import com.kaksha.domain.OrbitalElements;
import com.kaksha.propagation.Sgp4Propagator;
import com.kaksha.propagation.StateVector;
import com.kaksha.repository.ConjunctionEventRepository;
import com.kaksha.repository.OrbitalElementsRepository;
import com.kaksha.web.dto.GeometryDto;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Samples the orbits of the two objects of an event so the globe can draw them. */
@Service
public class GeometryService {

    private static final double SECONDS_PER_DAY = 86400.0;
    private static final int RING_SEGMENTS = 180;

    private final ConjunctionEventRepository events;
    private final OrbitalElementsRepository elements;

    public GeometryService(ConjunctionEventRepository events, OrbitalElementsRepository elements) {
        this.events = events;
        this.elements = elements;
    }

    public GeometryDto forEvent(long eventId) {
        ConjunctionEvent event = events.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Unknown event " + eventId));
        Instant tca = event.getTca().toInstant(ZoneOffset.UTC);

        OrbitalElements primary = elementsOf(event.getPrimaryId());
        OrbitalElements secondary = elementsOf(event.getSecondaryId());

        StateVector meeting = new Sgp4Propagator(primary).propagate(tca);
        return new GeometryDto(tca, ring(primary, tca), ring(secondary, tca),
                new double[] {round(meeting.x()), round(meeting.y()), round(meeting.z())});
    }

    private OrbitalElements elementsOf(long noradId) {
        return elements.findById(noradId)
                .orElseThrow(() -> new NoSuchElementException("No orbital data for object " + noradId));
    }

    /** Positions over one orbital period from the given time, which close up into a ring. */
    static List<double[]> ring(OrbitalElements e, Instant from) {
        Sgp4Propagator propagator = new Sgp4Propagator(e);
        double periodSeconds = SECONDS_PER_DAY / e.getMeanMotion();

        List<double[]> points = new ArrayList<>(RING_SEGMENTS + 1);
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            long offsetNanos = Math.round(periodSeconds * i / RING_SEGMENTS * 1e9);
            StateVector s = propagator.propagate(from.plusNanos(offsetNanos));
            points.add(new double[] {round(s.x()), round(s.y()), round(s.z())});
        }
        return points;
    }

    private static double round(double km) {
        return Math.round(km * 10) / 10.0;
    }
}
