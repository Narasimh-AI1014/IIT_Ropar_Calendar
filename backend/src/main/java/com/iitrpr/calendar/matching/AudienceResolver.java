package com.iitrpr.calendar.matching;

import com.iitrpr.calendar.domain.EventAudience;
import com.iitrpr.calendar.errors.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AudienceResolver {

    /**
     * Expands bulk criteria criteria parameters into distinct flat rows for database targeting.
     * @param eventId The primary tracking key of the master event.
     * @param criteria The incoming filtering rules matrix.
     * @return List of EventAudience rows prepared for repository batch saving.
     */
    public List<EventAudience> resolveAudienceRows(Long eventId, AudienceCriteria criteria) {
        List<EventAudience> audienceRows = new ArrayList<>();
        String targetType = criteria.getType() != null ? criteria.getType().toUpperCase() : "";

        switch (targetType) {
            case "ALL" -> {
                EventAudience row = new EventAudience();
                row.setEventId(eventId);
                row.setType("ALL");
                audienceRows.add(row);
            }
            case "COURSES" -> {
                if (criteria.getCourseCodes() == null || criteria.getCourseCodes().isEmpty()) {
                    throw new BadRequestException("COURSES type target requires at least one explicit course_code.");
                }
                for (String code : criteria.getCourseCodes()) {
                    EventAudience row = new EventAudience();
                    row.setEventId(eventId);
                    row.setType("COURSES");
                    row.setCourseCode(code.toUpperCase().trim()); // ✅ Fixed syntax parenthesis and naming binding check
                    audienceRows.add(row);
                }
            }
            case "GROUP" -> {
                List<String> depts = criteria.getDeptCodes();
                List<Integer> years = criteria.getEntryYears();

                boolean hasDepts = (depts != null && !depts.isEmpty());
                boolean hasYears = (years != null && !years.isEmpty());

                if (!hasDepts && !hasYears) {
                    throw new BadRequestException("GROUP type target requires at least one department or entry_year boundary.");
                }

                // Execute a Cross-Product Matrix Expansion to flat-map multi-parameter rows
                if (hasDepts && hasYears) {
                    for (String dept : depts) {
                        for (Integer year : years) {
                            EventAudience row = new EventAudience();
                            row.setEventId(eventId);
                            row.setType("GROUP");
                            row.setDeptCode(dept.toUpperCase().trim());
                            row.setEntryYear(year);
                            audienceRows.add(row);
                        }
                    }
                } else if (hasDepts) {
                    for (String dept : depts) {
                        EventAudience row = new EventAudience();
                        row.setEventId(eventId);
                        row.setType("GROUP");
                        row.setDeptCode(dept.toUpperCase().trim());
                        audienceRows.add(row);
                    }
                } else {
                    for (Integer year : years) {
                        EventAudience row = new EventAudience();
                        row.setEventId(eventId);
                        row.setType("GROUP");
                        row.setEntryYear(year);
                        audienceRows.add(row);
                    }
                }
            }
            default -> throw new BadRequestException("Unsupported audience matching type declaration token: " + targetType);
        }

        return audienceRows;
    }
}
