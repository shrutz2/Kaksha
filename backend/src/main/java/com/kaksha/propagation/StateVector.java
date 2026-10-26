package com.kaksha.propagation;

/**
 * Where an object is and how fast it moves at one moment.
 * Position is in kilometres and velocity in kilometres per second, both in the TEME frame
 * (an Earth-centred frame that does not rotate with the Earth, the native frame of SGP4).
 */
public record StateVector(double x, double y, double z, double vx, double vy, double vz) {

    /** Straight-line distance to another object, in kilometres. */
    public double distanceTo(StateVector other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Speed relative to another object, in kilometres per second. */
    public double relativeSpeedTo(StateVector other) {
        double dvx = vx - other.vx;
        double dvy = vy - other.vy;
        double dvz = vz - other.vz;
        return Math.sqrt(dvx * dvx + dvy * dvy + dvz * dvz);
    }

    /** Distance from the centre of the Earth, in kilometres. */
    public double radius() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
