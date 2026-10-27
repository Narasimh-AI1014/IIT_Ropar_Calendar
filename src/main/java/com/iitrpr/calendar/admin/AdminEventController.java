package com.iitrpr.calendar.admin;

import com.iitrpr.calendar.domain.*;
import com.iitrpr.calendar.errors.BadRequestException;
import com.iitrpr.calendar.matching.AudienceCriteria;
import com.iitrpr.calendar.matching.AudienceResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.iitrpr.calendar.student.StudentSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Admin posts one event. The browser sends the fields below (same names as the
 * admin site's Create Event form). The admin's identity comes from the login token.
 */
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    @Autowired
    private StudentSyncService studentSync;

    public static class AudiencePayload {
        public String type;
        public List<String> courses;
        public List<String> departments;
        public List<Integer> entryYears;
    }

    public static class EventPayload {
        public String title;
        public String category;
        public LocalDate date;
        public LocalTime start;
        public LocalTime end;
        public String venue;
        public String description;
        public Boolean notify;
        public AudiencePayload audience;
    }

    @PersistenceContext
    private EntityManager em;

    private final EventRepository eventRepository;
    private final EventAudienceRepository eventAudienceRepository;
    private final AppUserRepository appUserRepository;
    private final AudienceResolver audienceResolver;
    private final AuditLogRepository auditLogRepository;

    public AdminEventController(EventRepository eventRepository,
                                EventAudienceRepository eventAudienceRepository,
                                AppUserRepository appUserRepository,
                                AudienceResolver audienceResolver,
                                AuditLogRepository auditLogRepository) {
        this.eventRepository = eventRepository;
        this.eventAudienceRepository = eventAudienceRepository;
        this.appUserRepository = appUserRepository;
        this.audienceResolver = audienceResolver;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> create(@RequestBody EventPayload p, Authentication auth) {
        if (p.title == null || p.title.isBlank()) throw new BadRequestException("Title is required.");
        if (p.date == null) throw new BadRequestException("Date is required.");
        if (p.audience == null || p.audience.type == null) throw new BadRequestException("Audience is required.");

        AppUser admin = appUserRepository.findByEmail(auth.getName().toLowerCase())
                .orElseThrow(() -> new BadRequestException("Logged-in user not found."));

        Event event = new Event();
        event.setTitle(p.title.trim());
        event.setCategory(p.category == null ? "event" : p.category.toLowerCase().trim());
        event.setEventDate(p.date);
        event.setStartTime(p.start);
        event.setEndTime(p.end);
        event.setVenue(p.venue);
        event.setDescription(p.description);
        event.setCreatedBy(admin.getId());
        event.setCancelled(false);
        event.setNotify(Boolean.TRUE.equals(p.notify));
        Event saved = eventRepository.save(event);

        AudienceCriteria criteria = new AudienceCriteria();
        criteria.setType(p.audience.type.toUpperCase());
        criteria.setCourseCodes(p.audience.courses);
        criteria.setDeptCodes(p.audience.departments);
        criteria.setEntryYears(p.audience.entryYears);
        eventAudienceRepository.saveAll(audienceResolver.resolveAudienceRows(saved.getId(), criteria));

        int reached = countReached(criteria);

        AuditLog audit = new AuditLog();
        audit.setUserId(admin.getId());
        audit.setAction("CREATE_EVENT");
        audit.setDetail("Admin created event " + saved.getId() + " [" + saved.getTitle() + "]");
        audit.setCallingSite("admin");
        auditLogRepository.save(audit);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", saved.getId());
        out.put("reached", reached);
        studentSync.scheduleAllConnected();   // add it to connected students' Google calendars
        return ResponseEntity.ok(out);
    }

    private int countReached(AudienceCriteria c) {
        String type = c.getType();
        String sql;
        Map<String, Object> params = new HashMap<>();
        if ("ALL".equals(type)) {
            sql = "SELECT COUNT(*) FROM app_user WHERE role='STUDENT' AND active=true";
        } else if ("COURSES".equals(type)) {
            sql = "SELECT COUNT(DISTINCT user_id) FROM enrolment WHERE status='ENRO' AND course_code IN (:codes)";
            params.put("codes", c.getCourseCodes());
        } else {
            StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM app_user WHERE role='STUDENT' AND active=true");
            if (c.getDeptCodes() != null && !c.getDeptCodes().isEmpty()) {
                sb.append(" AND dept_code IN (:depts)");
                params.put("depts", c.getDeptCodes());
            }
            if (c.getEntryYears() != null && !c.getEntryYears().isEmpty()) {
                sb.append(" AND entry_year IN (:years)");
                params.put("years", c.getEntryYears());
            }
            sql = sb.toString();
        }
        var q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return ((Number) q.getSingleResult()).intValue();
    }
}
