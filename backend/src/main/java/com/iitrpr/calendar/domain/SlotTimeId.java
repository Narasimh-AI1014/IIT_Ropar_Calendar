package com.iitrpr.calendar.domain;

import java.io.Serializable;
import java.util.Objects;

public class SlotTimeId implements Serializable {
    private String slotCode;
    private Short weekday;

    public SlotTimeId() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotTimeId that = (SlotTimeId) o;
        return Objects.equals(slotCode, that.slotCode) && Objects.equals(weekday, that.weekday);
    }

    @Override
    public int hashCode() { return Objects.hash(slotCode, weekday); }
}
