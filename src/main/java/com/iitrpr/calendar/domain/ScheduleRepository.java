package com.iitrpr.calendar.domain;

import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;

@Repository
public class ScheduleRepository {

    @PersistenceContext
    private EntityManager entityManager;

    // 1. Fetch Weekly Recurrent Class Slots matched on the Student's specific group
    @SuppressWarnings("unchecked")
    public List<Object[]> findStudentClasses(Long studentId) {
        String sql = """
            SELECT 'CLASS' as type, c.code, c.title, st.weekday, st.start_time, st.end_time, cs.room, '' as description
            FROM enrolment e
            JOIN course c ON e.course_code = c.code
            JOIN course_slot cs ON c.code = cs.course_code AND (cs.grp = '' OR cs.grp = e.grp)
            JOIN slot_time st ON cs.slot_code = st.slot_code
            WHERE e.user_id = :studentId AND e.status = 'ENRO'
            """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("studentId", studentId);
        return query.getResultList();
    }

    // 2. Fetch Scheduled Examination Blocks for Enrolled Courses
    @SuppressWarnings("unchecked")
    public List<Object[]> findStudentExams(Long studentId) {
        String sql = """
            SELECT 'EXAM' as type, ex.course_code, (c.title || ' - ' || ex.session) as title, ex.exam_date, ex.start_time, ex.end_time, 'Exam Hall' as venue, ex.notes
            FROM enrolment e
            JOIN course c ON e.course_code = c.code
            JOIN exam ex ON c.code = ex.course_code
            WHERE e.user_id = :studentId AND e.status = 'ENRO'
            """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("studentId", studentId);
        return query.getResultList();
    }

    // 3. Fetch Custom Events based on strict multi-parameter Audience Scope Bounds
    @SuppressWarnings("unchecked")
    public List<Object[]> findStudentEvents(Long studentId) {
        String sql = """
            SELECT DISTINCT 'EVENT' as type, ea.course_code, e.title, e.event_date, e.start_time, e.end_time, e.venue, e.description
            FROM event e
            JOIN event_audience ea ON e.id = ea.event_id
            WHERE e.cancelled = false
              AND (
                  ea.type = 'ALL'
                  OR (ea.type = 'COURSES' AND ea.course_code IN (
                      SELECT course_code FROM enrolment WHERE user_id = :studentId AND status = 'ENRO'
                  ))
                  OR (ea.type = 'GROUP' AND (
                      (ea.dept_code IS NULL OR ea.dept_code = (SELECT dept_code FROM app_user WHERE id = :studentId))
                      AND 
                      (ea.entry_year IS NULL OR ea.entry_year = (SELECT entry_year FROM app_user WHERE id = :studentId))
                  ))
              )
            """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("studentId", studentId);
        return query.getResultList();
    }
}
