package com.kaksha.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaksha.catalog.CelestrakClient;
import com.kaksha.config.CelestrakProperties;
import com.kaksha.domain.OrbitalElements;
import com.kaksha.repository.OrbitalElementsRepository;
import com.kaksha.repository.SpaceObjectRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Loading the catalog from several CelesTrak groups. */
class CatalogGroupsTest {

    private static final String GP_HEADER = "OBJECT_NAME,OBJECT_ID,EPOCH,MEAN_MOTION,ECCENTRICITY,INCLINATION,"
            + "RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY,EPHEMERIS_TYPE,CLASSIFICATION_TYPE,NORAD_CAT_ID,"
            + "ELEMENT_SET_NO,REV_AT_EPOCH,BSTAR,MEAN_MOTION_DOT,MEAN_MOTION_DDOT\n";
    private static final String SATCAT_HEADER = "OBJECT_NAME,OBJECT_ID,NORAD_CAT_ID,OBJECT_TYPE,OPS_STATUS_CODE,OWNER,"
            + "LAUNCH_DATE,LAUNCH_SITE,DECAY_DATE,PERIOD,INCLINATION,APOGEE,PERIGEE,RCS,DATA_STATUS_CODE,ORBIT_CENTER,ORBIT_TYPE\n";

    @TempDir
    Path dir;

    private CelestrakClient client;
    private SpaceObjectRepository objects;
    private OrbitalElementsRepository elements;
    private CatalogService service;

    private static String gpRow(long id, String epoch, double meanMotion) {
        return "OBJ" + id + ",2000-001A," + epoch + "," + meanMotion + ",0.001,98.0,10,20,30,0,U," + id + ",1,1,0,0,0\n";
    }

    private static String satcatRow(long id, String type) {
        return "OBJ" + id + ",2000-001A," + id + "," + type + ",+,US,2000-01-01,AFETR,,95,98,500,490,,,EA,ORB\n";
    }

    @BeforeEach
    void setUp() throws IOException {
        client = mock(CelestrakClient.class);
        objects = mock(SpaceObjectRepository.class);
        elements = mock(OrbitalElementsRepository.class);
        CelestrakProperties props = new CelestrakProperties("http://x/{group}", List.of("active", "debris", "broken"),
                "http://x/satcat", dir, Duration.ofHours(23));
        service = new CatalogService(client, props, objects, elements);

        Path satcat = dir.resolve("satcat.csv");
        Files.writeString(satcat, SATCAT_HEADER + satcatRow(1, "PAY") + satcatRow(2, "DEB") + satcatRow(3, "DEB"));
        when(client.satcatFile()).thenReturn(satcat);
    }

    private Path gpFile(String name, String... rows) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, GP_HEADER + String.join("", rows));
        return file;
    }

    @Test
    void objectsFromAllGroupsAreStoredAndTheNewestElementsWin() throws IOException {
        when(client.gpFile("active")).thenReturn(gpFile("a.csv",
                gpRow(1, "2026-10-23T01:00:00", 15.0), gpRow(2, "2026-10-23T01:00:00", 14.0)));
        when(client.gpFile("debris")).thenReturn(gpFile("d.csv",
                gpRow(2, "2026-10-23T09:00:00", 14.5), gpRow(3, "2026-10-23T02:00:00", 13.0)));
        when(client.gpFile("broken")).thenThrow(new IOException("HTTP 403"));

        int stored = service.refresh();

        assertEquals(3, stored);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrbitalElements>> saved = ArgumentCaptor.forClass(List.class);
        verify(elements).saveAll(saved.capture());
        OrbitalElements two = saved.getValue().stream().filter(e -> e.getNoradId() == 2).findFirst().orElseThrow();
        assertEquals(14.5, two.getMeanMotion(), 1e-9);
    }

    @Test
    void anOptionalGroupThatCannotBeLoadedIsSkipped() throws IOException {
        when(client.gpFile("active")).thenReturn(gpFile("a.csv", gpRow(1, "2026-10-23T01:00:00", 15.0)));
        when(client.gpFile("debris")).thenThrow(new IOException("HTTP 403"));
        when(client.gpFile("broken")).thenThrow(new IOException("HTTP 403"));

        assertEquals(1, service.refresh());
        verify(objects).saveAll(any());
    }

    @Test
    void theFirstGroupIsRequired() throws IOException {
        when(client.gpFile("active")).thenThrow(new IOException("HTTP 403"));

        assertThrows(IOException.class, () -> service.refresh());
    }
}
