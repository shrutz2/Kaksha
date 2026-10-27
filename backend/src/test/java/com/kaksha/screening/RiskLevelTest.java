package com.kaksha.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RiskLevelTest {

    @Test
    void levelsFollowTheMissDistanceLimits() {
        assertEquals(RiskLevel.HIGH, RiskLevel.fromMissKm(0.0));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromMissKm(0.99));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromMissKm(1.0));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromMissKm(2.99));
        assertEquals(RiskLevel.LOW, RiskLevel.fromMissKm(3.0));
        assertEquals(RiskLevel.LOW, RiskLevel.fromMissKm(4.99));
    }
}
