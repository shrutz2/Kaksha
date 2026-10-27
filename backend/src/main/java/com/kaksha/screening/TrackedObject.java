package com.kaksha.screening;

import com.kaksha.domain.OrbitalElements;

/** An object ready for screening: who it is, its elements, and its altitude range. */
public record TrackedObject(long noradId, String name, OrbitalElements elements, OrbitRange range) {

    public static TrackedObject of(long noradId, String name, OrbitalElements elements) {
        return new TrackedObject(noradId, name, elements, OrbitRange.fromElements(elements));
    }
}
