package com.iitrpr.calendar.student;

import jakarta.validation.constraints.NotBlank;

public class GoogleConnectRequest {
    @NotBlank(message = "OAuth authorization code cannot be blank.")
    private String authCode;

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
}
