package com.iitrpr.calendar.admin;

public class AdminEventResponse {
    private Long id;
    private int reached;

    public AdminEventResponse() {}

    public AdminEventResponse(Long id, int reached) {
        this.id = id;
        this.reached = reached;
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getReached() { return reached; }
    public void setReached(int reached) { this.reached = reached; }
}
