package com.kaksha.catalog;

import com.kaksha.config.CelestrakProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Downloads CelesTrak files and keeps a local copy.
 *
 * CelesTrak asks users not to download the same data again and again, so a file is only
 * fetched when the cached copy is older than {@code minRefresh}. A download is also checked
 * before it replaces the cache: when CelesTrak refuses a request it answers with a short
 * text message instead of data, and that must never overwrite a good file.
 */
@Component
public class CelestrakClient {

    private static final Logger log = LoggerFactory.getLogger(CelestrakClient.class);

    private final CelestrakProperties props;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CelestrakClient(CelestrakProperties props) {
        this.props = props;
    }

    /** Returns the local path of the GP csv of one group, downloading it first if the cache is old. */
    public Path gpFile(String group) throws IOException {
        return fetchIfStale(props.gpUrl().replace("{group}", group),
                props.cacheDir().resolve("gp-" + group + ".csv"), "NORAD_CAT_ID");
    }

    /** Returns the local path of the SATCAT csv, downloading it first if the cache is old. */
    public Path satcatFile() throws IOException {
        return fetchIfStale(props.satcatUrl(), props.cacheDir().resolve("satcat.csv"), "NORAD_CAT_ID");
    }

    /**
     * @param requiredHeaderColumn a column name that must appear in the first line of a valid file
     */
    Path fetchIfStale(String url, Path cacheFile, String requiredHeaderColumn) throws IOException {
        Files.createDirectories(cacheFile.getParent());

        if (isFresh(cacheFile)) {
            log.info("Using cached {} (younger than {})", cacheFile.getFileName(), props.minRefresh());
            return cacheFile;
        }

        try {
            download(url, cacheFile, requiredHeaderColumn);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (Files.exists(cacheFile)) {
                log.warn("Download of {} failed ({}), falling back to the older cached copy",
                        cacheFile.getFileName(), e.toString());
                return cacheFile;
            }
            throw new IOException("Could not download " + url + " and no cached copy exists", e);
        }
        return cacheFile;
    }

    private boolean isFresh(Path cacheFile) throws IOException {
        if (!Files.exists(cacheFile)) {
            return false;
        }
        Instant modified = Files.getLastModifiedTime(cacheFile).toInstant();
        return modified.plus(props.minRefresh()).isAfter(Instant.now());
    }

    private void download(String url, Path cacheFile, String requiredHeaderColumn)
            throws IOException, InterruptedException {
        log.info("Downloading {}", url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "kaksha-conjunction-screening/0.1")
                .GET()
                .build();

        // Write to a temp file first so a half-finished download never damages the cache.
        Path temp = cacheFile.resolveSibling(cacheFile.getFileName() + ".part");
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp));

        if (response.statusCode() != 200) {
            Files.deleteIfExists(temp);
            throw new IOException("HTTP " + response.statusCode());
        }
        if (!looksLikeCsv(temp, requiredHeaderColumn)) {
            String message = firstLine(temp);
            Files.deleteIfExists(temp);
            throw new IOException("Unexpected response: " + message);
        }
        Files.move(temp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean looksLikeCsv(Path file, String requiredHeaderColumn) throws IOException {
        return firstLine(file).contains(requiredHeaderColumn);
    }

    private static String firstLine(Path file) throws IOException {
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.findFirst().orElse("");
        }
    }
}
