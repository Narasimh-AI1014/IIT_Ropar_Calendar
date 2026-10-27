package com.iitrpr.calendar.sync;

import com.iitrpr.calendar.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SyncTaskDispatcher {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Identifies all affected students matching an event's audience criteria and dispatches sync tasks.
     * @param eventId The primary tracking key of the changed event element.
     * @param audienceType The target type parameter ("ALL", "COURSES", "GROUP")
     * @param courseCodes List of related course identifiers
     * @param deptCodes List of filtered departments
     * @param entryYears List of cohort entry years
     */
    @Transactional
    public void dispatchTasksForEvent(Long eventId, String audienceType, 
                                      List<String> courseCodes, 
                                      List<String> deptCodes, 
                                      List<Integer> entryYears) {
        
        // 1. Fetch exactly which active student user IDs are impacted by this change
        int totalReached = appUserRepository.countReachedStudents(audienceType, courseCodes, deptCodes, entryYears);
        if (totalReached == 0) return;

        // Use the native database index query rules to pull the exact student ID list matching criteria
        String selectSql = """
            SELECT DISTINCT u.id FROM app_user u
            WHERE u.role = 'STUDENT' AND u.active = true
              AND (
                  ? = 'ALL'
                  OR (? = 'COURSES' AND u.id IN (
                      SELECT user_id FROM enrolment WHERE course_code IN (%s) AND status = 'ENRO'
                  ))
                  OR (? = 'GROUP' AND (
                      ((COALESCE(%s, NULL) IS NULL) OR (u.dept_code IN (%s)))
                      AND
                      ((COALESCE(%s, NULL) IS NULL) OR (u.entry_year IN (%s)))
                  ))
              )
            """;

        // Format placeholders dynamically based on collection sizing constraints to prevent SQL injection issues
        String coursePlaceholders = courseCodes == null || courseCodes.isEmpty() ? "NULL" : String.join(",", courseCodes.stream().map(c -> "'" + c + "'").toList());
        String deptPlaceholders = deptCodes == null || deptCodes.isEmpty() ? "NULL" : String.join(",", deptCodes.stream().map(d -> "'" + d + "'").toList());
        String yearPlaceholders = entryYears == null || entryYears.isEmpty() ? "NULL" : String.join(",", entryYears.stream().map(String::valueOf).toList());

        String formattedSql = String.format(selectSql, coursePlaceholders, deptPlaceholders, deptPlaceholders, yearPlaceholders, yearPlaceholders);

        List<Long> studentIds = jdbcTemplate.queryForList(formattedSql, Long.class, audienceType, audienceType, audienceType);

        // 2. Perform a batch insert operation to queue synchronization tasks under 'PENDING' status parameters
        if (!studentIds.isEmpty()) {
            String insertSql = "INSERT INTO sync_task (user_id, status, attempts) VALUES (?, 'PENDING', 0)";
            List<Object[]> batchArgs = new ArrayList<>();
            for (Long studentId : studentIds) {
                batchArgs.add(new Object[]{studentId});
            }
            jdbcTemplate.batchUpdate(insertSql, batchArgs);
        }
    }

    /**
     * Triggers an isolated single sync task entry immediately following a distinct individual enrollment profile shift.
     * @param studentId The primary tracking profile key of the student target user.
     */
    @Transactional
    public void dispatchSingleStudentSync(Long studentId) {
        String insertSql = "INSERT INTO sync_task (user_id, status, attempts) VALUES (?, 'PENDING', 0)";
        jdbcTemplate.update(insertSql, studentId);
    }
}
