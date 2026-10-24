package com.kaksha.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParserTest {

    private static final String SATCAT = """
            OBJECT_NAME,OBJECT_ID,NORAD_CAT_ID,OBJECT_TYPE,OPS_STATUS_CODE,OWNER,LAUNCH_DATE,LAUNCH_SITE,DECAY_DATE,PERIOD,INCLINATION,APOGEE,PERIGEE,RCS,DATA_STATUS_CODE,ORBIT_CENTER,ORBIT_TYPE
            SPUTNIK 1,1957-001B,2,PAY,D,CIS,1957-10-04,TYMSC,1958-01-03,96.10,65.00,1080,64,,,EA,IMP
            "CARTOSAT-3, TEST",2019-081A,44804,PAY,+,IND,2019-11-27,SHAR,,97.20,97.50,509,504,,,EA,ORB
            """;

    private static final String GP = """
            OBJECT_NAME,OBJECT_ID,EPOCH,MEAN_MOTION,ECCENTRICITY,INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY,EPHEMERIS_TYPE,CLASSIFICATION_TYPE,NORAD_CAT_ID,ELEMENT_SET_NO,REV_AT_EPOCH,BSTAR,MEAN_MOTION_DOT,MEAN_MOTION_DDOT
            CARTOSAT-3,2019-081A,2026-10-21T08:15:30.123456,14.80,0.0001234,97.5,120.25,90.0,270.0,0,U,44804,999,32000,0.00004,0.0000012,0
            """;

    // First rows of the real CelesTrak GP file (GROUP=active, FORMAT=csv, 23 Oct 2026). Note the
    // numbers without a leading zero (.00245855) and the exponent form (-.64396277E-3).
    private static final String REAL_GP = """
            OBJECT_NAME,OBJECT_ID,EPOCH,MEAN_MOTION,ECCENTRICITY,INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY,EPHEMERIS_TYPE,CLASSIFICATION_TYPE,NORAD_CAT_ID,ELEMENT_SET_NO,REV_AT_EPOCH,BSTAR,MEAN_MOTION_DOT,MEAN_MOTION_DDOT
            CALSPHERE 1,1964-063C,2026-10-23T04:24:34.329024,13.76740481,.00245855,90.2215,74.7349,263.4265,272.0310,0,U,900,999,8903,.55859276E-3,.559E-5,0
            LCS 1,1965-034C,2026-10-23T05:04:33.627648,9.89311105,.00108387,32.1395,233.0546,217.9669,142.0065,0,U,1361,999,22256,-.64396277E-3,.4E-7,0
            OPS 5712 (P/L 160),1967-053A,2026-10-23T04:36:39.015936,14.79135301,.00032721,69.9148,84.1867,263.4806,96.5970,0,U,2826,999,8515,.10844214E-2,.7981E-4,0
            """;

    @Test
    void gpParserReadsRealFileFormat() throws IOException {
        List<GpRecord> rows = GpParser.parse(new StringReader(REAL_GP));

        assertEquals(3, rows.size());
        GpRecord first = rows.get(0);
        assertEquals(900L, first.noradId());
        assertEquals(0.00245855, first.eccentricity(), 1e-12);
        assertEquals(0.55859276e-3, first.bstar(), 1e-12);
        assertEquals(LocalDateTime.parse("2026-10-23T04:24:34.329024"), first.epoch());

        assertEquals(-0.64396277e-3, rows.get(1).bstar(), 1e-12);
        assertEquals(2826L, rows.get(2).noradId());
    }

    @Test
    void satcatParserReadsQuotedNamesAndEmptyCells() throws IOException {
        List<SatcatRecord> rows = SatcatParser.parse(new StringReader(SATCAT));

        assertEquals(2, rows.size());
        assertEquals(LocalDate.of(1958, 1, 3), rows.get(0).decayDate());

        SatcatRecord indian = rows.get(1);
        assertEquals("CARTOSAT-3, TEST", indian.name());
        assertEquals("IND", indian.owner());
        assertNull(indian.decayDate());
        assertEquals(509.0, indian.apogeeKm());
    }

    @Test
    void gpParserReadsElementsByColumnName() throws IOException {
        List<GpRecord> rows = GpParser.parse(new StringReader(GP));

        assertEquals(1, rows.size());
        GpRecord gp = rows.get(0);
        assertEquals(44804L, gp.noradId());
        assertEquals(LocalDateTime.parse("2026-10-21T08:15:30.123456"), gp.epoch());
        assertEquals(14.80, gp.meanMotion(), 1e-9);
        assertEquals(97.5, gp.inclination(), 1e-9);
        assertEquals("U", gp.classification());
    }
}
