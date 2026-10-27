package com.iitrpr.calendar.imports;

import com.iitrpr.calendar.errors.BadRequestException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateTimeParserUtil {

    // Regex supporting multiple Indian/European day-first separators (e.g., 18/10/2026, 18-10-2026)
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd") // ISO backup anchor
    };

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-–—]\\s*(\\d{1,2}:\\d{2})");

    /**
     * Parses flexible day-first date format strings into valid LocalDate objects.
     */
    public static LocalDate parseDayFirstDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return null;
        String cleanDate = rawDate.trim();
        
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleanDate, formatter);
            } catch (DateTimeParseException ignored) {}
        }
        throw new BadRequestException("Invalid date value: '" + rawDate + "'. Supported patterns: DD/MM/YYYY, DD-MM-YYYY.");
    }

    /**
     * Extracts distinct Start and End LocalTime objects from a single time range block string.
     * @param rawRange String like "09:00 - 09:55" or "14:00-15:30"
     * @return LocalTime array where index 0 = start_time and index 1 = end_time
     */
    public static LocalTime[] parseTimeRange(String rawRange) {
        if (rawRange == null || rawRange.isBlank()) return null;
        Matcher matcher = RANGE_PATTERN.matcher(rawRange.trim());
        
        if (!matcher.matches()) {
            throw new BadRequestException("Invalid time range sequence block format: '" + rawRange + "'. Use 'HH:mm - HH:mm'.");
        }

        try {
            LocalTime start = LocalTime.parse(matcher.group(1), TIME_FORMATTER);
            LocalTime end = LocalTime.parse(matcher.group(2), TIME_FORMATTER);

            if (!end.isAfter(start)) {
                throw new BadRequestException("Time range logic boundary anomaly: End time (" + end + ") must sit after start time (" + start + ").");
            }
            return new LocalTime[]{start, end};
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Failed to process time range syntax: " + e.getMessage());
        }
    }
}
