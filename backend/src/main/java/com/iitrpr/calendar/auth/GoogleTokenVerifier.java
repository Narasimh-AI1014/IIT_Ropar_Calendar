package com.iitrpr.calendar.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.iitrpr.calendar.errors.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;
    private final String allowedDomain;

    // The constructor sets up the cryptographic verifier using your properties variables
    public GoogleTokenVerifier(
            @Value("${app.google.client-id}") String clientId,
            @Value("${app.google.allowed-domain:iitrpr.ac.in}") String allowedDomain) {
        
        this.allowedDomain = allowedDomain;
        
        // Build the official secure verifier engine from Google Client Libraries
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    /**
     * Verifies the cryptographic signature, payload structure, and institutional domain constraints.
     * @param idTokenString Raw token received from the calling frontend client application port.
     * @return GoogleIdToken.Payload containing verified identity claims.
     */
    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            // 1. Verify cryptographic signature, audience match bounds, and token expiration windows
            GoogleIdToken idToken = verifier.verify(idTokenString);
            
            if (idToken == null) {
                throw new BadRequestException("Invalid Google ID token signature or token has expired.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // 2. Validate that the email address is actively marked as verified by Google accounts infrastructure
            if (!payload.getEmailVerified()) {
                throw new BadRequestException("The provided Google account email address is not verified.");
            }

            // 3. Validate the Institutional Domain using the 'hd' (Hosted Domain) claim layer
            String hostedDomain = payload.getHostedDomain(); // Extracts 'hd' claim parameter values
            String email = payload.getEmail();

            if (hostedDomain == null || !hostedDomain.equalsIgnoreCase(allowedDomain)) {
                // Secondary check: ensure raw email values match target domain bounds if 'hd' is skipped under edge-cases
                if (!email.endsWith("@" + allowedDomain)) {
                    throw new BadRequestException("Access denied. Only corporate accounts belonging to the domain '" 
                            + allowedDomain + "' are authorized to use this application.");
                }
            }

            return payload; // Returns the verified token claims context payload successfully!
            
        } catch (GeneralSecurityException | IOException e) {
            throw new BadRequestException("Cryptographic execution processing anomaly failed: " + e.getMessage());
        }
    }
}
