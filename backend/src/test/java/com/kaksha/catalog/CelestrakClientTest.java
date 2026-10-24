package com.kaksha.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kaksha.config.CelestrakProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CelestrakClientTest {

    @TempDir
    Path tempDir;

    private CelestrakClient clientWithRefresh(Duration minRefresh) {
        // The url is unreachable on purpose: these tests must never touch the network.
        return new CelestrakClient(new CelestrakProperties(
                "http://localhost:1/gp/{group}", List.of("active"), "http://localhost:1/satcat", tempDir, minRefresh));
    }

    @Test
    void freshCacheIsReusedWithoutDownloading() throws IOException {
        Path cached = tempDir.resolve("gp-active.csv");
        Files.writeString(cached, "NORAD_CAT_ID\n25544\n");

        Path result = clientWithRefresh(Duration.ofHours(23)).gpFile("active");

        assertEquals(cached, result);
    }

    @Test
    void staleCacheIsKeptWhenDownloadFails() throws IOException {
        Path cached = tempDir.resolve("gp-active.csv");
        Files.writeString(cached, "NORAD_CAT_ID\n25544\n");

        // minRefresh of zero means the file is always stale, so a download is attempted and fails.
        Path result = clientWithRefresh(Duration.ZERO).gpFile("active");

        assertEquals(cached, result);
        assertEquals("NORAD_CAT_ID\n25544\n", Files.readString(cached));
    }

    @Test
    void failureWithoutCacheIsReported() {
        assertThrows(IOException.class, () -> clientWithRefresh(Duration.ZERO).gpFile("active"));
    }
}
