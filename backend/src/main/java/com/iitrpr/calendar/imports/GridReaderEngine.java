package com.iitrpr.calendar.imports;

import com.iitrpr.calendar.errors.BadRequestException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GridReaderEngine {

    /**
     * Un-pivots a 2D matrix layout timetable sheet grid into individual flat operational data rows.
     * @param flatExcelRows Raw parsed row list containing keyed positional strings.
     * @param slotColumnHeader The name of the primary column track holding time slots (e.g. "Time/Day")
     * @return List of normalized row models mapped for validation.
     */
    public List<ParsedRow> transformGridToFlatRows(List<ParsedRow> flatExcelRows, String slotColumnHeader) {
        if (flatExcelRows == null || flatExcelRows.isEmpty()) return List.of();
        
        List<ParsedRow> normalizedRows = new ArrayList<>();
        String canonicalSlotKey = slotColumnHeader.trim().toLowerCase();
        int uniqueLineCounter = 1;

        // Extract available column keys from the initial record to detect weekday layout axes
        Map<String, String> baselineFields = flatExcelRows.get(0).getData();
        List<String> dayHeaders = new ArrayList<>();
        
        for (String key : baselineFields.keySet()) {
            if (!key.equalsIgnoreCase(canonicalSlotKey)) {
                dayHeaders.add(key); // Captures headers like "Monday", "Tuesday"
            }
        }

        if (!baselineFields.containsKey(canonicalSlotKey)) {
            throw new BadRequestException("Grid Parsing Anomaly: Target slot tracking column header '" + slotColumnHeader + "' not found inside the excel schema mapping.");
        }

        // Loop horizontally and vertically over cell coordinates to un-pivot the grid matrix
        for (ParsedRow excelRow : flatExcelRows) {
            Map<String, String> rowData = excelRow.getData();
            String rawTimeRangeValue = rowData.get(canonicalSlotKey);

            if (rawTimeRangeValue == null || rawTimeRangeValue.isBlank()) continue;

            for (String day : dayHeaders) {
                String courseContent = rowData.get(day);
                
                // If the intersecting cell contains text data (e.g. "CS513" or "HS513 - G1"), extract it
                if (courseContent != null && !courseContent.isBlank()) {
                    ParsedRow flatRow = new ParsedRow(uniqueLineCounter++);
                    
                    // Normalize cell coordinates into a standard flat database map entry package
                    flatRow.addCell("time_range", rawTimeRangeValue);
                    flatRow.addCell("weekday_name", day.trim());
                    
                    if (courseContent.contains("-")) {
                        String[] parts = courseContent.split("-");
                        flatRow.addCell("course_code", parts[0].trim());
                        flatRow.addCell("grp", parts[1].trim());
                    } else {
                        flatRow.addCell("course_code", courseContent.trim());
                        flatRow.addCell("grp", "G1"); // Standard fallback allocation group
                    }
                    
                    normalizedRows.add(flatRow);
                }
            }
        }

        return normalizedRows;
    }
}
