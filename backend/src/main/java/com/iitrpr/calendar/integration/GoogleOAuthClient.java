package com.iitrpr.calendar.integration;

import com.iitrpr.calendar.security.AppSecrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Talks to Google: token exchange, token refresh, and the calendar itself. */
@Component
public class GoogleOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CAL = "https://www.googleapis.com/calendar/v3";

    private final RestClient http = RestClient.create();
    private final String clientId;
    private final AppSecrets secrets;

    public record Tokens(String accessToken, String refreshToken, String email) {}

    public static class GoogleApiException extends RuntimeException {
        private final int status;
        private final String body;

        public GoogleApiException(int status, String body) {
            super("Google API error " + status + ": " + body);
            this.status = status;
            this.body = body == null ? "" : body;
        }

        public int status() { return status; }
        public boolean isInvalidGrant() { return body.contains("invalid_grant"); }
    }

    public GoogleOAuthClient(@Value("${app.google.client-id}") String clientId, AppSecrets secrets) {
        this.clientId = clientId;
        this.secrets = secrets;
    }

    // ---------- tokens ----------

    /** Swaps the one-time code from the browser for tokens. */
    public Tokens exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", secrets.googleClientSecret());
        form.add("redirect_uri", "postmessage");
        form.add("grant_type", "authorization_code");
        Map<String, Object> r = postForm(TOKEN_URL, form);
        String idToken = (String) r.get("id_token");
        return new Tokens((String) r.get("access_token"), (String) r.get("refresh_token"), emailFromIdToken(idToken));
    }

    /** Gets a fresh short-lived access token from the saved refresh token. */
    public String accessTokenFromRefresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", clientId);
        form.add("client_secret", secrets.googleClientSecret());
        form.add("grant_type", "refresh_token");
        Map<String, Object> r = postForm(TOKEN_URL, form);
        return (String) r.get("access_token");
    }

    public void revoke(String token) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", token);
            postForm("https://oauth2.googleapis.com/revoke", form);
        } catch (Exception e) {
            log.warn("Token revoke failed (ignored): {}", e.getMessage());
        }
    }

    // ---------- calendars ----------

    public String createCalendar(String accessToken, String name) {
        Map<String, Object> body = Map.of(
                "summary", name,
                "description", "Timetable and events from the IIT Ropar Academic Calendar",
                "timeZone", "Asia/Kolkata");
        Map<String, Object> r = call("POST", CAL + "/calendars", accessToken, body);
        return (String) r.get("id");
    }

    public boolean calendarExists(String accessToken, String calendarId) {
        try {
            call("GET", CAL + "/calendars/" + enc(calendarId), accessToken, null);
            return true;
        } catch (GoogleApiException e) {
            if (e.status() == 404 || e.status() == 410) return false;
            throw e;
        }
    }

    public void deleteCalendar(String accessToken, String calendarId) {
        try {
            call("DELETE", CAL + "/calendars/" + enc(calendarId), accessToken, null);
        } catch (GoogleApiException e) {
            if (e.status() != 404 && e.status() != 410) throw e;
        }
    }

    // ---------- events ----------

    /** Adds the event, or updates it if it already exists. The id is ours, so it never duplicates. */
    public void upsertEvent(String accessToken, String calendarId, String eventId,
                            Map<String, Object> body, boolean expectExists) {
        Map<String, Object> b = new java.util.LinkedHashMap<>(body);
        b.put("id", eventId);
        b.put("status", "confirmed");
        String base = CAL + "/calendars/" + enc(calendarId) + "/events";
        if (expectExists) {
            try {
                call("PUT", base + "/" + eventId, accessToken, b);
                return;
            } catch (GoogleApiException e) {
                if (e.status() != 404 && e.status() != 410) throw e;
            }
        }
        try {
            call("POST", base, accessToken, b);
        } catch (GoogleApiException e) {
            if (e.status() == 409) {
                call("PUT", base + "/" + eventId, accessToken, b);   // id was used before (e.g. deleted): restore it
            } else {
                throw e;
            }
        }
    }

    public void deleteEvent(String accessToken, String calendarId, String eventId) {
        try {
            call("DELETE", CAL + "/calendars/" + enc(calendarId) + "/events/" + eventId, accessToken, null);
        } catch (GoogleApiException e) {
            if (e.status() != 404 && e.status() != 410) throw e;
        }
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        try {
            Map<String, Object> r = http.post().uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return r == null ? Map.of() : r;
        } catch (RestClientResponseException e) {
            log.warn("Google {} -> {} {}", url, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new GoogleApiException(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String url, String accessToken, Object body) {
        for (int attempt = 1; ; attempt++) {
            try {
                RestClient.RequestBodySpec spec = http.method(org.springframework.http.HttpMethod.valueOf(method))
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + accessToken);
                if (body != null) spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
                Map<String, Object> r = spec.retrieve().body(Map.class);
                return r == null ? Map.of() : r;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                String text = e.getResponseBodyAsString();
                boolean busy = status == 429 || status >= 500
                        || (status == 403 && text != null && text.toLowerCase().contains("ratelimit"));
                if (busy && attempt < 4) {                      // Google asked us to slow down: wait and retry
                    try {
                        Thread.sleep(attempt * 1500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                log.warn("Google {} {} -> {} {}", method, url, status, text);
                throw new GoogleApiException(status, text);
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // The id_token came straight from Google over TLS in the exchange, so reading it is safe.
    private static String emailFromIdToken(String idToken) {
        if (idToken == null) return null;
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) return null;
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1).toLowerCase() : null;
    }
}
