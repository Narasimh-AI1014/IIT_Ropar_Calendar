package com.iitrpr.calendar.matching;

import java.util.List;

public class AudienceCriteria {
    private String type;               // "ALL", "COURSES", "GROUP"
    private List<String> courseCodes;  // For COURSES type mappings
    private List<String> deptCodes;    // For GROUP type: e.g. ["CSE", "EE"]
    private List<Integer> entryYears;  // For GROUP type: e.g. [2023, 2024]

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<String> getCourseCodes() { return courseCodes; }
    public void setCourseCodes(List<String> courseCodes) { this.courseCodes = courseCodes; }
    public List<String> getDeptCodes() { return deptCodes; }
    public void setDeptCodes(List<String> deptCodes) { this.deptCodes = deptCodes; }
    public List<Integer> getEntryYears() { return entryYears; }
    public void setEntryYears(List<Integer> entryYears) { this.entryYears = entryYears; }
}
