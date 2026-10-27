package com.iitrpr.calendar.imports;

import java.util.List;
import java.util.Map;

public class ImportAnalysisResponse {
    private Long importId;
    private String fileName;
    private List<String> sheets;
    private Map<String, Object> analysis;

    public ImportAnalysisResponse() {}

    public ImportAnalysisResponse(Long importId, String fileName, List<String> sheets, Map<String, Object> analysis) {
        this.importId = importId;
        this.fileName = fileName;
        this.sheets = sheets;
        this.analysis = analysis;
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public List<String> getSheets() { return sheets; }
    public void setSheets(List<String> sheets) { this.sheets = sheets; }
    public Map<String, Object> getAnalysis() { return analysis; }
    public void setAnalysis(Map<String, Object> analysis) { this.analysis = analysis; }
}
