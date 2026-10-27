package com.iitrpr.calendar.faculty;

import com.iitrpr.calendar.matching.AudienceCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public class FacultyEventRequest {
    @NotBlank(message = "Title is required.")
    private String title;

    @NotBlank(message = "Category must be quiz or lecture_alteration.")
    private String category; // Checked against V2 lower-case constraint tokens

    @NotNull(message = "Date is required.")
    private LocalDate eventDate;

    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
    private String description;

    @Valid
    @NotNull(message = "Target audience mapping is required.")
    private AudienceCriteria audience;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AudienceCriteria getAudience() { return audience; }
    public void setAudience(AudienceCriteria audience) { this.audience = audience; }
}
