package com.kaksha.web;

import com.kaksha.service.StatsService;
import com.kaksha.web.dto.StatsDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/last-run")
    public StatsDto lastRun() {
        return stats.lastRun();
    }
}
