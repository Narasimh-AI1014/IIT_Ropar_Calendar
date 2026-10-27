package com.iitrpr.calendar.sync;

import com.iitrpr.calendar.domain.CalendarLink;
import com.iitrpr.calendar.domain.CalendarLinkRepository;
import com.iitrpr.calendar.student.StudentScheduleController;
import com.iitrpr.calendar.student.StudentScheduleEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SyncDiffEngine {

    @Autowired
    private StudentScheduleController studentScheduleController;

    @Autowired
    private CalendarLinkRepository calendarLinkRepository;

    public static class DiffResult {
        public List<StudentScheduleEvent> toCreate = new ArrayList<>();
        public List<CalendarLink> toUpdate = new ArrayList<>();
        public List<CalendarLink> toDelete = new ArrayList<>();
    }

    /**
     * Calculates the exact state differential mapping grid parameters for a student target.
     */
    public DiffResult calculateDifferential(Long studentId) {
        DiffResult diff = new DiffResult();

        // 1. Fetch what the local database matrix says the schedule should look like
        List<StudentScheduleEvent> desiredEvents = studentScheduleController.getMySchedule(studentId).getBody();
        if (desiredEvents == null) desiredEvents = List.of();

        // 2. Fetch all active external event links stored historically in our table index
        List<CalendarLink> externalLinks = calendarLinkRepository.findByUserId(studentId);

        Map<String, CalendarLink> linkMap = new HashMap<>();
        for (CalendarLink link : externalLinks) {
            // Index by local functional identifier keys
            String uniqueKey = link.getCourseCode() + "_" + link.getCategory() + "_" + link.getEventDate();
            linkMap.put(uniqueKey.toLowerCase(), link);
        }

        Set<String> processedKeys = new HashSet<>();

        // 3. Evaluate Desired state rows against current external link references
        for (StudentScheduleEvent desired : desiredEvents) {
            String key = (desired.getCourseCode() + "_" + desired.getType() + "_" + desired.getDate()).toLowerCase();
            processedKeys.add(key);

            if (!linkMap.containsKey(key)) {
                // Scenario A: Staged row is completely untracked externally -> Queue Creation
                diff.toCreate.add(desired);
            } else {
                CalendarLink existingLink = linkMap.get(key);
                // Scenario B: Link exists but is flagged as cancelled locally -> Queue Deletion
                if (existingLink.getSynced() && !"ENRO".equalsIgnoreCase(existingLink.getStatus())) {
                    diff.toDelete.add(existingLink);
                }
            }
        }

        // 4. Any historical active link not matched inside the fresh desired matrix is stale -> Queue Deletion
        for (Map.Entry<String, CalendarLink> entry : linkMap.entrySet()) {
            if (!processedKeys.contains(entry.getKey()) && entry.getValue().getSynced()) {
                diff.toDelete.add(entry.getValue());
            }
        }

        return diff;
    }
}
