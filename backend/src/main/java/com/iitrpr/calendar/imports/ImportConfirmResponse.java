package com.iitrpr.calendar.imports;

public class ImportConfirmResponse {
    private Long importId;
    private String status;
    private int savedCount;

    public ImportConfirmResponse() {}

    public ImportConfirmResponse(Long importId, String status, int savedCount) {
        this.importId = importId;
        this.status = status;
        this.savedCount = savedCount;
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getSavedCount() { return savedCount; }
    public void setSavedCount(int savedCount) { this.savedCount = savedCount; }
}
