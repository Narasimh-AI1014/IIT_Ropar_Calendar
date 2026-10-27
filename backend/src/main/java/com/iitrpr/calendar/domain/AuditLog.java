package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "detail")
    private String detail;

    @Column(name = "at", nullable = false, updatable = false)
    private ZonedDateTime at = ZonedDateTime.now();

    @Column(name = "calling_site")
    private String callingSite; // Formatted key mapping matching your V2 migration columns

    public AuditLog() {}

    // ==========================================
    // GETTERS & SETTERS (Strict Formatted Alignment)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public ZonedDateTime getAt() { return at; }
    public void setAt(ZonedDateTime at) { this.at = at; }

    public String getCallingSite() { return callingSite; }
    public void setCallingSite(String callingSite) { this.callingSite = callingSite; }
}
