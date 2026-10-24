package com.kaksha.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal CSV reader for the CelesTrak files. The first line is the header and
 * columns are looked up by name, so the order of columns in the file does not matter.
 */
public class CsvTable {

    private final Map<String, Integer> columnIndex = new HashMap<>();
    private final List<String[]> rows = new ArrayList<>();

    public static CsvTable read(Reader reader) throws IOException {
        CsvTable table = new CsvTable();
        BufferedReader in = new BufferedReader(reader);

        String header = in.readLine();
        if (header == null) {
            throw new IOException("CSV file is empty");
        }
        String[] names = splitLine(header);
        for (int i = 0; i < names.length; i++) {
            table.columnIndex.put(names[i].trim(), i);
        }

        String line;
        while ((line = in.readLine()) != null) {
            if (!line.isBlank()) {
                table.rows.add(splitLine(line));
            }
        }
        return table;
    }

    public boolean hasColumn(String name) {
        return columnIndex.containsKey(name);
    }

    public int size() {
        return rows.size();
    }

    public Row row(int i) {
        return new Row(rows.get(i));
    }

    /** Splits one line on commas, but keeps commas that are inside double quotes. */
    static String[] splitLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                insideQuotes = !insideQuotes;
            } else if (c == ',' && !insideQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    public class Row {
        private final String[] cells;

        private Row(String[] cells) {
            this.cells = cells;
        }

        /** Returns the cell text, or null when the cell is empty or the row is short. */
        public String get(String column) {
            Integer idx = columnIndex.get(column);
            if (idx == null) {
                throw new IllegalArgumentException("Missing column: " + column);
            }
            if (idx >= cells.length) {
                return null;
            }
            String value = cells[idx].trim();
            return value.isEmpty() ? null : value;
        }

        public Double getDouble(String column) {
            String value = get(column);
            return value == null ? null : Double.valueOf(value);
        }
    }
}
