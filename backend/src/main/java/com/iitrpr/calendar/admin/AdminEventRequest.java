package com.iitrpr.calendar.admin;

import com.iitrpr.calendar.matching.AudienceCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public class AdminEventRequest {
    @NotBlank(message = "Event title cannot be blank.")
    private String title;

    @NotBlank(message = "Category field is required (event, quiz, lecture_alteration, exam).")
    private String category;

    @NotNull(message = "Event execution date is required.")
    private LocalDate eventDate;

    private LocalTime startTime;
    private LocalTime endTime;
    private String venue;
    private String description;
    
    @NotNull(message = "Target creator user identity is required.")
    private Long createdBy;

    @Valid
    @NotNull(message = "Audience criteria context mapping targets are required.")
    private AudienceCriteria audience;

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
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
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public AudienceCriteria getAudience() { return audience; }
    public void setAudience(AudienceCriteria audience) { this.audience = audience; }
}
