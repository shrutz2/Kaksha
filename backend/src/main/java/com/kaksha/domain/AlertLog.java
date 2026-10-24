package com.kaksha.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Remembers that an alert was already emailed, so the same close approach is not sent twice
 * when a later run finds it again. The time of closest approach is rounded to a half-hour
 * bucket, because a new run with fresher data moves it by a few seconds or minutes.
 */
@Entity
@Table(name = "alert_log",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert",
                columnNames = {"email", "primaryId", "secondaryId", "tcaBucket"}))
public class AlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private Long primaryId;
    private Long secondaryId;
    private Long tcaBucket;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime sentAt;

    protected AlertLog() {
        // required by JPA
    }

    public AlertLog(String email, Long primaryId, Long secondaryId, Long tcaBucket, LocalDateTime sentAt) {
        this.email = email;
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
        this.tcaBucket = tcaBucket;
        this.sentAt = sentAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public Long getPrimaryId() { return primaryId; }
    public Long getSecondaryId() { return secondaryId; }
    public Long getTcaBucket() { return tcaBucket; }
    public LocalDateTime getSentAt() { return sentAt; }
}
