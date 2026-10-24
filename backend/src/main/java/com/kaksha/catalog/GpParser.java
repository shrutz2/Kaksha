package com.kaksha.catalog;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the GP csv (the OMM mean elements CelesTrak publishes) into {@link GpRecord} objects.
 *
 * The csv format is used instead of the classic two-line TLE because TLE cannot hold
 * catalog numbers above 99999, and new objects are now past that limit.
 */
public final class GpParser {

    private GpParser() {
    }

    public static List<GpRecord> parse(Reader reader) throws IOException {
        CsvTable table = CsvTable.read(reader);
        List<GpRecord> records = new ArrayList<>(table.size());

        for (int i = 0; i < table.size(); i++) {
            CsvTable.Row row = table.row(i);
            records.add(new GpRecord(
                    Long.parseLong(row.get("NORAD_CAT_ID")),
                    LocalDateTime.parse(row.get("EPOCH")),
                    row.getDouble("MEAN_MOTION"),
                    row.getDouble("ECCENTRICITY"),
                    row.getDouble("INCLINATION"),
                    row.getDouble("RA_OF_ASC_NODE"),
                    row.getDouble("ARG_OF_PERICENTER"),
                    row.getDouble("MEAN_ANOMALY"),
                    row.getDouble("BSTAR"),
                    row.getDouble("MEAN_MOTION_DOT"),
                    row.getDouble("MEAN_MOTION_DDOT"),
                    Integer.parseInt(row.get("ELEMENT_SET_NO")),
                    Integer.parseInt(row.get("REV_AT_EPOCH")),
                    Integer.parseInt(row.get("EPHEMERIS_TYPE")),
                    row.get("CLASSIFICATION_TYPE")));
        }
        return records;
    }
}
