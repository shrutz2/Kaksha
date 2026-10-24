package com.kaksha.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** Someone who wants an email when a HIGH risk event is found for a satellite. */
@Entity
@Table(name = "watchlist_entry",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_watch_email_sat", columnNames = {"email", "noradId"}),
                @UniqueConstraint(name = "uk_watch_token", columnNames = {"token"})
        })
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Long noradId;

    /** Random value used in the unsubscribe link of every email, so only the owner can use it. */
    @Column(nullable = false, length = 36)
    private String token;

    @Column(nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    protected WatchlistEntry() {
        // required by JPA
    }

    public WatchlistEntry(String email, Long noradId, String token, LocalDateTime createdAt) {
        this.email = email;
        this.noradId = noradId;
        this.token = token;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public Long getNoradId() { return noradId; }
    public String getToken() { return token; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
