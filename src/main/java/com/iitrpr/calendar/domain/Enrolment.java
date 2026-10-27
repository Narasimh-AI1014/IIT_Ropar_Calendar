package com.iitrpr.calendar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "enrolment", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "course_code"})})
public class Enrolment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(name = "grp")
    private String grp = "";

    @Column(name = "status", nullable = false)
    private String status = "ENRO"; // 'ENRO', 'IPEN', 'APEN'

    public Enrolment() {}

    // ==========================================
    // GETTERS & SETTERS (Strict camelCase Naming Alignment)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { 
        return this.userId; 
    }
    public void setUserId(Long userId) { 
        this.userId = userId; 
    }

    public String getCourseCode() { 
        return this.courseCode; 
    }
    public void setCourseCode(String courseCode) { 
        this.courseCode = courseCode; 
    }

    public String getGrp() { 
        return this.grp; 
    }
    public void setGrp(String grp) { 
        this.grp = grp; 
    }

    public String getStatus() { 
        return this.status; 
    }
    public void setStatus(String status) { 
        this.status = status; 
    }
}
