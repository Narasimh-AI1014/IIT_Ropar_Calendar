package com.iitrpr.calendar.imports;

import com.iitrpr.calendar.domain.AppUser;
import com.iitrpr.calendar.domain.AppUserRepository;
import com.iitrpr.calendar.errors.BadRequestException;
import com.iitrpr.calendar.student.StudentSyncService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Saves rows that the admin site has already read and the admin has checked.
 * The server validates every row again before writing to the database.
 * Supported kinds: event, quiz, exam, classes.
 */
@RestController
@RequestMapping("/api/admin/imports")
public class ImportCommitController {

    public static class Row {
        public Integer line;
        public String title;
        public String date;        // YYYY-MM-DD
        public Integer day;        // 1 = Monday ... 7 = Sunday
        public String start;       // HH:mm
        public String end;         // HH:mm
        public String venue;
        public String people;
        public String description;
        public String category;
        public String course_code;
        public String audience;
    }

    public static class CommitRequest {
        public String kind;
        public List<Row> rows;
    }

    private static final Set<String> EVENT_CATEGORIES =
            Set.of("event", "quiz", "lecture_alteration", "exam");

    @Autowired
    private StudentSyncService studentSync;

    @PersistenceContext
    private EntityManager em;

    private final AppUserRepository users;

    public ImportCommitController(AppUserRepository users) {
        this.users = users;
    }

    @PostMapping("/commit")
    @Transactional
    public Map<String, Object> commit(@RequestBody CommitRequest req, Authentication auth) {
        if (req == null || req.kind == null || req.rows == null || req.rows.isEmpty()) {
            throw new BadRequestException("Nothing to import.");
        }
        AppUser admin = users.findByEmail(auth.getName().toLowerCase())
                .orElseThrow(() -> new BadRequestException("Logged-in user not found."));

        String kind = req.kind.toLowerCase().trim();
        int saved = 0;
        int duplicates = 0;

        for (Row r : req.rows) {
            switch (kind) {
                case "event", "quiz" -> {
                    if (saveEvent(r, kind, admin.getId())) saved++; else duplicates++;
                }
                case "exam" -> {
                    saveExam(r);
                    saved++;
                }
                case "classes" -> {
                    saveClass(r);
                    saved++;
                }
                default -> throw new BadRequestException(
                        "Importing '" + kind + "' files is not available yet.");
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", saved);
        out.put("duplicates", duplicates);
        if (saved > 0) studentSync.scheduleAllConnected();   // update connected students' Google calendars
        return out;
    }

    // ---------------- events and quizzes ----------------
    private boolean saveEvent(Row r, String kind, Long adminId) {
        require(r.title, "Title is missing on line " + r.line);
        require(r.date, "Date is missing on line " + r.line);

        String category = r.category == null ? "event" : r.category.toLowerCase().trim();
        if ("quiz".equals(kind)) category = "quiz";
        if (!EVENT_CATEGORIES.contains(category)) category = "event";

        String description = buildDescription(r);
        String courseCode = clean(r.course_code);
        String hash = sha256(r.title.trim() + "|" + r.date + "|" + nz(r.start) + "|" + nz(r.venue));

        List<?> inserted = em.createNativeQuery("""
                INSERT INTO event (title, category, event_date, start_time, end_time, venue, description,
                                   created_by, cancelled, notify, source_hash)
                VALUES (:title, :cat, CAST(:d AS date), CAST(:st AS time), CAST(:en AS time), :venue, :descr,
                        :by, false, false, :hash)
                ON CONFLICT (source_hash) DO NOTHING
                RETURNING id
                """)
                .setParameter("title", r.title.trim())
                .setParameter("cat", category)
                .setParameter("d", r.date)
                .setParameter("st", blankToNull(r.start))
                .setParameter("en", blankToNull(r.end))
                .setParameter("venue", blankToNull(r.venue))
                .setParameter("descr", blankToNull(description))
                .setParameter("by", adminId)
                .setParameter("hash", hash)
                .getResultList();

        if (inserted.isEmpty()) return false; // same event was imported before

        Long eventId = ((Number) inserted.get(0)).longValue();
        if (courseCode != null) {
            ensureCourse(courseCode, null);
            em.createNativeQuery("INSERT INTO event_audience (event_id, type, course_code) VALUES (:e, 'COURSES', :c)")
                    .setParameter("e", eventId).setParameter("c", courseCode).executeUpdate();
        } else {
            em.createNativeQuery("INSERT INTO event_audience (event_id, type) VALUES (:e, 'ALL')")
                    .setParameter("e", eventId).executeUpdate();
        }
        return true;
    }

    // ---------------- exams ----------------
    private void saveExam(Row r) {
        String code = clean(r.course_code);
        require(code, "Course code is missing on line " + r.line);
        require(r.date, "Date is missing on line " + r.line);
        ensureCourse(code, r.title);

        String session = "";
        if (r.start != null && !r.start.isBlank()) {
            int hour = Integer.parseInt(r.start.substring(0, 2));
            session = hour < 12 ? "FN" : "AN";
        }
        em.createNativeQuery("""
                INSERT INTO exam (course_code, exam_date, session, start_time, end_time, notes)
                VALUES (:c, CAST(:d AS date), :s, CAST(:st AS time), CAST(:en AS time), :n)
                ON CONFLICT (course_code, exam_date, session)
                DO UPDATE SET start_time = EXCLUDED.start_time, end_time = EXCLUDED.end_time, notes = EXCLUDED.notes
                """)
                .setParameter("c", code)
                .setParameter("d", r.date)
                .setParameter("s", session)
                .setParameter("st", blankToNull(r.start))
                .setParameter("en", blankToNull(r.end))
                .setParameter("n", blankToNull(r.venue))
                .executeUpdate();
    }

    // ---------------- weekly classes ----------------
    private void saveClass(Row r) {
        String code = clean(r.course_code);
        require(code, "Course code is missing on line " + r.line);
        if (r.day == null || r.day < 1 || r.day > 7) {
            throw new BadRequestException("Weekday is missing on line " + r.line);
        }
        require(r.start, "Start time is missing on line " + r.line);
        require(r.end, "End time is missing on line " + r.line);
        ensureCourse(code, r.title);

        String slotCode = "IMP" + r.day + "-" + r.start.replace(":", "") + "-" + r.end.replace(":", "");
        em.createNativeQuery("""
                INSERT INTO slot_time (slot_code, weekday, start_time, end_time)
                VALUES (:s, :w, CAST(:st AS time), CAST(:en AS time))
                ON CONFLICT (slot_code, weekday) DO NOTHING
                """)
                .setParameter("s", slotCode)
                .setParameter("w", r.day)
                .setParameter("st", r.start)
                .setParameter("en", r.end)
                .executeUpdate();
        em.createNativeQuery("""
                INSERT INTO course_slot (course_code, grp, slot_code, room)
                VALUES (:c, '', :s, :room)
                ON CONFLICT (course_code, grp, slot_code) DO UPDATE SET room = EXCLUDED.room
                """)
                .setParameter("c", code)
                .setParameter("s", slotCode)
                .setParameter("room", blankToNull(r.venue))
                .executeUpdate();
    }

    // ---------------- helpers ----------------
    private void ensureCourse(String code, String title) {
        String t = (title == null || title.isBlank()) ? code : title.trim();
        em.createNativeQuery("INSERT INTO course (code, title) VALUES (:c, :t) ON CONFLICT (code) DO NOTHING")
                .setParameter("c", code).setParameter("t", t).executeUpdate();
    }

    private String buildDescription(Row r) {
        List<String> parts = new ArrayList<>();
        if (r.people != null && !r.people.isBlank()) parts.add("Who: " + r.people.trim());
        if (r.description != null && !r.description.isBlank()) parts.add(r.description.trim());
        return String.join(". ", parts);
    }

    private String clean(String s) {
        return (s == null || s.isBlank()) ? null : s.trim().toUpperCase();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private void require(String s, String message) {
        if (s == null || s.isBlank()) throw new BadRequestException(message);
    }

    private String sha256(String input) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
