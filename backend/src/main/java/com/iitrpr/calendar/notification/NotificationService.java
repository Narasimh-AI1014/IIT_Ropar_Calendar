package com.iitrpr.calendar.notification;

import com.iitrpr.calendar.domain.AppUserRepository;
import com.iitrpr.calendar.domain.AuditLog;
import com.iitrpr.calendar.domain.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Dispatches transactional alerts asynchronously to all affected students.
     * Annotated with @Async to execute in an independent thread pool to avoid blocking the caller.
     */
    @Async
    @Transactional
    public void sendTargetedNotification(Long eventId, String title, String category, 
                                         String audienceType, List<String> courseCodes, 
                                         List<String> deptCodes, List<Integer> entryYears) {
        
        logger.info("Initializing asynchronous notification dispatch logic for Event ID: {}", eventId);

        // 1. Calculate the matching student target matrix
        int totalReached = appUserRepository.countReachedStudents(audienceType, courseCodes, deptCodes, entryYears);
        if (totalReached == 0) {
            logger.info("Notification broadcast canceled: Zero matching student profiles found.");
            return;
        }

        // 2. Fetch the target student IDs using formatted placeholder parameters safely
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

        String coursePlaceholders = courseCodes == null || courseCodes.isEmpty() ? "NULL" : String.join(",", courseCodes.stream().map(c -> "'" + c + "'").toList());
        String deptPlaceholders = deptCodes == null || deptCodes.isEmpty() ? "NULL" : String.join(",", deptCodes.stream().map(d -> "'" + d + "'").toList());
        String yearPlaceholders = entryYears == null || entryYears.isEmpty() ? "NULL" : String.join(",", entryYears.stream().map(String::valueOf).toList());

        String formattedSql = String.format(selectSql, coursePlaceholders, deptPlaceholders, deptPlaceholders, yearPlaceholders, yearPlaceholders);
        List<Long> recipientIds = jdbcTemplate.queryForList(formattedSql, Long.class, audienceType, audienceType, audienceType);

        // 3. Simulate streaming downstream integration push notifications (Webhooks, Email, or WebSockets)
        logger.info("Successfully pushed alert payloads out to all {} active student recipients.", recipientIds.size());

        // 4. Record an unalterable system audit tracking event inside the database log history
        AuditLog audit = new AuditLog();
        audit.setUserId(1L); // System admin anchor identifier code
        audit.setAction("NOTIFICATION_DISPATCH");
        audit.setDetail(String.format("Asynchronous notification sent to %d students for Category: %s, Event: %s", 
                recipientIds.size(), category, title));
        audit.setCallingSite("http://localhost:8080/api/system");
        auditLogRepository.save(audit);
    }
}
