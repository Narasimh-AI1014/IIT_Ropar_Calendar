package com.iitrpr.calendar.student;

public class StudentScheduleEvent {
    private String type;         // "CLASS", "EXAM", "EVENT"
    private String courseCode;   // e.g., "CS513"
    private String title;        // e.g., "Advanced Data Structures" or "Surprise Quiz 1"
    private String date;         // Formatted as "YYYY-MM-DD"
    private String startTime;    // Formatted strictly as "HH:mm"
    private String endTime;      // Formatted strictly as "HH:mm"
    private String venue;        // Room / Hall allocation
    private String description;

    public StudentScheduleEvent() {}

    public StudentScheduleEvent(String type, String courseCode, String title, String date, 
                                String startTime, String endTime, String venue, String description) {
        this.type = type;
        this.courseCode = courseCode;
        this.title = title;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venue = venue;
        this.description = description;
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
