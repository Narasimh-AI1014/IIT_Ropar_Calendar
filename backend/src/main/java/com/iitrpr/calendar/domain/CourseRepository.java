package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, String> {
    @Query(value = """
        SELECT c.* FROM course c
        JOIN course_instructor ci ON c.code = ci.course_code
        WHERE ci.user_id = :facultyId
        """, nativeQuery = true)
    List<Course> findCoursesByInstructor(@Param("facultyId") Long facultyId);
}
