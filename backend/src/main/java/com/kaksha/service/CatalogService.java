package com.kaksha.service;

import com.kaksha.catalog.CelestrakClient;
import com.kaksha.catalog.GpParser;
import com.kaksha.catalog.GpRecord;
import com.kaksha.catalog.SatcatParser;
import com.kaksha.catalog.SatcatRecord;
import com.kaksha.config.CelestrakProperties;
import com.kaksha.domain.OrbitalElements;
import com.kaksha.domain.SpaceObject;
import com.kaksha.repository.OrbitalElementsRepository;
import com.kaksha.repository.SpaceObjectRepository;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the CelesTrak catalog into MySQL.
 *
 * An object is stored only if it is still in orbit (no decay date in SATCAT) and the GP data
 * has current elements for it, because without elements it cannot be propagated anyway.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CelestrakClient client;
    private final CelestrakProperties props;
    private final SpaceObjectRepository objects;
    private final OrbitalElementsRepository elements;

    public CatalogService(CelestrakClient client, CelestrakProperties props, SpaceObjectRepository objects,
                          OrbitalElementsRepository elements) {
        this.client = client;
        this.props = props;
        this.objects = objects;
        this.elements = elements;
    }

    /** Downloads (or reuses the cached files) and saves the catalog. Returns how many objects were stored. */
    @Transactional
    public int refresh() throws IOException {
        Map<Long, GpRecord> gpById = loadGroups();
        Path satcatFile = client.satcatFile();

        List<SatcatRecord> satcat;
        try (Reader reader = Files.newBufferedReader(satcatFile, StandardCharsets.UTF_8)) {
            satcat = SatcatParser.parse(reader);
        }

        List<SpaceObject> objectRows = new ArrayList<>();
        List<OrbitalElements> elementRows = new ArrayList<>();

        for (SatcatRecord s : satcat) {
            GpRecord gp = gpById.get(s.noradId());
            if (gp == null || s.decayDate() != null) {
                continue;
            }
            objectRows.add(new SpaceObject(s.noradId(), s.name(), s.intlDesignator(), s.objectType(),
                    s.opsStatus(), s.owner(), s.launchDate(), s.apogeeKm(), s.perigeeKm()));
            elementRows.add(new OrbitalElements(gp.noradId(), gp.epoch(), gp.meanMotion(),
                    gp.eccentricity(), gp.inclination(), gp.raan(), gp.argOfPericenter(),
                    gp.meanAnomaly(), gp.bstar(), gp.meanMotionDot(), gp.meanMotionDdot(),
                    gp.elementSetNo(), gp.revAtEpoch(), gp.ephemerisType(), gp.classification()));
        }

        // saveAll inserts new rows and updates existing ones, so running this daily is safe.
        objects.saveAll(objectRows);
        elements.saveAll(elementRows);

        long indian = objectRows.stream().filter(o -> SpaceObject.OWNER_INDIA.equals(o.getOwner())).count();
        log.info("Catalog refreshed: {} GP rows, {} SATCAT rows, {} objects stored ({} Indian)",
                gpById.size(), satcat.size(), objectRows.size(), indian);
        return objectRows.size();
    }

    /**
     * Reads the GP file of every configured group into one map. The first group (the active
     * satellites) is required. The others, such as debris groups, are skipped with a warning if
     * they cannot be loaded. An object that appears in two groups is kept once, with its newest elements.
     */
    private Map<Long, GpRecord> loadGroups() throws IOException {
        Map<Long, GpRecord> gpById = new HashMap<>();
        List<String> groups = props.gpGroups();
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);
            try {
                Path file = client.gpFile(group);
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    for (GpRecord gp : GpParser.parse(reader)) {
                        gpById.merge(gp.noradId(), gp, (a, b) -> b.epoch().isAfter(a.epoch()) ? b : a);
                    }
                }
            } catch (IOException e) {
                if (i == 0) {
                    throw e;
                }
                log.warn("Group {} skipped: {}", group, e.getMessage());
            }
        }
        return gpById;
    }
}
