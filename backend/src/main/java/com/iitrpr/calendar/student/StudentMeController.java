package com.iitrpr.calendar.student;

import com.iitrpr.calendar.domain.AppUser;
import com.iitrpr.calendar.domain.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Endpoints used by the student website. The student is identified from the
 * login token (email), so no studentId is ever sent by the browser.
 */
@RestController
@RequestMapping("/api/student")
public class StudentMeController {

    @PersistenceContext
    private EntityManager em;

    private final AppUserRepository users;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public StudentMeController(AppUserRepository users) {
        this.users = users;
    }

    private AppUser me(Authentication auth) {
        return users.findByEmail(auth.getName().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("User not found: " + auth.getName()));
    }

    // ---------- 1. Courses ----------
    @SuppressWarnings("unchecked")
    @GetMapping("/courses")
    public List<Map<String, Object>> courses(Authentication auth) {
        AppUser u = me(auth);
        List<Object[]> rows = em.createNativeQuery("""
                SELECT c.code, c.title, c.instructor_names, e.grp, e.status
                FROM enrolment e JOIN course c ON c.code = e.course_code
                WHERE e.user_id = :uid AND e.status = 'ENRO'
                ORDER BY c.code
                """).setParameter("uid", u.getId()).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);          // course code is the id
            m.put("code", r[0]);
            m.put("name", r[1]);
            m.put("type", "CORE");      // everything enrolled counts as core
            m.put("instructor", r[2]);
            m.put("group", r[3]);
            out.add(m);
        }
        return out;
    }

    // ---------- 2. Electives (enrolment-driven, so nothing to choose yet) ----------
    @GetMapping("/me/electives")
    public List<Object> getElectives() {
        return new ArrayList<>();
    }

    @PutMapping("/me/electives")
    public Map<String, Object> saveElectives(@RequestBody(required = false) Map<String, Object> body) {
        return Map.of("ok", true);
    }

    // ---------- 3. Schedule ----------
    @SuppressWarnings("unchecked")
    @GetMapping("/me/schedule")
    public Map<String, Object> schedule(Authentication auth) {
        AppUser u = me(auth);
        Long uid = u.getId();

        // weekly classes
        List<Object[]> classRows = em.createNativeQuery("""
                SELECT c.code, st.weekday, st.start_time, st.end_time, cs.room
                FROM enrolment e
                JOIN course c ON e.course_code = c.code
                JOIN course_slot cs ON c.code = cs.course_code AND (cs.grp = '' OR cs.grp = e.grp)
                JOIN slot_time st ON cs.slot_code = st.slot_code
                WHERE e.user_id = :uid AND e.status = 'ENRO'
                """).setParameter("uid", uid).getResultList();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (Object[] r : classRows) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("courseId", r[0]);
            s.put("day", ((Number) r[1]).intValue());
            s.put("start", hhmm(r[2]));
            s.put("end", hhmm(r[3]));
            s.put("room", r[4] == null ? "" : r[4]);
            slots.add(s);
        }

        List<Map<String, Object>> events = new ArrayList<>();

        // exams
        List<Object[]> examRows = em.createNativeQuery("""
                SELECT ex.id, c.title, ex.session, ex.exam_date, ex.start_time, ex.end_time, ex.notes
                FROM enrolment e
                JOIN course c ON e.course_code = c.code
                JOIN exam ex ON c.code = ex.course_code
                WHERE e.user_id = :uid AND e.status = 'ENRO'
                """).setParameter("uid", uid).getResultList();
        for (Object[] r : examRows) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("id", 1_000_000L + ((Number) r[0]).longValue());
            ev.put("title", r[1] + " - Exam" + (r[2] == null || r[2].toString().isEmpty() ? "" : " (" + r[2] + ")"));
            ev.put("date", r[3].toString());
            ev.put("start", hhmm(r[4]));
            ev.put("end", hhmm(r[5]));
            ev.put("venue", "Exam Hall");
            ev.put("category", "EXAM");
            events.add(ev);
        }

        // posted events (admin / faculty)
        List<Object[]> eventRows = em.createNativeQuery("""
                SELECT DISTINCT e.id, e.title, e.event_date, e.start_time, e.end_time, e.venue, e.category
                FROM event e
                JOIN event_audience ea ON e.id = ea.event_id
                WHERE e.cancelled = false
                  AND (
                      ea.type = 'ALL'
                      OR (ea.type = 'COURSES' AND ea.course_code IN (
                          SELECT course_code FROM enrolment WHERE user_id = :uid AND status = 'ENRO'))
                      OR (ea.type = 'GROUP'
                          AND (ea.dept_code IS NULL OR ea.dept_code = (SELECT dept_code FROM app_user WHERE id = :uid))
                          AND (ea.entry_year IS NULL OR ea.entry_year = (SELECT entry_year FROM app_user WHERE id = :uid)))
                  )
                ORDER BY e.event_date, e.start_time
                """).setParameter("uid", uid).getResultList();
        for (Object[] r : eventRows) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("id", ((Number) r[0]).longValue());
            ev.put("title", r[1]);
            ev.put("date", r[2].toString());
            ev.put("start", hhmm(r[3]));
            ev.put("end", hhmm(r[4]));
            ev.put("venue", r[5] == null ? "" : r[5]);
            ev.put("category", r[6] == null ? "EVENT" : r[6].toString().toUpperCase());
            events.add(ev);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slots", slots);
        result.put("events", events);
        return result;
    }

    private String hhmm(Object t) {
        if (t == null) return "00:00";
        if (t instanceof java.sql.Time) return ((java.sql.Time) t).toLocalTime().format(HHMM);
        if (t instanceof LocalTime) return ((LocalTime) t).format(HHMM);
        String s = t.toString();
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }
}
