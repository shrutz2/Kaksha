package com.kaksha.web.dto;

import java.time.LocalDate;

/** An Indian satellite that is being watched. */
public record SatelliteDto(
        Long noradId,
        String name,
        String objectType,
        LocalDate launchDate,
        Double perigeeKm,
        Double apogeeKm) {
}
