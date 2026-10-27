package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "role", nullable = false)
    private String role; // 'STUDENT', 'FACULTY', 'ADMIN'

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "dept_code")
    private String deptCode;

    @Column(name = "entry_year")
    private Integer entryYear;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public AppUser() {}

    // ==========================================
    // GETTERS & SETTERS (Strict Formatted camelCase Alignment)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDeptCode() { return deptCode; }
    public void setDeptCode(String deptCode) { this.deptCode = deptCode; }

    public Integer getEntryYear() { return entryYear; }
    public void setEntryYear(Integer entryYear) { this.entryYear = entryYear; }

    public String getRefreshToken() { 
        return this.refreshToken; 
    }
    public void setRefreshToken(String refreshToken) { 
        this.refreshToken = refreshToken; 
    }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
