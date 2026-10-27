package com.iitrpr.calendar.auth;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank(message = "Google ID token cannot be blank.")
    private String idToken;

    @NotBlank(message = "Calling site origin is required to verify dashboard permissions.")
    private String callingSite; // Accepts "http://localhost:5173", "http://localhost:5175", or "http://localhost:5176"

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public String getCallingSite() { return callingSite; }
    public void setCallingSite(String callingSite) { this.callingSite = callingSite; }
}
