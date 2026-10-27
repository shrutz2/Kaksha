package com.kaksha.screening;

/** How worrying a close approach is, judged only by the miss distance. */
public enum RiskLevel {
    HIGH,
    MEDIUM,
    LOW;

    public static final double HIGH_BELOW_KM = 1.0;
    public static final double MEDIUM_BELOW_KM = 3.0;

    /** Call this only for approaches already below the event threshold (5 km). */
    public static RiskLevel fromMissKm(double missKm) {
        if (missKm < HIGH_BELOW_KM) {
            return HIGH;
        }
        if (missKm < MEDIUM_BELOW_KM) {
            return MEDIUM;
        }
        return LOW;
    }
}
