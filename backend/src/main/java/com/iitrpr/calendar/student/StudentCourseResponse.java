package com.iitrpr.calendar.student;

public class StudentCourseResponse {
    private String code;         // e.g., "CS513"
    private String title;        // e.g., "Advanced Data Structures"
    private String instructor;   // Display names
    private String group;        // Student group allocation (e.g., "G1")
    private String status;       // Enrollment tracking status (e.g., "ENRO")

    public StudentCourseResponse() {}

    public StudentCourseResponse(String code, String title, String instructor, String group, String status) {
        this.code = code;
        this.title = title;
        this.instructor = instructor;
        this.group = group;
        this.status = status;
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = instructor; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
