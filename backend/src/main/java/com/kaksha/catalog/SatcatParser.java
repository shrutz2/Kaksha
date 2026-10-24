package com.kaksha.catalog;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Turns the SATCAT csv into {@link SatcatRecord} objects. */
public final class SatcatParser {

    private SatcatParser() {
    }

    public static List<SatcatRecord> parse(Reader reader) throws IOException {
        CsvTable table = CsvTable.read(reader);
        List<SatcatRecord> records = new ArrayList<>(table.size());

        for (int i = 0; i < table.size(); i++) {
            CsvTable.Row row = table.row(i);
            String decay = row.get("DECAY_DATE");
            String launch = row.get("LAUNCH_DATE");

            records.add(new SatcatRecord(
                    Long.parseLong(row.get("NORAD_CAT_ID")),
                    row.get("OBJECT_NAME"),
                    row.get("OBJECT_ID"),
                    row.get("OBJECT_TYPE"),
                    row.get("OPS_STATUS_CODE"),
                    row.get("OWNER"),
                    launch == null ? null : LocalDate.parse(launch),
                    decay == null ? null : LocalDate.parse(decay),
                    row.getDouble("APOGEE"),
                    row.getDouble("PERIGEE")));
        }
        return records;
    }
}
