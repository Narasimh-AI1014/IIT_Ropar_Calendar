package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "google_token")
public class GoogleToken {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "refresh_token_encrypted", nullable = false)
    private byte[] refreshTokenEncrypted;

    @Column(name = "key_version", nullable = false)
    private Short keyVersion = 1;

    @Column(name = "calendar_id")
    private String calendarId;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // 'ACTIVE', 'REVOKED'

    @Column(name = "connected_at", nullable = false, updatable = false)
    private ZonedDateTime connectedAt = ZonedDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @PreUpdate
    protected void onUpdate() { this.updatedAt = ZonedDateTime.now(); }

    // Getters, Setters
}
