package com.kaksha.catalog;

import java.time.LocalDate;

/** One row of the CelesTrak SATCAT: who an object is, who owns it and its rough orbit size. */
public record SatcatRecord(
        long noradId,
        String name,
        String intlDesignator,
        String objectType,
        String opsStatus,
        String owner,
        LocalDate launchDate,
        LocalDate decayDate,
        Double apogeeKm,
        Double perigeeKm) {
}
