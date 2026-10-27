package com.iitrpr.calendar.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class HolidayId implements Serializable {
    private Long termId;
    private LocalDate day;

    public HolidayId() {}
    public HolidayId(Long termId, LocalDate day) {
        this.termId = termId;
        this.day = day;
    }

    // Getters, Setters, Equals, HashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HolidayId holidayId = (HolidayId) o;
        return Objects.equals(termId, holidayId.termId) && Objects.equals(day, holidayId.day);
    }

    @Override
    public int hashCode() { return Objects.hash(termId, day); }
}
