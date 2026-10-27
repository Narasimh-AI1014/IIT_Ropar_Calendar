package com.iitrpr.calendar.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads secrets that must never be committed to Git.
 * Order: environment variable, then application property, then the local file secrets.yml.
 */
@Component
public class AppSecrets {

    private static final Logger log = LoggerFactory.getLogger(AppSecrets.class);

    private final String googleClientSecret;
    private final String tokenKeyMaterial;

    public AppSecrets(@Value("${app.google.client-secret:}") String propSecret,
                      @Value("${app.crypto.key:}") String propKey) {
        Map<String, String> file = readSecretsFile();

        this.googleClientSecret = first(System.getenv("GOOGLE_CLIENT_SECRET"), propSecret, file.get("client-secret"));

        String key = first(System.getenv("TOKEN_ENC_KEY"), propKey, file.get("crypto-key"));
        if (key.isEmpty() && !googleClientSecret.isEmpty()) {
            log.warn("No encryption key set (crypto-key / TOKEN_ENC_KEY). Using a key derived from the Google client secret. "
                    + "Set a separate key before real students use this.");
            key = "derived:" + googleClientSecret;
        }
        this.tokenKeyMaterial = key;

        if (googleClientSecret.isEmpty()) {
            log.warn("Google client secret is NOT configured. Connecting Google Calendar will not work. "
                    + "Create backend/secrets.yml or set GOOGLE_CLIENT_SECRET.");
        } else {
            log.info("Google client secret loaded.");
        }
    }

    public String googleClientSecret() { return googleClientSecret; }

    public String tokenKeyMaterial() { return tokenKeyMaterial; }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static Map<String, String> readSecretsFile() {
        Map<String, String> out = new HashMap<>();
        Pattern line = Pattern.compile("^\\s*([A-Za-z\\-]+):\\s*\"?([^\"\\s#]+)\"?\\s*(#.*)?$");
        for (String candidate : new String[]{"secrets.yml", "backend/secrets.yml", "../backend/secrets.yml"}) {
            Path p = Path.of(candidate);
            if (!Files.isRegularFile(p)) continue;
            try {
                for (String l : Files.readAllLines(p)) {
                    Matcher m = line.matcher(l);
                    if (m.matches()) out.putIfAbsent(m.group(1), m.group(2));
                }
                log.info("Read secrets from {}", p.toAbsolutePath());
                break;
            } catch (Exception e) {
                log.warn("Could not read {}: {}", p, e.getMessage());
            }
        }
        return out;
    }
}
