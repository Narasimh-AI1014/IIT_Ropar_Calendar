package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SlotTimeRepository extends JpaRepository<SlotTime, SlotTimeId> {
    List<SlotTime> findByWeekday(Short weekday);
}
