package com.iitrpr.calendar.student;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Works out what a student should have in their calendar (read only). */
@Service
public class StudentPlanService {

    @PersistenceContext
    private EntityManager em;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    // Courses the student has selected: enrolled ones (unless unticked) + ticked electives
    public static final String MY_COURSES = """
            WITH my_courses AS (
                SELECT c.code FROM course c
                LEFT JOIN enrolment e ON e.course_code = c.code AND e.user_id = :uid AND e.status = 'ENRO'
                LEFT JOIN student_pick p ON p.user_id = :uid AND p.item_type = 'COURSE' AND p.item_ref = c.code
                WHERE COALESCE(p.picked, e.user_id IS NOT NULL)
            )
            """;

    // ---------- preferences ----------
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Map<String, Object> prefs(Long uid) {
        List<Object[]> r = em.createNativeQuery(
                "SELECT want_quiz, want_exam, want_change, want_event FROM student_pref WHERE user_id = :u")
                .setParameter("u", uid).getResultList();
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.isEmpty()) {
            m.put("quiz", true); m.put("exam", true); m.put("change", true); m.put("event", false);
        } else {
            m.put("quiz", Boolean.TRUE.equals(r.get(0)[0]));
            m.put("exam", Boolean.TRUE.equals(r.get(0)[1]));
            m.put("change", Boolean.TRUE.equals(r.get(0)[2]));
            m.put("event", Boolean.TRUE.equals(r.get(0)[3]));
        }
        return m;
    }

    // ---------- upcoming items with their ticks ----------
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> items(Long uid) {
        Map<String, Boolean> picks = new HashMap<>();
        List<Object[]> pr = em.createNativeQuery(
                "SELECT item_type, item_ref, picked FROM student_pick WHERE user_id = :uid AND item_type IN ('EVENT','EXAM')")
                .setParameter("uid", uid).getResultList();
        for (Object[] r : pr) picks.put(r[0] + ":" + r[1], Boolean.TRUE.equals(r[2]));

        Map<String, Object> pf = prefs(uid);
        List<Map<String, Object>> list = new ArrayList<>();

        List<Object[]> ev = em.createNativeQuery(MY_COURSES + """
                SELECT DISTINCT e.id, e.title, e.event_date, e.start_time, e.end_time, e.venue, e.category
                FROM event e
                JOIN event_audience ea ON ea.event_id = e.id
                WHERE e.cancelled = false
                  AND e.event_date >= CURRENT_DATE
                  AND (
                      ea.type = 'ALL'
                      OR (ea.type = 'COURSES' AND ea.course_code IN (SELECT code FROM my_courses))
                      OR (ea.type = 'GROUP'
                          AND (ea.dept_code IS NULL OR ea.dept_code = (SELECT dept_code FROM app_user WHERE id = :uid))
                          AND (ea.entry_year IS NULL OR ea.entry_year = (SELECT entry_year FROM app_user WHERE id = :uid)))
                  )
                ORDER BY e.event_date, e.start_time
                """).setParameter("uid", uid).getResultList();
        for (Object[] r : ev) {
            String ref = String.valueOf(((Number) r[0]).longValue());
            String cat = r[6] == null ? "event" : r[6].toString().toLowerCase();
            boolean def = switch (cat) {                  // the student's standing preference
                case "quiz" -> Boolean.TRUE.equals(pf.get("quiz"));
                case "exam" -> Boolean.TRUE.equals(pf.get("exam"));
                case "lecture_alteration" -> Boolean.TRUE.equals(pf.get("change"));
                default -> Boolean.TRUE.equals(pf.get("event"));
            };
            list.add(item("EVENT", ref, r[1].toString(), r[2], r[3], r[4], r[5], cat.toUpperCase(),
                    picks.getOrDefault("EVENT:" + ref, def)));
        }

        List<Object[]> ex = em.createNativeQuery(MY_COURSES + """
                SELECT ex.id, c.title, ex.session, ex.exam_date, ex.start_time, ex.end_time, ex.notes
                FROM exam ex
                JOIN my_courses mc ON mc.code = ex.course_code
                JOIN course c ON c.code = ex.course_code
                WHERE ex.exam_date >= CURRENT_DATE
                ORDER BY ex.exam_date, ex.start_time
                """).setParameter("uid", uid).getResultList();
        for (Object[] r : ex) {
            String ref = String.valueOf(((Number) r[0]).longValue());
            String sess = r[2] == null ? "" : r[2].toString();
            String title = r[1] + " - Exam" + (sess.isEmpty() ? "" : " (" + sess + ")");
            list.add(item("EXAM", ref, title, r[3], r[4], r[5], r[6], "EXAM",
                    picks.getOrDefault("EXAM:" + ref, Boolean.TRUE.equals(pf.get("exam")))));
        }

        list.sort(Comparator.comparing((Map<String, Object> m) -> m.get("date").toString())
                .thenComparing(m -> String.valueOf(m.get("start"))));
        return list;
    }

    // ---------- the full plan the calendar should contain ----------
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public Map<String, Object> buildPlan(Long uid) {
        // current term (or the latest one)
        List<Object[]> terms = em.createNativeQuery("""
                SELECT id, start_date, end_date FROM term
                ORDER BY (end_date >= CURRENT_DATE) DESC,
                         CASE WHEN end_date >= CURRENT_DATE THEN start_date END ASC,
                         end_date DESC
                LIMIT 1
                """).getResultList();

        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> classes = new ArrayList<>();
        List<String> skip = new ArrayList<>();

        if (!terms.isEmpty()) {
            Object[] t = terms.get(0);
            Long termId = ((Number) t[0]).longValue();
            LocalDate termStart = LocalDate.parse(t[1].toString());
            LocalDate termEnd = LocalDate.parse(t[2].toString());
            out.put("termStart", termStart.toString());
            out.put("termEnd", termEnd.toString());

            // days with no classes: term holidays + campus holidays / no-class days
            List<Object> days = em.createNativeQuery("""
                    SELECT CAST(day AS text) FROM holiday WHERE term_id = :t
                    UNION
                    SELECT CAST(CAST(d AS date) AS text) FROM (
                        SELECT generate_series(start_date, COALESCE(end_date, start_date), interval '1 day') AS d
                        FROM campus_item
                        WHERE category IN ('HOLIDAY', 'NOCLASS')
                          AND start_date <= CAST(:te AS date)
                          AND COALESCE(end_date, start_date) >= CAST(:ts AS date)
                    ) x
                    """)
                    .setParameter("t", termId)
                    .setParameter("te", termEnd.toString())
                    .setParameter("ts", termStart.toString())
                    .getResultList();
            for (Object d : days) {
                String s = d.toString();
                if (s.compareTo(termStart.toString()) >= 0 && s.compareTo(termEnd.toString()) <= 0) skip.add(s);
            }
            Collections.sort(skip);

            List<Object[]> rows = em.createNativeQuery(MY_COURSES + """
                    SELECT c.code, c.title, st.weekday, st.start_time, st.end_time, cs.room, cs.slot_code
                    FROM my_courses mc
                    JOIN course c ON c.code = mc.code
                    LEFT JOIN enrolment e ON e.course_code = c.code AND e.user_id = :uid AND e.status = 'ENRO'
                    JOIN course_slot cs ON cs.course_code = c.code
                         AND (cs.grp = '' OR e.grp IS NULL OR cs.grp = e.grp)
                    JOIN slot_time st ON st.slot_code = cs.slot_code
                    ORDER BY c.code, st.weekday, st.start_time
                    """).setParameter("uid", uid).getResultList();

            Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
            for (Object[] r : rows) {
                int weekday = ((Number) r[2]).intValue();
                // First lesson of the term (stable, so the calendar entry does not change every day)
                LocalDate first = termStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)));
                if (first.isAfter(termEnd)) continue;
                String start = hhmm(r[3]);
                String key = "cls-" + r[0] + "-" + weekday + "-" + start.replace(":", "");
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", key);
                m.put("code", r[0]);
                m.put("title", r[1]);
                m.put("weekday", weekday);          // 1 = Monday ... 7 = Sunday
                m.put("firstDate", first.toString());
                m.put("until", termEnd.toString());
                m.put("start", start);
                m.put("end", hhmm(r[4]));
                m.put("room", r[5] == null ? "" : r[5]);
                byKey.putIfAbsent(key, m);
            }
            classes.addAll(byKey.values());
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> it : items(uid)) {
            if (Boolean.TRUE.equals(it.get("picked"))) {
                Map<String, Object> m = new LinkedHashMap<>(it);
                m.put("key", ("EXAM".equals(it.get("type")) ? "exm-" : "evt-") + it.get("ref"));
                m.remove("picked");
                events.add(m);
            }
        }

        out.put("classes", classes);
        out.put("events", events);
        out.put("skipDates", skip);
        return out;
    }

    // ---------- helpers ----------
    private Map<String, Object> item(String type, String ref, String title, Object date, Object start,
                                     Object end, Object venue, String category, boolean picked) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("ref", ref);
        m.put("title", title);
        m.put("date", date.toString());
        m.put("start", start == null ? null : hhmm(start));
        m.put("end", end == null ? null : hhmm(end));
        m.put("venue", venue == null ? "" : venue.toString());
        m.put("category", category);
        m.put("picked", picked);
        return m;
    }

    private String hhmm(Object t) {
        if (t == null) return "00:00";
        if (t instanceof java.sql.Time) return ((java.sql.Time) t).toLocalTime().format(HHMM);
        if (t instanceof LocalTime) return ((LocalTime) t).format(HHMM);
        String s = t.toString();
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }
}
