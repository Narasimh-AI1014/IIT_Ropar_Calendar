package com.iitrpr.calendar.imports;

import java.util.HashMap;
import java.util.Map;

public class ParsedRow {
    private int lineNumber;
    private Map<String, String> data = new HashMap<>();

    public ParsedRow(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public void addCell(String header, String value) {
        if (header != null) {
            this.data.put(header.trim().toLowerCase(), value != null ? value.trim() : "");
        }
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }
}
