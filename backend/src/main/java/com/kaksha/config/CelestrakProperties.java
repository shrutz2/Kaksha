package com.kaksha.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "kaksha.celestrak" in application.yml.
 *
 * @param gpUrl        where the orbital elements (GP data) are downloaded from; {group} is replaced by a group name
 * @param gpGroups     the CelesTrak groups to load. The first one is required, the others are optional.
 * @param satcatUrl    where the satellite catalog (SATCAT) is downloaded from
 * @param cacheDir     folder where downloaded files are kept between runs
 * @param minRefresh   a cached file younger than this is reused instead of downloading again
 */
@ConfigurationProperties(prefix = "kaksha.celestrak")
public record CelestrakProperties(
        String gpUrl,
        List<String> gpGroups,
        String satcatUrl,
        Path cacheDir,
        Duration minRefresh) {
}
