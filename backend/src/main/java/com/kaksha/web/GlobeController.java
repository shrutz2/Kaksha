package com.kaksha.web;

import com.kaksha.service.GlobeService;
import com.kaksha.web.dto.GlobeSnapshotDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/globe")
public class GlobeController {

    private final GlobeService globe;

    public GlobeController(GlobeService globe) {
        this.globe = globe;
    }

    /** Current positions for the live globe. */
    @GetMapping("/objects")
    public GlobeSnapshotDto objects() {
        return globe.snapshot();
    }
}
