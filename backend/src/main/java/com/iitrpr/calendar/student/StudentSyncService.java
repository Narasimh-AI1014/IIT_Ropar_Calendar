package com.iitrpr.calendar.student;

import com.iitrpr.calendar.errors.BadRequestException;
import com.iitrpr.calendar.integration.GoogleOAuthClient;
import com.iitrpr.calendar.integration.GoogleOAuthClient.GoogleApiException;
import com.iitrpr.calendar.security.TokenCrypto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps each connected student's "University Timetable" Google calendar equal to their plan.
 * New items are added, changed items are updated, removed or unticked items are deleted.
 * Runs in one background thread, so two syncs never overlap.
 */
@Service
public class StudentSyncService {

    private static final Logger log = LoggerFactory.getLogger(StudentSyncService.class);
    private static final String CALENDAR_NAME = "University Timetable";
    private static final String TZ = "Asia/Kolkata";

    private final JdbcTemplate jdbc;
    private final StudentPlanService plans;
    private final GoogleOAuthClient google;
    private final TokenCrypto crypto;

    private final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "student-sync");
        t.setDaemon(true);
        return t;
    });
    private final Set<Long> pending = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean allPending = new AtomicBoolean(false);

    private record Desired(String key, String googleId, Map<String, Object> body, String fingerprint, String date) {}
    private record Existing(String googleId, String fingerprint, String date) {}

    public StudentSyncService(JdbcTemplate jdbc, StudentPlanService plans, GoogleOAuthClient google, TokenCrypto crypto) {
        this.jdbc = jdbc;
        this.plans = plans;
        this.google = google;
        this.crypto = crypto;
    }

    @PostConstruct
    void start() {
        // safety net: catches new enrolments, faculty posts, day changes
        pool.scheduleWithFixedDelay(this::safeAll, 2, 30, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }

    // ---------- triggers ----------

    /** Sync one student in a few seconds (many quick changes become one sync). */
    public void scheduleStudent(Long uid) {
        afterCommit(() -> {
            if (pending.add(uid)) {
                pool.schedule(() -> {
                    pending.remove(uid);
                    safeRun(uid);
                }, 3, TimeUnit.SECONDS);
            }
        });
    }

    /** Sync every connected student (after an admin posts or imports something). */
    public void scheduleAllConnected() {
        afterCommit(() -> {
            if (allPending.compareAndSet(false, true)) {
                pool.schedule(() -> {
                    allPending.set(false);
                    safeAll();
                }, 5, TimeUnit.SECONDS);
            }
        });
    }

    /** Sync one student now and wait for the result (used by the "Sync now" button). */
    public Map<String, Object> syncNow(Long uid) {
        Future<Map<String, Object>> f = pool.submit(() -> sync(uid));
        try {
            return f.get(120, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return Map.of("started", true);
        } catch (ExecutionException e) {
            Throwable c = e.getCause() == null ? e : e.getCause();
            log.warn("Sync failed for user {}: {}", uid, c.toString());
            if (c instanceof GoogleApiException ge && ge.isInvalidGrant()) {
                throw new BadRequestException("Google access was removed. Please connect again.");
            }
            throw new BadRequestException("Could not sync right now. Please try again in a minute.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sync interrupted");
        }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    private void safeAll() {
        try {
            List<Long> ids = jdbc.queryForList("SELECT user_id FROM student_google WHERE status = 'ACTIVE'", Long.class);
            for (Long id : ids) safeRun(id);
        } catch (Exception e) {
            log.warn("Sync-all failed: {}", e.toString());
        }
    }

    private void safeRun(Long uid) {
        try {
            sync(uid);
        } catch (Exception e) {
            log.warn("Sync failed for user {}: {}", uid, e.toString());
        }
    }

    // ---------- the sync ----------

    private Map<String, Object> sync(Long uid) {
        List<Map<String, Object>> g = jdbc.queryForList(
                "SELECT refresh_token_enc, calendar_id FROM student_google WHERE user_id = ? AND status = 'ACTIVE'", uid);
        Map<String, Object> result = new LinkedHashMap<>();
        if (g.isEmpty()) {
            result.put("skipped", true);
            return result;
        }

        String refresh = crypto.decrypt((String) g.get(0).get("refresh_token_enc"));
        String calId = (String) g.get(0).get("calendar_id");

        String access;
        try {
            access = google.accessTokenFromRefresh(refresh);
        } catch (GoogleApiException e) {
            if (e.isInvalidGrant()) {
                jdbc.update("UPDATE student_google SET status = 'REVOKED', last_error = ? WHERE user_id = ?",
                        "Google access was removed. Please connect again.", uid);
            } else {
                jdbc.update("UPDATE student_google SET last_error = ? WHERE user_id = ?",
                        "Could not reach Google. Will retry.", uid);
            }
            throw e;
        }

        // The student may have deleted the calendar in Google: make a new one and start fresh.
        if (calId == null || !google.calendarExists(access, calId)) {
            calId = google.createCalendar(access, CALENDAR_NAME);
            jdbc.update("UPDATE student_google SET calendar_id = ? WHERE user_id = ?", calId, uid);
            jdbc.update("DELETE FROM student_gcal_item WHERE user_id = ?", uid);
        }

        Map<String, Object> plan = plans.buildPlan(uid);
        Map<String, Desired> desired = desired(uid, plan);

        Map<String, Existing> existing = new LinkedHashMap<>();
        jdbc.query("SELECT item_key, google_event_id, fingerprint, CAST(item_date AS text) FROM student_gcal_item WHERE user_id = ?",
                rs -> {
                    existing.put(rs.getString(1), new Existing(rs.getString(2), rs.getString(3), rs.getString(4)));
                }, uid);

        int added = 0, updated = 0, removed = 0, failed = 0;
        String today = LocalDate.now().toString();

        for (Desired d : desired.values()) {
            Existing e = existing.get(d.key());
            if (e != null && e.fingerprint().equals(d.fingerprint())) continue;   // nothing changed
            try {
                google.upsertEvent(access, calId, d.googleId(), d.body(), e != null);
                saveRow(uid, d);
                if (e == null) added++; else updated++;
            } catch (GoogleApiException ex) {
                failed++;
                log.warn("Item {} failed for user {}: {}", d.key(), uid, ex.getMessage());
            }
        }

        for (Map.Entry<String, Existing> en : existing.entrySet()) {
            if (desired.containsKey(en.getKey())) continue;
            Existing e = en.getValue();
            // Past events stay in the calendar as a record; only current or future ones are removed.
            if (e.date() != null && e.date().compareTo(today) < 0) continue;
            try {
                google.deleteEvent(access, calId, e.googleId());
                jdbc.update("DELETE FROM student_gcal_item WHERE user_id = ? AND item_key = ?", uid, en.getKey());
                removed++;
            } catch (GoogleApiException ex) {
                failed++;
                log.warn("Delete {} failed for user {}: {}", en.getKey(), uid, ex.getMessage());
            }
        }

        jdbc.update("UPDATE student_google SET last_sync_at = now(), last_error = ? WHERE user_id = ?",
                failed == 0 ? null : failed + " item(s) could not be synced. Will retry.", uid);

        result.put("added", added);
        result.put("updated", updated);
        result.put("removed", removed);
        result.put("failed", failed);
        result.put("total", desired.size());
        log.info("Synced user {}: +{} ~{} -{} failed {}", uid, added, updated, removed, failed);
        return result;
    }

    private void saveRow(Long uid, Desired d) {
        jdbc.update("""
                INSERT INTO student_gcal_item (user_id, item_key, google_event_id, fingerprint, item_date)
                VALUES (?, ?, ?, ?, CAST(? AS date))
                ON CONFLICT (user_id, item_key) DO UPDATE SET
                    google_event_id = EXCLUDED.google_event_id,
                    fingerprint = EXCLUDED.fingerprint,
                    item_date = EXCLUDED.item_date
                """, uid, d.key(), d.googleId(), d.fingerprint(), d.date());
    }

    // ---------- plan -> Google events ----------

    @SuppressWarnings("unchecked")
    private Map<String, Desired> desired(Long uid, Map<String, Object> plan) {
        Map<String, Desired> out = new LinkedHashMap<>();
        List<String> skip = (List<String>) plan.getOrDefault("skipDates", List.of());

        for (Map<String, Object> c : (List<Map<String, Object>>) plan.getOrDefault("classes", List.of())) {
            String key = (String) c.get("key");
            Map<String, Object> body = classBody(c, skip);
            out.put(key, new Desired(key, eventId(uid, key), body, fingerprint(body), null));
        }
        for (Map<String, Object> e : (List<Map<String, Object>>) plan.getOrDefault("events", List.of())) {
            String key = (String) e.get("key");
            Map<String, Object> body = eventBody(e);
            out.put(key, new Desired(key, eventId(uid, key), body, fingerprint(body), (String) e.get("date")));
        }
        return out;
    }

    private Map<String, Object> classBody(Map<String, Object> c, List<String> skip) {
        LocalDate first = LocalDate.parse((String) c.get("firstDate"));
        LocalDate until = LocalDate.parse((String) c.get("until"));
        int weekday = ((Number) c.get("weekday")).intValue();
        String start = (String) c.get("start");
        String end = (String) c.get("end");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("summary", c.get("title"));
        String room = String.valueOf(c.get("room"));
        if (!room.isEmpty()) b.put("location", room);
        b.put("description", "Course " + c.get("code") + ". Added by the IIT Ropar Academic Calendar.");
        b.put("start", dateTime(first.toString(), start));
        b.put("end", dateTime(first.toString(), end));

        List<String> rec = new ArrayList<>();
        // 23:59 IST on the last day = 18:29:59 UTC
        rec.add("RRULE:FREQ=WEEKLY;UNTIL=" + until.format(DateTimeFormatter.BASIC_ISO_DATE) + "T182959Z");
        List<String> ex = new ArrayList<>();
        for (String s : skip) {
            LocalDate d = LocalDate.parse(s);
            if (d.getDayOfWeek().getValue() == weekday && !d.isBefore(first) && !d.isAfter(until)) {
                ex.add(d.format(DateTimeFormatter.BASIC_ISO_DATE) + "T" + start.replace(":", "") + "00");
            }
        }
        if (!ex.isEmpty()) rec.add("EXDATE;TZID=" + TZ + ":" + String.join(",", ex));
        b.put("recurrence", rec);
        b.put("reminders", reminders(10));
        return b;
    }

    private Map<String, Object> eventBody(Map<String, Object> e) {
        String date = (String) e.get("date");
        String start = (String) e.get("start");
        String end = (String) e.get("end");
        String category = String.valueOf(e.get("category"));

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("summary", e.get("title"));
        String venue = String.valueOf(e.get("venue"));
        if (!venue.isEmpty() && !"null".equals(venue)) b.put("location", venue);
        b.put("description", category + ". Added by the IIT Ropar Academic Calendar.");

        if (start == null) {                                   // all-day item
            b.put("start", Map.of("date", date));
            b.put("end", Map.of("date", LocalDate.parse(date).plusDays(1).toString()));
        } else {
            if (end == null) {
                LocalTime s = LocalTime.parse(start);
                LocalTime e1 = s.plusHours(1);
                end = e1.isBefore(s) ? "23:59" : e1.format(DateTimeFormatter.ofPattern("HH:mm"));
            }
            b.put("start", dateTime(date, start));
            b.put("end", dateTime(date, end));
        }
        b.put("reminders", "EXAM".equals(category) ? reminders(1440, 60) : reminders(60));
        return b;
    }

    private static Map<String, Object> dateTime(String date, String hhmm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dateTime", date + "T" + hhmm + ":00");
        m.put("timeZone", TZ);
        return m;
    }

    private static Map<String, Object> reminders(int... minutes) {
        List<Object> list = new ArrayList<>();
        for (int m : minutes) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("method", "popup");
            o.put("minutes", m);
            list.add(o);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("useDefault", false);
        r.put("overrides", list);
        return r;
    }

    // Google event ids: only a-v and 0-9, 5 to 1024 characters. Same input always gives the same id.
    static String eventId(Long uid, String key) {
        return "uc" + sha256(uid + "|" + key).substring(0, 40);
    }

    static String fingerprint(Map<String, Object> body) {
        return sha256(body.toString());
    }

    private static String sha256(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : h) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
