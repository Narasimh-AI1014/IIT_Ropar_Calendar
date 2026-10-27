package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    
    // ✅ FIX: Ensures the return type is wrapped cleanly in an Optional container
    Optional<AppUser> findByEmail(String email);

    @Query(value = """
        SELECT COUNT(DISTINCT u.id) 
        FROM app_user u
        WHERE u.role = 'STUDENT' AND u.active = true
          AND (
              :type = 'ALL'
              OR (:type = 'COURSES' AND u.id IN (
                  SELECT user_id FROM enrolment WHERE course_code IN :courseCodes AND status = 'ENRO'
              ))
              OR (:type = 'GROUP' AND (
                  ((COALESCE(:deptCodes, NULL) IS NULL) OR (u.dept_code IN :deptCodes))
                  AND
                  ((COALESCE(:entryYears, NULL) IS NULL) OR (u.entry_year IN :entryYears))
              ))
          )
        """, nativeQuery = true)
    int countReachedStudents(
        @Param("type") String type,
        @Param("courseCodes") List<String> courseCodes,
        @Param("deptCodes") List<String> deptCodes,
        @Param("entryYears") List<Integer> entryYears
    );
}
