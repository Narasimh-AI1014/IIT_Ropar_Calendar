package com.iitrpr.calendar.student;

import com.iitrpr.calendar.domain.AppUser;
import com.iitrpr.calendar.domain.AppUserRepository;
import com.iitrpr.calendar.errors.BadRequestException;
import com.iitrpr.calendar.integration.GoogleOAuthClient;
import com.iitrpr.calendar.integration.GoogleOAuthClient.GoogleApiException;
import com.iitrpr.calendar.integration.GoogleOAuthClient.Tokens;
import com.iitrpr.calendar.security.TokenCrypto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Connects a student's Google Calendar (one time) so the server can add items for them.
 *  - GET    /api/student/me/google/status
 *  - POST   /api/student/me/google/connect   {code}
 *  - POST   /api/student/me/google/sync      (sync now)
 *  - DELETE /api/student/me/google           (disconnect and remove the calendar)
 */
@RestController
@RequestMapping("/api/student/me/google")
public class StudentGoogleController {

    private static final Logger log = LoggerFactory.getLogger(StudentGoogleController.class);
    private static final String CALENDAR_NAME = "University Timetable";

    @PersistenceContext
    private EntityManager em;

    private final AppUserRepository users;
    private final GoogleOAuthClient google;
    private final TokenCrypto crypto;
    private final StudentSyncService sync;

    public StudentGoogleController(AppUserRepository users, GoogleOAuthClient google, TokenCrypto crypto,
                                   StudentSyncService sync) {
        this.users = users;
        this.google = google;
        this.crypto = crypto;
        this.sync = sync;
    }

    private AppUser me(Authentication auth) {
        return users.findByEmail(auth.getName().toLowerCase())
                .orElseThrow(() -> new IllegalStateException("User not found: " + auth.getName()));
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/status")
    public Map<String, Object> status(Authentication auth) {
        Long uid = me(auth).getId();
        List<Object[]> rows = em.createNativeQuery(
                "SELECT status, connected_at, last_sync_at, last_error FROM student_google WHERE user_id = :u")
                .setParameter("u", uid).getResultList();
        Map<String, Object> m = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            m.put("connected", false);
            return m;
        }
        Object[] r = rows.get(0);
        m.put("connected", "ACTIVE".equals(r[0]));
        m.put("status", r[0]);
        m.put("connectedAt", r[1] == null ? null : r[1].toString());
        m.put("lastSyncAt", r[2] == null ? null : r[2].toString());
        m.put("lastError", r[3]);
        m.put("calendarName", CALENDAR_NAME);
        return m;
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/connect")
    @Transactional
    public Map<String, Object> connect(Authentication auth, @RequestBody Map<String, Object> body) {
        AppUser user = me(auth);
        Long uid = user.getId();
        String code = body == null || body.get("code") == null ? "" : String.valueOf(body.get("code")).trim();
        if (code.isEmpty()) throw new BadRequestException("Missing Google authorization code.");

        Tokens t;
        try {
            t = google.exchangeCode(code);
        } catch (GoogleApiException e) {
            throw new BadRequestException("Google did not accept the sign-in. Please try again.");
        }

        // The Google account must be the same college account the student logged in with.
        if (t.email() == null || !t.email().equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Please connect with your college Google account (" + user.getEmail() + ").");
        }

        // Existing connection (if any)
        List<Object[]> old = em.createNativeQuery(
                "SELECT refresh_token_enc, calendar_id FROM student_google WHERE user_id = :u")
                .setParameter("u", uid).getResultList();

        String refresh = t.refreshToken();
        String calendarId = null;
        if (!old.isEmpty()) calendarId = (String) old.get(0)[1];

        if (refresh == null || refresh.isBlank()) {
            if (old.isEmpty()) {
                throw new BadRequestException("Google did not give lasting access. Open myaccount.google.com/permissions, "
                        + "remove this app, and connect again.");
            }
            refresh = crypto.decrypt((String) old.get(0)[0]);   // keep the existing lasting access
        }

        try {
            if (calendarId == null || !google.calendarExists(t.accessToken(), calendarId)) {
                calendarId = google.createCalendar(t.accessToken(), CALENDAR_NAME);
            }
        } catch (GoogleApiException e) {
            log.warn("Calendar setup failed for {}: {}", user.getEmail(), e.getMessage());
            throw new BadRequestException("Could not create the calendar. Make sure you allowed Google Calendar access.");
        }

        em.createNativeQuery("""
                INSERT INTO student_google (user_id, refresh_token_enc, calendar_id, status, connected_at, last_error)
                VALUES (:u, :r, :c, 'ACTIVE', now(), NULL)
                ON CONFLICT (user_id) DO UPDATE SET
                    refresh_token_enc = EXCLUDED.refresh_token_enc,
                    calendar_id = EXCLUDED.calendar_id,
                    status = 'ACTIVE', connected_at = now(), last_error = NULL
                """)
                .setParameter("u", uid)
                .setParameter("r", crypto.encrypt(refresh))
                .setParameter("c", calendarId)
                .executeUpdate();

        sync.scheduleStudent(uid);      // fill the calendar right after connecting

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connected", true);
        out.put("calendarName", CALENDAR_NAME);
        return out;
    }

    /** "Sync now" button: bring the calendar up to date and report what changed. */
    @PostMapping("/sync")
    public Map<String, Object> syncNow(Authentication auth) {
        return sync.syncNow(me(auth).getId());
    }

    @SuppressWarnings("unchecked")
    @DeleteMapping
    @Transactional
    public Map<String, Object> disconnect(Authentication auth) {
        Long uid = me(auth).getId();
        List<Object[]> rows = em.createNativeQuery(
                "SELECT refresh_token_enc, calendar_id FROM student_google WHERE user_id = :u")
                .setParameter("u", uid).getResultList();

        if (!rows.isEmpty()) {
            try {
                String refresh = crypto.decrypt((String) rows.get(0)[0]);
                String calId = (String) rows.get(0)[1];
                if (calId != null) {
                    String access = google.accessTokenFromRefresh(refresh);
                    google.deleteCalendar(access, calId);    // remove everything we added
                }
                google.revoke(refresh);
            } catch (Exception e) {
                log.warn("Disconnect cleanup incomplete (ignored): {}", e.getMessage());
            }
        }
        em.createNativeQuery("DELETE FROM student_gcal_item WHERE user_id = :u").setParameter("u", uid).executeUpdate();
        em.createNativeQuery("DELETE FROM student_google WHERE user_id = :u").setParameter("u", uid).executeUpdate();
        return Map.of("connected", false);
    }
}
