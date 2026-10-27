package com.iitrpr.calendar.domain;

import java.io.Serializable;
import java.util.Objects;

public class CourseInstructorId implements Serializable {
    private Long userId;
    private String courseCode;

    public CourseInstructorId() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourseInstructorId that = (CourseInstructorId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(courseCode, that.courseCode);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, courseCode); }
}
