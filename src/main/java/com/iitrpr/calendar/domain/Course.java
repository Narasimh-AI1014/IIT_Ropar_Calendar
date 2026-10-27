package com.iitrpr.calendar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "course")
public class Course {
    @Id
    @Column(name = "code")
    private String code; // Uppercase course key (e.g., CS513)

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "instructor_names")
    private String instructorNames;

    @Column(name = "term_id")
    private Long termId;

    // Getters, Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code != null ? code.toUpperCase() : null; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInstructorNames() { return instructorNames; }
    public void setInstructorNames(String instructorNames) { this.instructorNames = instructorNames; }
    public Long getTermId() { return termId; }
    public void setTermId(Long termId) { this.termId = termId; }
}
