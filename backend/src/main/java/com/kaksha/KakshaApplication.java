package com.kaksha;

import com.kaksha.config.CelestrakProperties;
import com.kaksha.config.ScreeningProperties;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({CelestrakProperties.class, ScreeningProperties.class})
public class KakshaApplication {

    public static void main(String[] args) {
        // All times are stored and compared in UTC. The MySQL driver converts LocalDateTime values
        // using the JVM time zone, so without this a laptop in India and a server in UTC would
        // read the same rows 5.5 hours apart, and orbit epochs would be wrong.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(KakshaApplication.class, args);
    }
}
