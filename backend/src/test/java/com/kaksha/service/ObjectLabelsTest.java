package com.kaksha.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kaksha.domain.SpaceObject;
import org.junit.jupiter.api.Test;

class ObjectLabelsTest {

    private static SpaceObject object(String type, String status) {
        return new SpaceObject(1L, "X", "2000-001A", type, status, "US", null, 500.0, 500.0);
    }

    @Test
    void ownerCodesBecomeCountryNames() {
        assertEquals("United States", ObjectLabels.ownerName("US"));
        assertEquals("China", ObjectLabels.ownerName("PRC"));
        assertEquals("Russia (CIS)", ObjectLabels.ownerName("CIS"));
        assertEquals("India", ObjectLabels.ownerName("IND"));
    }

    @Test
    void unknownOrMissingOwnersAreHandled() {
        assertEquals("ZZZ", ObjectLabels.ownerName("ZZZ"));
        assertEquals("Owner not known", ObjectLabels.ownerName("TBD"));
        assertEquals("Owner not known", ObjectLabels.ownerName(null));
    }

    @Test
    void payloadsAreSplitIntoActiveAndInactive() {
        assertEquals("Active satellite", ObjectLabels.kind(object("PAY", "+")));
        assertEquals("Active satellite", ObjectLabels.kind(object("PAY", "P")));
        assertEquals("Inactive satellite", ObjectLabels.kind(object("PAY", "-")));
        assertEquals("Satellite", ObjectLabels.kind(object("PAY", "?")));
    }

    @Test
    void otherObjectTypesHaveTheirOwnLabel() {
        assertEquals("Rocket body", ObjectLabels.kind(object("R/B", "-")));
        assertEquals("Debris", ObjectLabels.kind(object("DEB", null)));
        assertEquals("Unknown object", ObjectLabels.kind(object("UNK", null)));
    }
}
