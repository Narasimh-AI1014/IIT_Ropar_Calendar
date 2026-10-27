package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

    // ✅ FIX: Standard JPA query method that returns a clean entity list mapping
    List<Enrolment> findByUserIdAndStatus(Long userId, String status);

    @Query(value = """
        SELECT c.code, c.title, c.instructor_names, e.grp, e.status
        FROM enrolment e
        JOIN course c ON e.course_code = c.code
        WHERE e.user_id = :studentId AND e.status = 'ENRO'
        """, nativeQuery = true)
    List<Object[]> findActiveStudentCourses(@Param("studentId") Long studentId);
}
