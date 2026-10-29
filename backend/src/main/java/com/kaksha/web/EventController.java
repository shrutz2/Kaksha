package com.kaksha.web;

import com.kaksha.service.EventService;
import com.kaksha.service.GeometryService;
import com.kaksha.web.dto.EventDto;
import com.kaksha.web.dto.GeometryDto;
import com.kaksha.web.dto.PredictionDto;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService events;
    private final GeometryService geometry;

    public EventController(EventService events, GeometryService geometry) {
        this.events = events;
        this.geometry = geometry;
    }

    /** The two orbits and the meeting point of one event, for drawing on the globe. */
    @GetMapping("/{id}/geometry")
    public GeometryDto geometry(@PathVariable long id) {
        return geometry.forEvent(id);
    }

    /** What each screening run predicted for this approach, oldest first. */
    @GetMapping("/{id}/history")
    public List<PredictionDto> history(@PathVariable long id) {
        return events.predictionHistory(id);
    }

    /** GET /api/events?date=2026-10-23 . Without a date, today (UTC) is used. */
    @GetMapping
    public List<EventDto> eventsForDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return events.eventsForDate(day);
    }
}
