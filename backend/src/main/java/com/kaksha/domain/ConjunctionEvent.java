package com.kaksha.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A flagged close approach between an Indian satellite (primary) and another object. */
@Entity
@Table(name = "conjunction_event", indexes = {
        @Index(name = "idx_event_primary", columnList = "primaryId"),
        @Index(name = "idx_event_tca", columnList = "tca")
})
public class ConjunctionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The screening run that found this event. */
    private Long runId;

    private Long primaryId;
    private Long secondaryId;

    /** Time of closest approach, UTC. */
    @Column(nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime tca;

    private double missKm;
    private double relativeSpeedKmS;

    /** HIGH, MEDIUM or LOW. */
    @Column(nullable = false, length = 8)
    private String risk;

    /** True for events of the daily run. Only these are compared with earlier predictions. */
    private Boolean daily;

    /** How many daily runs have predicted this same approach, this one included. Empty counts as 1. */
    private Integer timesSeen;

    /** The miss distance and time of closest approach in the previous daily run that found it. */
    private Double previousMissKm;

    @Column(columnDefinition = "datetime(6)")
    private LocalDateTime previousTca;

    protected ConjunctionEvent() {
        // required by JPA
    }

    public ConjunctionEvent(Long runId, Long primaryId, Long secondaryId, LocalDateTime tca,
                            double missKm, double relativeSpeedKmS, String risk) {
        this(runId, primaryId, secondaryId, tca, missKm, relativeSpeedKmS, risk, null, null, null, null);
    }

    public ConjunctionEvent(Long runId, Long primaryId, Long secondaryId, LocalDateTime tca,
                            double missKm, double relativeSpeedKmS, String risk, Boolean daily,
                            Integer timesSeen, Double previousMissKm, LocalDateTime previousTca) {
        this.runId = runId;
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
        this.tca = tca;
        this.missKm = missKm;
        this.relativeSpeedKmS = relativeSpeedKmS;
        this.risk = risk;
        this.daily = daily;
        this.timesSeen = timesSeen;
        this.previousMissKm = previousMissKm;
        this.previousTca = previousTca;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getPrimaryId() { return primaryId; }
    public Long getSecondaryId() { return secondaryId; }
    public LocalDateTime getTca() { return tca; }
    public double getMissKm() { return missKm; }
    public double getRelativeSpeedKmS() { return relativeSpeedKmS; }
    public String getRisk() { return risk; }
    public Boolean getDaily() { return daily; }
    public Integer getTimesSeen() { return timesSeen; }
    public Double getPreviousMissKm() { return previousMissKm; }
    public LocalDateTime getPreviousTca() { return previousTca; }
}
