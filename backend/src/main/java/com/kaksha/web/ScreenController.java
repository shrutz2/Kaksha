package com.kaksha.web;

import com.kaksha.domain.ScreeningRun;
import com.kaksha.service.EventService;
import com.kaksha.service.ScreeningService;
import com.kaksha.web.dto.ScreenResponseDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screen")
public class ScreenController {

    private final ScreeningService screening;
    private final EventService events;

    public ScreenController(ScreeningService screening, EventService events) {
        this.screening = screening;
        this.events = events;
    }

    /** Screens one satellite against the whole catalog right now. */
    @PostMapping("/{noradId}")
    public ScreenResponseDto screen(@PathVariable long noradId) {
        ScreeningRun run = screening.screenOne(noradId);
        return new ScreenResponseDto(noradId, run.getSecondaries(), run.getEventsFlagged(),
                run.getTotalMillis(), events.eventsOfRun(run.getId()));
    }
}
