package com.iitrpr.calendar.faculty;

import com.iitrpr.calendar.domain.*;
import com.iitrpr.calendar.errors.BadRequestException;
import com.iitrpr.calendar.matching.AudienceResolver;
import com.iitrpr.calendar.sync.SyncTaskDispatcher;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty")
public class FacultyEventController {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventAudienceRepository eventAudienceRepository;

    @Autowired
    private AudienceResolver audienceResolver;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SyncTaskDispatcher syncTaskDispatcher;

    // =========================================================================
    // 1. GET /api/faculty/courses - GET ALL ASSIGNED TEACHING COURSES
    // =========================================================================
    @GetMapping("/courses")
    public ResponseEntity<List<Course>> getMyCourses(@RequestParam Long facultyId) {
        return ResponseEntity.ok(courseRepository.findCoursesByInstructor(facultyId));
    }

    // =========================================================================
    // 2. GET /api/faculty/events - GET ALL CREATED EVENTS BY INSTRUCTOR
    // =========================================================================
    @GetMapping("/events")
    public ResponseEntity<List<Event>> getMyEvents(@RequestParam Long facultyId) {
        return ResponseEntity.ok(eventRepository.findByCreatedBy(facultyId));
    }

    // =========================================================================
    // 3. POST /api/faculty/events - CREATE EVENT WITH COURSE ROLE CROSS-CHECK
    // =========================================================================
    @PostMapping("/events")
    @Transactional
    public ResponseEntity<Event> createEvent(@RequestParam Long facultyId, @Valid @RequestBody FacultyEventRequest request) {
        // Enforce boundary constraint rule: If targeting a course, faculty must be assigned to teach it!
        if ("COURSES".equalsIgnoreCase(request.getAudience().getType())) {
            List<Course> teachingCourses = courseRepository.findCoursesByInstructor(facultyId);
            for (String targetCode : request.getAudience().getCourseCodes()) {
                boolean holdsAssignment = teachingCourses.stream().anyMatch(c -> c.getCode().equalsIgnoreCase(targetCode));
                if (!holdsAssignment) {
                    throw new BadRequestException("Access Denied: You are not authorized to target course " + targetCode);
                }
            }
        }

        Event event = new Event();
        event.setTitle(request.getTitle());
        event.setCategory(request.getCategory().toLowerCase().trim());
        event.setEventDate(request.getEventDate());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setVenue(request.getVenue());
        event.setDescription(request.getDescription());
        event.setCreatedBy(facultyId);

        Event savedEvent = eventRepository.save(event);

        List<EventAudience> audienceRows = audienceResolver.resolveAudienceRows(savedEvent.getId(), request.getAudience());
        eventAudienceRepository.saveAll(audienceRows);

        // A. Write to Immutable System Audit Log
        AuditLog audit = new AuditLog();
        audit.setUserId(facultyId);
        audit.setAction("CREATE_EVENT");
        audit.setDetail("Faculty created event ID: " + savedEvent.getId() + " [Title: " + savedEvent.getTitle() + "]");
        audit.setCallingSite("http://localhost:5176"); // Faculty Portal origin dashboard space
        auditLogRepository.save(audit);

        // B. Dispatch Automated Real-Time Calendar Sync Tracking Hooks
        syncTaskDispatcher.dispatchTasksForEvent(
            savedEvent.getId(),
            request.getAudience().getType().toUpperCase(),
            request.getAudience().getCourseCodes(),
            request.getAudience().getDeptCodes(),
            request.getAudience().getEntryYears()
        );

        return ResponseEntity.ok(savedEvent);
    }

    // =========================================================================
    // 4. PUT /api/faculty/events/{id} - UPDATE EXISTING FACULTY EVENT
    // =========================================================================
    @PutMapping("/events/{id}")
    @Transactional
    public ResponseEntity<Event> updateEvent(@PathVariable Long id, @RequestParam Long facultyId, @Valid @RequestBody FacultyEventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Event configuration target entry not found."));

        if (!event.getCreatedBy().equals(facultyId)) {
            throw new BadRequestException("Access Denied: You cannot modify an event created by another user.");
        }

        event.setTitle(request.getTitle());
        event.setCategory(request.getCategory().toLowerCase().trim());
        event.setEventDate(request.getEventDate());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setVenue(request.getVenue());
        event.setDescription(request.getDescription());

        Event updatedEvent = eventRepository.save(event);

        // A. Write to Immutable System Audit Log
        AuditLog audit = new AuditLog();
        audit.setUserId(facultyId);
        audit.setAction("UPDATE_EVENT");
        audit.setDetail("Faculty modified event ID: " + updatedEvent.getId());
        audit.setCallingSite("http://localhost:5176");
        auditLogRepository.save(audit);

        // B. Dispatch Automated Sync Tracking Hooks (Triggers 'ALL' to re-verify operational student slots)
        syncTaskDispatcher.dispatchTasksForEvent(null, "ALL", null, null, null);

        return ResponseEntity.ok(updatedEvent);
    }

    // =========================================================================
    // 5. DELETE /api/faculty/events/{id} - GRACEFULLY CANCEL COMPONENT EVENT
    // =========================================================================
    @DeleteMapping("/events/{id}")
    @Transactional
    public ResponseEntity<Void> cancelEvent(@PathVariable Long id, @RequestParam Long facultyId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Event configuration target entry not found."));

        if (!event.getCreatedBy().equals(facultyId)) {
            throw new BadRequestException("Access Denied: You cannot cancel an event created by another user.");
        }

        event.setCancelled(true); // Gracefully set cancelled flag without dropping key relational data maps
        eventRepository.save(event);

        // A. Write to Immutable System Audit Log
        AuditLog audit = new AuditLog();
        audit.setUserId(facultyId);
        audit.setAction("CANCEL_EVENT");
        audit.setDetail("Faculty cancelled event ID: " + event.getId());
        audit.setCallingSite("http://localhost:5176");
        auditLogRepository.save(audit);

        // B. Dispatch Automated Real-Time Sync Cancellation Tasks
        syncTaskDispatcher.dispatchTasksForEvent(null, "ALL", null, null, null);

        return ResponseEntity.noContent().build();
    }
}
