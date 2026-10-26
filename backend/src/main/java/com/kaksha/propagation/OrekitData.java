package com.kaksha.propagation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.orekit.data.DataContext;
import org.orekit.data.DirectoryCrawler;

/**
 * Tells Orekit where its data folder is. Orekit needs it for leap seconds and for the
 * rotation between reference frames. Loading is done once; later calls do nothing.
 */
public final class OrekitData {

    private static boolean loaded = false;

    private OrekitData() {
    }

    public static synchronized void load(Path dataDir) {
        if (loaded) {
            return;
        }
        if (!Files.isDirectory(dataDir)) {
            throw new IllegalStateException("Orekit data folder not found: " + dataDir.toAbsolutePath()
                    + ". Run scripts/fetch-orekit-data.sh (or .ps1) from the project root.");
        }
        DataContext.getDefault().getDataProvidersManager()
                .addProvider(new DirectoryCrawler(dataDir.toFile()));
        loaded = true;
    }
}
