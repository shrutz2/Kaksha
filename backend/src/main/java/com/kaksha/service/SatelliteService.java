package com.kaksha.service;

import com.kaksha.domain.OrbitalElements;
import com.kaksha.domain.SpaceObject;
import com.kaksha.repository.OrbitalElementsRepository;
import com.kaksha.repository.SpaceObjectRepository;
import com.kaksha.web.dto.SatelliteDto;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SatelliteService {

    private final SpaceObjectRepository objects;
    private final OrbitalElementsRepository elements;

    public SatelliteService(SpaceObjectRepository objects, OrbitalElementsRepository elements) {
        this.objects = objects;
        this.elements = elements;
    }

    /** Indian satellites that have current orbital data, so they can actually be screened. */
    public List<SatelliteDto> indianSatellites() {
        List<SpaceObject> payloads = objects.findByOwnerOrderByNameAsc(SpaceObject.OWNER_INDIA).stream()
                .filter(SpaceObject::isIndianPayload)
                .toList();

        // One query for all of them. Asking for each satellite separately made this endpoint
        // several times slower than the others.
        Set<Long> withElements = elements
                .findAllById(payloads.stream().map(SpaceObject::getNoradId).toList()).stream()
                .map(OrbitalElements::getNoradId)
                .collect(Collectors.toSet());

        return payloads.stream()
                .filter(o -> withElements.contains(o.getNoradId()))
                .map(o -> new SatelliteDto(o.getNoradId(), o.getName(), o.getObjectType(),
                        o.getLaunchDate(), o.getPerigeeKm(), o.getApogeeKm()))
                .toList();
    }
}
