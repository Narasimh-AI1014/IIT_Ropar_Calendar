package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CourseSlotRepository extends JpaRepository<CourseSlot, Long> {
    
    /**
     * Finds course slot records matching an explicit slot code identifier string.
     * Used by the validation engine to identify which course owns a colliding slot time.
     * 
     * @param slotCode The string representation of the slot (e.g., 'PC-1', 'G7')
     * @return List of matching CourseSlot mappings present in the database.
     */
    List<CourseSlot> findBySlotCode(String slotCode);
}
