package com.kaksha.screening;

import com.kaksha.domain.OrbitalElements;

/** The lowest and highest altitude an object reaches above the Earth's surface, in kilometres. */
public record OrbitRange(double perigeeKm, double apogeeKm) {

    private static final double MU = 398600.4418;        // Earth's gravitational parameter, km^3/s^2
    private static final double EARTH_RADIUS_KM = 6378.137;
    private static final double SECONDS_PER_DAY = 86400.0;

    /**
     * Works out the altitude range from the mean elements. Mean motion gives the size of the
     * orbit (Kepler's third law), and eccentricity says how stretched it is.
     */
    public static OrbitRange fromElements(OrbitalElements e) {
        double radiansPerSecond = e.getMeanMotion() * 2 * Math.PI / SECONDS_PER_DAY;
        double semiMajorAxis = Math.cbrt(MU / (radiansPerSecond * radiansPerSecond));
        double perigee = semiMajorAxis * (1 - e.getEccentricity()) - EARTH_RADIUS_KM;
        double apogee = semiMajorAxis * (1 + e.getEccentricity()) - EARTH_RADIUS_KM;
        return new OrbitRange(perigee, apogee);
    }
}
