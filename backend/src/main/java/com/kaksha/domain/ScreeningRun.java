package com.kaksha.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** One execution of the screening pipeline, with the benchmark numbers of every stage. */
@Entity
@Table(name = "screening_run")
public class ScreeningRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime runAt;

    /** DAILY for the scheduled run, ON_DEMAND when one satellite was screened by request. */
    @Column(nullable = false, length = 16)
    private String mode;

    /** For ON_DEMAND runs, the one satellite that was screened. Empty for the daily run. */
    private Long primaryId;

    private int primaries;
    private int secondaries;
    private int horizonHours;

    private long pairsTotal;
    private long pairsAfterAltitude;
    private long pairsAfterCoarse;
    private long encountersRefined;
    private long encountersInVolume;
    private int eventsFlagged;

    private long altitudeMillis;
    private long coarseMillis;
    private long refineMillis;
    private long flagMillis;
    private long totalMillis;

    private int propagationFailures;

    protected ScreeningRun() {
        // required by JPA
    }

    public ScreeningRun(LocalDateTime runAt, String mode, Long primaryId, int horizonHours, int primaries, int secondaries,
                        long pairsTotal, long pairsAfterAltitude, long pairsAfterCoarse,
                        long encountersRefined, long encountersInVolume, int eventsFlagged,
                        long altitudeMillis, long coarseMillis, long refineMillis, long flagMillis,
                        long totalMillis, int propagationFailures) {
        this.runAt = runAt;
        this.mode = mode;
        this.primaryId = primaryId;
        this.horizonHours = horizonHours;
        this.primaries = primaries;
        this.secondaries = secondaries;
        this.pairsTotal = pairsTotal;
        this.pairsAfterAltitude = pairsAfterAltitude;
        this.pairsAfterCoarse = pairsAfterCoarse;
        this.encountersRefined = encountersRefined;
        this.encountersInVolume = encountersInVolume;
        this.eventsFlagged = eventsFlagged;
        this.altitudeMillis = altitudeMillis;
        this.coarseMillis = coarseMillis;
        this.refineMillis = refineMillis;
        this.flagMillis = flagMillis;
        this.totalMillis = totalMillis;
        this.propagationFailures = propagationFailures;
    }

    public Long getId() { return id; }
    public LocalDateTime getRunAt() { return runAt; }
    public String getMode() { return mode; }
    public Long getPrimaryId() { return primaryId; }
    public int getPrimaries() { return primaries; }
    public int getSecondaries() { return secondaries; }
    public int getHorizonHours() { return horizonHours; }
    public long getPairsTotal() { return pairsTotal; }
    public long getPairsAfterAltitude() { return pairsAfterAltitude; }
    public long getPairsAfterCoarse() { return pairsAfterCoarse; }
    public long getEncountersRefined() { return encountersRefined; }
    public long getEncountersInVolume() { return encountersInVolume; }
    public int getEventsFlagged() { return eventsFlagged; }
    public long getAltitudeMillis() { return altitudeMillis; }
    public long getCoarseMillis() { return coarseMillis; }
    public long getRefineMillis() { return refineMillis; }
    public long getFlagMillis() { return flagMillis; }
    public long getTotalMillis() { return totalMillis; }
    public int getPropagationFailures() { return propagationFailures; }
}
