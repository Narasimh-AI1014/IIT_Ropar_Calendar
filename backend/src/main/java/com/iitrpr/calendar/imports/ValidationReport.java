package com.iitrpr.calendar.imports;

import java.util.ArrayList;
import java.util.List;

public class ValidationReport {
    private boolean valid = true;
    private String blockedReason; // "GRID" or "ENROLMENT"
    private List<String> globalErrors = new ArrayList<>();

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getBlockedReason() { return blockedReason; }
    public void setBlockedReason(String blockedReason) { this.blockedReason = blockedReason; }
    public List<String> getGlobalErrors() { return globalErrors; }
    public void setGlobalErrors(List<String> globalErrors) { this.globalErrors = globalErrors; }
    
    public void addGlobalError(String error) {
        this.globalErrors.add(error);
        this.valid = false;
    }
}
