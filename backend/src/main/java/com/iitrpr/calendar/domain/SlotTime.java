package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "slot_time")
@IdClass(SlotTimeId.class)
public class SlotTime {
    @Id
    @Column(name = "slot_code")
    private String slotCode;

    @Id
    @Column(name = "weekday")
    private Short weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public SlotTime() {}

    // ==========================================
    // GETTERS & SETTERS (Strict Alignment)
    // ==========================================
    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }

    public Short getWeekday() { return weekday; }
    public void setWeekday(Short weekday) { this.weekday = weekday; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
}
