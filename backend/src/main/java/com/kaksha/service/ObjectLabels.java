package com.kaksha.service;

import com.kaksha.domain.SpaceObject;
import java.util.Map;

/**
 * Turns the short codes of the CelesTrak catalog into words a person can read: which country an
 * object belongs to, and what kind of object it is.
 */
public final class ObjectLabels {

    // Owner codes used in the catalog. A code that is not listed is shown as it is.
    private static final Map<String, String> OWNERS = Map.ofEntries(
            Map.entry("US", "United States"), Map.entry("PRC", "China"), Map.entry("CIS", "Russia (CIS)"),
            Map.entry("IND", "India"), Map.entry("JPN", "Japan"), Map.entry("UK", "United Kingdom"),
            Map.entry("FR", "France"), Map.entry("GER", "Germany"), Map.entry("IT", "Italy"),
            Map.entry("ESA", "European Space Agency"), Map.entry("EUME", "EUMETSAT"),
            Map.entry("EUTE", "Eutelsat"), Map.entry("CA", "Canada"), Map.entry("SKOR", "South Korea"),
            Map.entry("NKOR", "North Korea"), Map.entry("ISRA", "Israel"), Map.entry("UAE", "United Arab Emirates"),
            Map.entry("SAUD", "Saudi Arabia"), Map.entry("TURK", "Turkey"), Map.entry("IRAN", "Iran"),
            Map.entry("PAKI", "Pakistan"), Map.entry("BGD", "Bangladesh"), Map.entry("NPL", "Nepal"),
            Map.entry("BHUT", "Bhutan"), Map.entry("SING", "Singapore"), Map.entry("INDO", "Indonesia"),
            Map.entry("THAI", "Thailand"), Map.entry("VTNM", "Vietnam"), Map.entry("MALA", "Malaysia"),
            Map.entry("RP", "Philippines"), Map.entry("ROC", "Taiwan"), Map.entry("AUS", "Australia"),
            Map.entry("BRAZ", "Brazil"), Map.entry("ARGN", "Argentina"), Map.entry("MEX", "Mexico"),
            Map.entry("SAFR", "South Africa"), Map.entry("EGYP", "Egypt"), Map.entry("NIG", "Nigeria"),
            Map.entry("SPN", "Spain"), Map.entry("POR", "Portugal"), Map.entry("POL", "Poland"),
            Map.entry("NETH", "Netherlands"), Map.entry("BEL", "Belgium"), Map.entry("SWED", "Sweden"),
            Map.entry("NOR", "Norway"), Map.entry("FIN", "Finland"), Map.entry("DEN", "Denmark"),
            Map.entry("SWTZ", "Switzerland"), Map.entry("ASRA", "Austria"), Map.entry("GREC", "Greece"),
            Map.entry("UKR", "Ukraine"), Map.entry("KAZ", "Kazakhstan"), Map.entry("LUXE", "Luxembourg"),
            Map.entry("IRID", "Iridium"), Map.entry("GLOB", "Globalstar"), Map.entry("ORB", "ORBCOMM"),
            Map.entry("ITSO", "Intelsat"), Map.entry("SES", "SES"), Map.entry("IM", "Inmarsat"),
            Map.entry("O3B", "O3b Networks"), Map.entry("NATO", "NATO"), Map.entry("ISS", "International Space Station"),
            Map.entry("TBD", "Owner not known"));

    private ObjectLabels() {
    }

    public static String ownerName(String code) {
        if (code == null || code.isBlank()) {
            return "Owner not known";
        }
        return OWNERS.getOrDefault(code, code);
    }

    /**
     * A short description of what the object is. Whether a satellite is still operating matters
     * most: an active one can move out of the way, a dead one or a piece of debris cannot.
     */
    public static String kind(SpaceObject object) {
        String type = object.getObjectType();
        String status = object.getOpsStatus();
        if (SpaceObject.TYPE_PAYLOAD.equals(type)) {
            if ("+".equals(status) || "P".equals(status) || "B".equals(status) || "S".equals(status)
                    || "X".equals(status)) {
                return "Active satellite";
            }
            if ("-".equals(status) || "D".equals(status)) {
                return "Inactive satellite";
            }
            return "Satellite";
        }
        if ("R/B".equals(type)) {
            return "Rocket body";
        }
        if ("DEB".equals(type)) {
            return "Debris";
        }
        return "Unknown object";
    }
}
