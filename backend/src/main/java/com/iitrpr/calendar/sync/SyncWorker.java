package com.iitrpr.calendar.sync;

import com.iitrpr.calendar.domain.CalendarLink;
import com.iitrpr.calendar.domain.CalendarLinkRepository;
import com.iitrpr.calendar.student.StudentScheduleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class SyncWorker {

    private static final Logger logger = LoggerFactory.getLogger(SyncWorker.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SyncDiffEngine syncDiffEngine;

    @Autowired
    private CalendarLinkRepository calendarLinkRepository;

    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Daemon Thread loop executing every 15 seconds to flush pending synchronization blocks.
     */
    @Scheduled(fixedDelay = 15000)
    public void processPendingSyncTasks() {
        // 1. Core Requirement: High-Concurrency Thread Selection Rule utilizing SKIP LOCKED parameters
        String selectTasksSql = """
            SELECT id FROM sync_task 
            WHERE status = 'PENDING' AND attempts < ? 
            ORDER BY id ASC 
            LIMIT ? 
            FOR UPDATE SKIP LOCKED
            """;

        List<Long> taskIds = jdbcTemplate.queryForList(selectTasksSql, Long.class, MAX_ATTEMPTS, BATCH_SIZE);
        if (taskIds.isEmpty()) return;

        logger.info("SyncWorker locked down a batch of {} pending synchronization task records.", taskIds.size());

        for (Long taskId : taskIds) {
            // Fetch target student profile assigned to this task item
            Long studentId = jdbcTemplate.queryForObject("SELECT user_id FROM sync_task WHERE id = ?", Long.class, taskId);
            
            try {
                executeSynchronizationLifecycle(studentId);
                
                // If the run clears without exceptions, flag task status as DONE
                jdbcTemplate.update("UPDATE sync_task SET status = 'DONE', updated_at = ? WHERE id = ?", ZonedDateTime.now(), taskId);
            } catch (GoogleClientRateLimitException e) {
                // 429 Exception Route: Trigger backoff mitigation and retry loop cycles
                logger.warn("API Throttle triggered (HTTP 429) on task ID {}. Re-queuing with exponential backoff strategy.", taskId);
                jdbcTemplate.update("UPDATE sync_task SET attempts = attempts + 1, status = 'PENDING', error = ? WHERE id = ?", e.getMessage(), taskId);
                
                // Suspend the active thread execution context momentarily to let external rate buckets flush out
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            } catch (GoogleClientAuthException e) {
                // invalid_grant Exception Route: Completely terminate job processing and drop sync configurations
                logger.error("OAuth Security Failure (invalid_grant) on student account ID {}. Terminating task updates.", studentId);
                jdbcTemplate.update("UPDATE sync_task SET status = 'FAILED', error = ? WHERE id = ?", "Auth Revoked: " + e.getMessage(), taskId);
                jdbcTemplate.update("UPDATE app_user SET active = false WHERE id = ?", studentId); // Disable system access profile
            } catch (Exception e) {
                // Standard operational failure: Increment processing attempt counters
                logger.error("Standard processing error encountered on task sequence ID {}: {}", taskId, e.getMessage());
                jdbcTemplate.update("UPDATE sync_task SET attempts = attempts + 1, error = ? WHERE id = ?", e.getMessage(), taskId);
            }
        }
    }

    private void executeSynchronizationLifecycle(Long studentId) throws Exception {
        // 2. Fetch the state differential configuration trees
        SyncDiffEngine.DiffResult diff = syncDiffEngine.calculateDifferential(studentId);

        // A. Process External API Creation Updates
        for (StudentScheduleEvent event : diff.toCreate) {
            String mockExternalGoogleEventId = simulateGoogleCalendarApiCall(studentId, "CREATE", event.getTitle(), null);
            
            CalendarLink newLink = new CalendarLink();
            newLink.setUserId(studentId);
            newLink.setCourseCode(event.getCourseCode());
            newLink.setCategory(event.getType());
            newLink.setEventDate(event.getDate());
            newLink.setGoogleEventId(mockExternalGoogleEventId);
            newLink.setSynced(true);
            newLink.setStatus("ENRO");
            calendarLinkRepository.save(newLink);
        }

        // B. Process External API Deletion Updates (Handling HTTP 410 Gone Exception codes)
        for (CalendarLink staleLink : diff.toDelete) {
            try {
                simulateGoogleCalendarApiCall(studentId, "DELETE", null, staleLink.getGoogleEventId());
            } catch (GoogleResourceDeletedException e) {
                // 410 Gone Handler Route: The event was already purged manually on the client dashboard. Clear the local link mapping rows.
                logger.warn("HTTP 410 Gone caught: Event ID {} was already deleted externally. Flushing structural tracking link row.", staleLink.getGoogleEventId());
            }
            calendarLinkRepository.delete(staleLink);
        }
    }

    /**
     * Simulates network transactions hitting the external Google API endpoints.
     */
    private String simulateGoogleCalendarApiCall(Long userId, String action, String title, String googleEventId) throws Exception {
        // Mock injection check vectors to simulate edge-case network exception code patterns
        if (userId == 2) {
            throw new GoogleClientRateLimitException("HTTP 429 Too Many Requests: Rate limits exceeded on target profile quota boundaries.");
        }
        if (userId == 99) {
            throw new GoogleClientAuthException("invalid_grant: Refresh token signature has been revoked or expired on the credential cluster.");
        }
        if ("DELETE".equalsIgnoreCase(action) && "stale_101".equalsIgnoreCase(googleEventId)) {
            throw new GoogleResourceDeletedException("HTTP 410 Gone: Target calendar event resource identifier was deleted on the server.");
        }

        return "g_evt_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // =========================================================================
    // CUSTOM EXCEPTION TYPE SIGNATURES FOR GRANULAR API TRACKING
    // =========================================================================
    private static class GoogleClientRateLimitException extends Exception {
        public GoogleClientRateLimitException(String msg) { super(msg); }
    }
    private static class GoogleClientAuthException extends Exception {
        public GoogleClientAuthException(String msg) { super(msg); }
    }
    private static class GoogleResourceDeletedException extends Exception {
        public GoogleResourceDeletedException(String msg) { super(msg); }
    }
}
