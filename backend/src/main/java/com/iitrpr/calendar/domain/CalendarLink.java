package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "calendar_link", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "google_event_id"})})
public class CalendarLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "category")
    private String category;

    @Column(name = "event_date")
    private String eventDate; // Maps matching string formatted tokens

    @Column(name = "google_event_id", nullable = false)
    private String googleEventId;

    @Column(name = "synced", nullable = false)
    private boolean synced = false;

    @Column(name = "status")
    private String status = "ENRO"; 

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    public CalendarLink() {}

    // ==========================================
    // GETTERS & SETTERS (Strict camelCase Naming Alignment)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCourseCode() { 
        return this.courseCode; 
    }
    public void setCourseCode(String courseCode) { 
        this.courseCode = courseCode; 
    }

    public String getCategory() { 
        return this.category; 
    }
    public void setCategory(String category) { 
        this.category = category; 
    }

    public String getEventDate() { 
        return this.eventDate; 
    }
    public void setEventDate(String eventDate) { 
        this.eventDate = eventDate; 
    }

    public String getGoogleEventId() { return googleEventId; }
    public void setGoogleEventId(String googleEventId) { this.googleEventId = googleEventId; }

    public boolean getSynced() { 
        return this.synced; 
    }
    public void setSynced(boolean synced) { 
        this.synced = synced; 
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }
}
