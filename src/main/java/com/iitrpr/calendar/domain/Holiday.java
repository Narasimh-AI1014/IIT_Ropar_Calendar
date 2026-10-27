package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "holiday")
@IdClass(HolidayId.class)
public class Holiday {
    @Id
    @Column(name = "term_id")
    private Long termId;

    @Id
    @Column(name = "day")
    private LocalDate day;

    @Column(name = "label")
    private String label;

    // Getters, Setters
    public Long getTermId() { return termId; }
    public void setTermId(Long termId) { this.termId = termId; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
