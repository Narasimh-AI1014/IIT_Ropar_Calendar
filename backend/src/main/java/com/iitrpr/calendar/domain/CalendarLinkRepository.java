package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CalendarLinkRepository extends JpaRepository<CalendarLink, Long> {
    
    /**
     * Fetches all external calendar links stored for a specific student tracking ID.
     */
    List<CalendarLink> findByUserId(Long userId);
}
