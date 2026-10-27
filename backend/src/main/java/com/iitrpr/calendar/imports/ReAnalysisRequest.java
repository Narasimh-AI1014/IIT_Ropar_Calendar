package com.iitrpr.calendar.imports;

import jakarta.validation.constraints.NotBlank;

public class ReAnalysisRequest {
    @NotBlank(message = "Target operation mapping kind is required (classes, exam, enrolment).")
    private String kind;
    
    private String sheetName; // The newly selected target worksheet tab string

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
}
