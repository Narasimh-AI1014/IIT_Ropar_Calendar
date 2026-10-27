package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "import_job")
public class ImportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kind")
    private String kind; 

    @Column(name = "sheet")
    private String sheet;

    @Column(name = "status", nullable = false)
    private String status = "PREVIEW"; 

    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "saved_count")
    private Integer savedCount;

    @Column(name = "error")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusHours(1);

    @Column(name = "confirmed_at")
    private ZonedDateTime confirmedAt;

    public ImportJob() {}

    // ==========================================
    // GETTERS & SETTERS (Strict Formatted camelCase Alignment)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getSheet() { return sheet; }
    public void setSheet(String sheet) { this.sheet = sheet; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBlockedReason() { return blockedReason; }
    public void setBlockedReason(String blockedReason) { this.blockedReason = blockedReason; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Integer getSavedCount() { return savedCount; }
    public void setSavedCount(Integer savedCount) { this.savedCount = savedCount; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(ZonedDateTime expiresAt) { this.expiresAt = expiresAt; }

    public ZonedDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(ZonedDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
