package com.iitrpr.calendar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "course_instructor")
@IdClass(CourseInstructorId.class)
public class CourseInstructor {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "course_code")
    private String courseCode;

    // Getters, Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
}
