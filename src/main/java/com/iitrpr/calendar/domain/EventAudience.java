package com.iitrpr.calendar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "event_audience")
public class EventAudience {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "dept_code")
    private String deptCode;

    @Column(name = "entry_year")
    private Integer entryYear;

    // ==========================================
    // GETTERS & SETTERS (Ensure these match perfectly)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getDeptCode() { return deptCode; }
    public void setDeptCode(String deptCode) { this.deptCode = deptCode; }

    public Integer getEntryYear() { return entryYear; }
    public void setEntryYear(Integer entryYear) { this.entryYear = entryYear; }
}
