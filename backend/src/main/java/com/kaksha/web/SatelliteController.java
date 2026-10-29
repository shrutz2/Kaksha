package com.kaksha.web;

import com.kaksha.service.EventService;
import com.kaksha.service.SatelliteService;
import com.kaksha.web.dto.EventDto;
import com.kaksha.web.dto.SatelliteDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/satellites")
public class SatelliteController {

    private final SatelliteService satellites;
    private final EventService events;

    public SatelliteController(SatelliteService satellites, EventService events) {
        this.satellites = satellites;
        this.events = events;
    }

    @GetMapping("/indian")
    public List<SatelliteDto> indian() {
        return satellites.indianSatellites();
    }

    @GetMapping("/{noradId}/events")
    public List<EventDto> eventsOf(@PathVariable long noradId) {
        return events.eventsForSatellite(noradId);
    }
}
