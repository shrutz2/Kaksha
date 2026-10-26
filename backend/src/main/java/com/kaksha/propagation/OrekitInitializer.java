package com.kaksha.propagation;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Loads the Orekit data folder when the application starts. */
@Component
public class OrekitInitializer {

    private final Path dataDir;

    public OrekitInitializer(@Value("${kaksha.orekit.data-dir}") Path dataDir) {
        this.dataDir = dataDir;
    }

    @PostConstruct
    void init() {
        OrekitData.load(dataDir);
    }
}
