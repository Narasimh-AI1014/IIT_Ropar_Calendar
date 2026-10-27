package com.iitrpr.calendar.student;

import com.iitrpr.calendar.domain.AppUser;
import com.iitrpr.calendar.domain.AppUserRepository;
import com.iitrpr.calendar.errors.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * What the student has chosen to put in Google Calendar.
 *  - GET  /api/student/me/selections  : courses + upcoming items, each with a tick
 *  - PUT  /api/student/me/selections  : save ticks
 *  - GET  /api/student/me/preferences : standing rules (quizzes, exams, changes, events)
 *  - PUT  /api/student/me/preferences
 *  - GET  /api/student/me/sync-plan   : exactly what should be in the calendar
 * Every change schedules a background sync for that student.
 */
@RestController
@RequestMapping("/api/student/me")
public class StudentPlanController {

    @PersistenceContext
    private EntityManager em;

    private final AppUserRepository users;
    private final StudentPlanService plans;
    private final StudentSyncService sync;

    public StudentPlanController(AppUserRepository users, StudentPlanService plans, StudentSyncService sync) {
        this.users = users;
        this.plans = plans;
        this.sync = sync;
    }

    private AppUser me(Authentication auth) {
        return users.findByEmail(auth.getName().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("User not found: " + auth.getName()));
    }

    // ---------- Selections ----------
    @SuppressWarnings("unchecked")
    @GetMapping("/selections")
    public Map<String, Object> selections(Authentication auth) {
        Long uid = me(auth).getId();

        List<Object[]> rows = em.createNativeQuery("""
                SELECT c.code, c.title, c.kind, c.instructor_names,
                       (e.user_id IS NOT NULL) AS enrolled, p.picked
                FROM course c
                LEFT JOIN enrolment e ON e.course_code = c.code AND e.user_id = :uid AND e.status = 'ENRO'
                LEFT JOIN student_pick p ON p.user_id = :uid AND p.item_type = 'COURSE' AND p.item_ref = c.code
                WHERE e.user_id IS NOT NULL
                   OR (c.kind = 'ELECTIVE' AND EXISTS (SELECT 1 FROM course_slot s WHERE s.course_code = c.code))
                ORDER BY c.kind, c.code
                """).setParameter("uid", uid).getResultList();

        List<Map<String, Object>> courses = new ArrayList<>();
        for (Object[] r : rows) {
            boolean enrolled = Boolean.TRUE.equals(r[4]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r[0]);
            m.put("name", r[1]);
            m.put("kind", r[2]);
            m.put("instructor", r[3]);
            m.put("enrolled", enrolled);
            m.put("picked", r[5] == null ? enrolled : Boolean.TRUE.equals(r[5]));
            courses.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("courses", courses);
        out.put("items", plans.items(uid));
        return out;
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/selections")
    @Transactional
    public Map<String, Object> saveSelections(Authentication auth, @RequestBody Map<String, Object> body) {
        Long uid = me(auth).getId();
        Object raw = body == null ? null : body.get("picks");
        if (!(raw instanceof List)) throw new BadRequestException("Send {\"picks\":[{type,ref,picked}]}");

        int saved = 0;
        for (Object o : (List<Object>) raw) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> p = (Map<String, Object>) o;
            String type = String.valueOf(p.get("type")).toUpperCase();
            String ref = p.get("ref") == null ? "" : String.valueOf(p.get("ref")).trim();
            boolean picked = Boolean.TRUE.equals(p.get("picked"));
            if (!(type.equals("COURSE") || type.equals("EVENT") || type.equals("EXAM")) || ref.isEmpty()) {
                throw new BadRequestException("Bad pick: " + type + " / " + ref);
            }
            if (type.equals("COURSE")) ref = ref.toUpperCase();
            em.createNativeQuery("""
                    INSERT INTO student_pick (user_id, item_type, item_ref, picked)
                    VALUES (:u, :t, :r, :p)
                    ON CONFLICT (user_id, item_type, item_ref)
                    DO UPDATE SET picked = EXCLUDED.picked, updated_at = now()
                    """)
                    .setParameter("u", uid).setParameter("t", type)
                    .setParameter("r", ref).setParameter("p", picked)
                    .executeUpdate();
            saved++;
        }
        sync.scheduleStudent(uid);
        return Map.of("ok", true, "saved", saved);
    }

    // ---------- Preferences (standing rules) ----------
    @GetMapping("/preferences")
    public Map<String, Object> preferences(Authentication auth) {
        return plans.prefs(me(auth).getId());
    }

    @PutMapping("/preferences")
    @Transactional
    public Map<String, Object> savePreferences(Authentication auth, @RequestBody Map<String, Object> body) {
        Long uid = me(auth).getId();
        Map<String, Object> cur = plans.prefs(uid);
        for (String k : new String[]{"quiz", "exam", "change", "event"}) {
            if (body != null && body.get(k) instanceof Boolean) cur.put(k, body.get(k));
        }
        em.createNativeQuery("""
                INSERT INTO student_pref (user_id, want_quiz, want_exam, want_change, want_event)
                VALUES (:u, :q, :x, :c, :e)
                ON CONFLICT (user_id) DO UPDATE SET
                    want_quiz = EXCLUDED.want_quiz, want_exam = EXCLUDED.want_exam,
                    want_change = EXCLUDED.want_change, want_event = EXCLUDED.want_event,
                    updated_at = now()
                """)
                .setParameter("u", uid)
                .setParameter("q", cur.get("quiz")).setParameter("x", cur.get("exam"))
                .setParameter("c", cur.get("change")).setParameter("e", cur.get("event"))
                .executeUpdate();
        sync.scheduleStudent(uid);
        return cur;
    }

    // ---------- Sync plan (for checking) ----------
    @GetMapping("/sync-plan")
    public Map<String, Object> syncPlan(Authentication auth) {
        return plans.buildPlan(me(auth).getId());
    }
}
