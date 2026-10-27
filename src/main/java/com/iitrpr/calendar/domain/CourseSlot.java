package com.iitrpr.calendar.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "course_slot", uniqueConstraints = {@UniqueConstraint(columnNames = {"course_code", "grp", "slot_code"})})
public class CourseSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(name = "grp", nullable = false)
    private String grp = "";

    @Column(name = "slot_code", nullable = false)
    private String slotCode;

    @Column(name = "room")
    private String room;

    public CourseSlot() {}

    // ==========================================
    // GETTERS & SETTERS (Strict camelCase Naming)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCourseCode() { 
        return this.courseCode; 
    }
    public void setCourseCode(String courseCode) { 
        this.courseCode = courseCode; 
    }

    public String getGrp() { return grp; }
    public void setGrp(String grp) { this.grp = grp; }

    public String getSlotCode() { return slotCode; }
    public void setSlotCode(String slotCode) { this.slotCode = slotCode; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
}
