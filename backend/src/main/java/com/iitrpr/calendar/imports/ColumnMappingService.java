package com.iitrpr.calendar.imports;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ColumnMappingService {

    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("^[A-Za-z]{2,4}\\s*\\d{3}[A-Za-z]?$"); // e.g. CS513, HS513
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(".*\\d{1,2}:\\d{2}\\s*[-–—]\\s*\\d{1,2}:\\d{2}.*");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    /**
     * Resolves layout columns using dynamic explicit dictionary names and fallback content heuristics.
     * @param rawHeaders Flat list of strings representing the detected header row cell tokens.
     * @param sampleRows Sample rows extracted immediately below the header block to analyze content.
     * @param userMapping Map dictionary containing saved preferences (e.g. {"Room Allocated": "venue"})
     * @return Map linking canonical entity variables to actual sheet column keys.
     */
    public Map<String, String> resolveColumnSchemaMap(
            List<String> rawHeaders, 
            List<Map<String, String>> sampleRows, 
            Map<String, String> userMapping) {
        
        Map<String, String> dynamicSchemaMap = new HashMap<>();

        for (String rawHeader : rawHeaders) {
            String cleanHeader = rawHeader.trim().toLowerCase();

            // Strategy 1: Explicit mapping by name dictionary match checks
            if (userMapping != null && userMapping.containsKey(rawHeader)) {
                dynamicSchemaMap.put(userMapping.get(rawHeader), cleanHeader);
                continue;
            }

            // Strategy 2: Inferred implicit direct name mapping loops
            if (cleanHeader.contains("date") || cleanHeader.contains("day")) {
                dynamicSchemaMap.put("event_date", cleanHeader);
            } else if (cleanHeader.contains("code") || cleanHeader.contains("subject") || cleanHeader.contains("course")) {
                dynamicSchemaMap.put("course_code", cleanHeader);
            } else if (cleanHeader.contains("time") || cleanHeader.contains("slot") || cleanHeader.contains("hour")) {
                dynamicSchemaMap.put("time_range", cleanHeader);
            } else if (cleanHeader.contains("room") || cleanHeader.contains("venue") || cleanHeader.contains("location")) {
                dynamicSchemaMap.put("venue", cleanHeader);
            } else if (cleanHeader.contains("mail") || cleanHeader.contains("user")) {
                dynamicSchemaMap.put("email", cleanHeader);
            } 
            // Strategy 3: Fallback content heuristic mapping checks (Deep Inspection)
            else if (!sampleRows.isEmpty()) {
                evaluateColumnByContentContentHeuristics(cleanHeader, sampleRows, dynamicSchemaMap);
            }
        }
        return dynamicSchemaMap;
    }

    private void evaluateColumnByContentContentHeuristics(
            String cleanHeader, 
            List<Map<String, String>> sampleRows, 
            Map<String, String> resolvedMap) {
        
        int courseHits = 0, timeHits = 0, emailHits = 0;
        int checkLimit = Math.min(sampleRows.size(), 5); // Evaluate up to 5 validation anchor positions

        for (int i = 0; i < checkLimit; i++) {
            String cellContent = sampleRows.get(i).get(cleanHeader);
            if (cellContent == null || cellContent.isBlank()) continue;
            
            String val = cellContent.trim();
            if (COURSE_CODE_PATTERN.matcher(val).matches()) courseHits++;
            if (TIME_RANGE_PATTERN.matcher(val).matches()) timeHits++;
            if (EMAIL_PATTERN.matcher(val).matches()) emailHits++;
        }

        // Apply strict evaluation boundary flags if structural heuristic patterns clear threshold limits
        if (courseHits >= (checkLimit / 2) && !resolvedMap.containsKey("course_code")) {
            resolvedMap.put("course_code", cleanHeader);
        } else if (timeHits >= (checkLimit / 2) && !resolvedMap.containsKey("time_range")) {
            resolvedMap.put("time_range", cleanHeader);
        } else if (emailHits >= (checkLimit / 2) && !resolvedMap.containsKey("email")) {
            resolvedMap.put("email", cleanHeader);
        }
    }
}
