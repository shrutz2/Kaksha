package com.kaksha.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * The latest mean orbital elements of one object (from CelesTrak GP data).
 * Only one row is kept per object; a new download replaces the old row.
 * Angles are in degrees, mean motion in revolutions per day, epoch in UTC.
 */
@Entity
@Table(name = "orbital_elements")
public class OrbitalElements {

    @Id
    private Long noradId;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime epoch;

    private double meanMotion;
    private double eccentricity;
    private double inclination;
    private double raan;
    private double argOfPericenter;
    private double meanAnomaly;
    private double bstar;
    private double meanMotionDot;
    private double meanMotionDdot;
    private int elementSetNo;
    private int revAtEpoch;
    private int ephemerisType;

    @Column(length = 1)
    private String classification;

    protected OrbitalElements() {
        // required by JPA
    }

    public OrbitalElements(Long noradId, LocalDateTime epoch, double meanMotion, double eccentricity,
                           double inclination, double raan, double argOfPericenter, double meanAnomaly,
                           double bstar, double meanMotionDot, double meanMotionDdot,
                           int elementSetNo, int revAtEpoch, int ephemerisType, String classification) {
        this.noradId = noradId;
        this.epoch = epoch;
        this.meanMotion = meanMotion;
        this.eccentricity = eccentricity;
        this.inclination = inclination;
        this.raan = raan;
        this.argOfPericenter = argOfPericenter;
        this.meanAnomaly = meanAnomaly;
        this.bstar = bstar;
        this.meanMotionDot = meanMotionDot;
        this.meanMotionDdot = meanMotionDdot;
        this.elementSetNo = elementSetNo;
        this.revAtEpoch = revAtEpoch;
        this.ephemerisType = ephemerisType;
        this.classification = classification;
    }

    public Long getNoradId() { return noradId; }
    public LocalDateTime getEpoch() { return epoch; }
    public double getMeanMotion() { return meanMotion; }
    public double getEccentricity() { return eccentricity; }
    public double getInclination() { return inclination; }
    public double getRaan() { return raan; }
    public double getArgOfPericenter() { return argOfPericenter; }
    public double getMeanAnomaly() { return meanAnomaly; }
    public double getBstar() { return bstar; }
    public double getMeanMotionDot() { return meanMotionDot; }
    public double getMeanMotionDdot() { return meanMotionDdot; }
    public int getElementSetNo() { return elementSetNo; }
    public int getRevAtEpoch() { return revAtEpoch; }
    public int getEphemerisType() { return ephemerisType; }
    public String getClassification() { return classification; }
}
