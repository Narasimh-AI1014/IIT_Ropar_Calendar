package com.iitrpr.calendar.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iitrpr.calendar.domain.*;
import com.iitrpr.calendar.errors.BadRequestException;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;



import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/imports")
public class BulkImportController {

    @Autowired
    private SpreadsheetReaderService spreadsheetReaderService;

    @Autowired
    private ColumnMappingService columnMappingService;

    @Autowired
    private ImportValidationService importValidationService;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private ImportRowRepository importRowRepository;

    private final ObjectMapper jacksonMapper = new ObjectMapper();

    // =========================================================================
    // 1. POST /api/admin/imports/preview - NON-PERSISTENT ROW ANALYSIS
    // =========================================================================
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> getImportFilePreview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sheet", required = false) String sheetName,
            @RequestParam(value = "kind", defaultValue = "classes") String kind) {

        List<ParsedRow> rawRows = spreadsheetReaderService.parseSpreadsheet(file, sheetName);
        if (rawRows.isEmpty()) {
            throw new BadRequestException("The uploaded spreadsheet contains no data rows.");
        }

        List<String> headers = new ArrayList<>(rawRows.get(0).getData().keySet());
        List<Map<String, String>> sampleRows = new ArrayList<>();
        for (int i = 0; i < Math.min(rawRows.size(), 5); i++) {
            sampleRows.add(rawRows.get(i).getData());
        }

        Map<String, String> userMappingMock = new HashMap<>(); 
        Map<String, String> resolvedSchemaMap = columnMappingService.resolveColumnSchemaMap(headers, sampleRows, userMappingMock);

        ValidationReport structuralReport = new ValidationReport();
        importValidationService.detectSheetTypeMismatch(kind, resolvedSchemaMap, structuralReport);

        List<Map<String, Object>> processedRowsReport = new ArrayList<>();
        boolean hasAnyBlockingErrors = !structuralReport.isValid();

        for (ParsedRow rawRow : rawRows) {
            Map<String, String> rowData = rawRow.getData();

            if (importValidationService.isSummaryRow(rowData)) {
                continue; 
            }

            Map<String, Object> rowReport = new HashMap<>();
            rowReport.put("lineNumber", rawRow.getLineNumber());
            rowReport.put("rawData", rowData);

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            if (resolvedSchemaMap.containsKey("course_code")) {
                String courseCodeKey = resolvedSchemaMap.get("course_code");
                String rawCourseCode = rowData.get(courseCodeKey);
                if (rawCourseCode == null || rawCourseCode.isBlank()) {
                    errors.add("Course code value cannot be blank.");
                } else if (!rawCourseCode.equals(rawCourseCode.toUpperCase())) {
                    warnings.add("Course code '" + rawCourseCode + "' automatically converted to uppercase.");
                    rowData.put(courseCodeKey, rawCourseCode.toUpperCase().trim());
                }
            }

            if (resolvedSchemaMap.containsKey("event_date")) {
                String dateKey = resolvedSchemaMap.get("event_date");
                String rawDate = rowData.get(dateKey);
                try {
                    LocalDate parsedDate = DateTimeParserUtil.parseDayFirstDate(rawDate);
                    rowReport.put("parsedDate", parsedDate != null ? parsedDate.toString() : null);
                } catch (Exception e) {
                    errors.add(e.getMessage());
                }
            }

            LocalTime parsedStartTime = null;
            LocalTime parsedEndTime = null;
            if (resolvedSchemaMap.containsKey("time_range")) {
                String timeRangeKey = resolvedSchemaMap.get("time_range");
                String rawTimeRange = rowData.get(timeRangeKey);
                try {
                    LocalTime[] times = DateTimeParserUtil.parseTimeRange(rawTimeRange);
                    if (times != null) {
                        parsedStartTime = times[0];
                        parsedEndTime = times[1];
                        rowReport.put("parsedStartTime", parsedStartTime.toString());
                        rowReport.put("parsedEndTime", parsedEndTime.toString());
                    }
                } catch (Exception e) {
                    errors.add(e.getMessage());
                }
            }

            if (resolvedSchemaMap.containsKey("time_range") && errors.isEmpty() && parsedStartTime != null && parsedEndTime != null) {
                String slotCodeKey = resolvedSchemaMap.getOrDefault("slot_code", "slot_code");
                String courseCodeKey = resolvedSchemaMap.get("course_code");
                
                String rawSlotCode = rowData.getOrDefault(slotCodeKey, "GEN-SLOT");
                String rawCourseCode = rowData.getOrDefault(courseCodeKey, "UNKNOWN");
                
                Short weekday = 1;
                if (rowData.containsKey("weekday")) {
                    try { weekday = Short.valueOf(rowData.get("weekday").trim()); } catch (NumberFormatException ignored) {}
                }

                String clashResult = importValidationService.checkTimetableClash(
                    rawCourseCode, rawSlotCode, weekday, parsedStartTime, parsedEndTime
                );
                
                if (clashResult != null) {
                    warnings.add(clashResult); 
                }
            }

            rowReport.put("errors", errors);
            rowReport.put("warnings", warnings);

            if (!errors.isEmpty()) {
                hasAnyBlockingErrors = true;
            }

            processedRowsReport.add(rowReport);
        }

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("importJobId", System.currentTimeMillis());
        finalResult.put("status", hasAnyBlockingErrors ? "FAILED" : "PREVIEW");
        finalResult.put("blockedReason", structuralReport.getBlockedReason());
        finalResult.put("globalErrors", structuralReport.getGlobalErrors());
        finalResult.put("kind", kind);
        finalResult.put("totalRows", processedRowsReport.size());
        finalResult.put("resolvedMappings", resolvedSchemaMap);
        finalResult.put("rows", processedRowsReport);

        return ResponseEntity.ok(finalResult);
    }

    // =========================================================================
    // 2. POST /api/admin/imports - PERSISTENT PREVIEW INGESTION (JSONB)
    // =========================================================================
    @PostMapping
    @Transactional
    public ResponseEntity<ImportAnalysisResponse> saveImportJobPreview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sheet", required = false) String sheetName,
            @RequestParam(value = "kind", defaultValue = "classes") String kind,
            @RequestParam("userId") Long userId) {

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "spreadsheet.xlsx";

        List<ParsedRow> rawRows = spreadsheetReaderService.parseSpreadsheet(file, sheetName);
        if (rawRows.isEmpty()) {
            throw new BadRequestException("The uploaded spreadsheet file contains no valid rows.");
        }

        List<String> headers = new ArrayList<>(rawRows.get(0).getData().keySet());
        List<Map<String, String>> sampleRows = new ArrayList<>();
        for (int i = 0; i < Math.min(rawRows.size(), 5); i++) {
            sampleRows.add(rawRows.get(i).getData());
        }

        Map<String, String> userMappingMock = new HashMap<>(); 
        Map<String, String> resolvedSchemaMap = columnMappingService.resolveColumnSchemaMap(headers, sampleRows, userMappingMock);

        ValidationReport structuralReport = new ValidationReport();
        importValidationService.detectSheetTypeMismatch(kind, resolvedSchemaMap, structuralReport);

        ImportJob job = new ImportJob();
        job.setUserId(userId);
        job.setKind(kind.toLowerCase().trim());
        job.setSheet(sheetName != null ? sheetName : "Sheet1");
        job.setStatus("PREVIEW"); 
        job.setFileName(originalFileName);
        job.setBlockedReason(structuralReport.getBlockedReason());
        job.setSavedCount(0);

        ImportJob savedJob = importJobRepository.save(job);

        int baselineLineCounter = 1;
        int errorRowCount = 0;
        int warningRowCount = 0;

        for (ParsedRow rawRow : rawRows) {
            Map<String, String> rowData = rawRow.getData();
            if (importValidationService.isSummaryRow(rowData)) {
                continue;
            }

            List<String> rowErrors = new ArrayList<>();
            List<String> rowWarnings = new ArrayList<>();

            if (resolvedSchemaMap.containsKey("course_code")) {
                String val = rowData.get(resolvedSchemaMap.get("course_code"));
                if (val == null || val.isBlank()) rowErrors.add("Course code cannot be blank.");
            }

            try {
                ImportRow dbRow = new ImportRow();
                dbRow.setImportId(savedJob.getId());
                dbRow.setLine(baselineLineCounter++);
                dbRow.setData(jacksonMapper.writeValueAsString(rowData));
                dbRow.setErrors(jacksonMapper.writeValueAsString(rowErrors));
                dbRow.setWarnings(jacksonMapper.writeValueAsString(rowWarnings));
                if (!rowErrors.isEmpty()) errorRowCount++;
                if (!rowWarnings.isEmpty()) warningRowCount++;
                importRowRepository.save(dbRow);
            } catch (Exception e) {
                throw new BadRequestException("Failed to format dynamic JSONB data strings: " + e.getMessage());
            }
        }
                Map<String, Object> summaryAnalysis = new HashMap<>();
        summaryAnalysis.put("totalParsedRows", rawRows.size());
        summaryAnalysis.put("failedRowsCount", errorRowCount);
        summaryAnalysis.put("warningsRowsCount", warningRowCount);
        summaryAnalysis.put("resolvedSchemaColumns", resolvedSchemaMap);
        summaryAnalysis.put("sheetValidationStatus", structuralReport.isValid() ? "PASSED" : "BLOCKED");

        // ✅ FIXED Syntax: Creates a type-safe List mapping using standard initializers
        List<String> availableSheetsList = new ArrayList<>();
        availableSheetsList.add(sheetName != null ? sheetName : "Sheet1");

        return ResponseEntity.ok(new ImportAnalysisResponse(
            savedJob.getId(),
            originalFileName,
            availableSheetsList,
            summaryAnalysis
        ));
    }

        // =========================================================================
    // 3. POST /api/admin/imports/{id}/analyze - LIVE RE-ANALYSIS ROUTE
    // =========================================================================
    @PostMapping("/{id}/analyze")
    @Transactional
    public ResponseEntity<ImportAnalysisResponse> reAnalyzeImportJob(
            @PathVariable("id") Long importId,
            @Valid @RequestBody ReAnalysisRequest request) {

        // 1. Fetch the existing master ImportJob staging entry
        ImportJob job = importJobRepository.findById(importId)
                .orElseThrow(() -> new BadRequestException("Staged import job configuration entry ID '" + importId + "' not found."));

        if (!"PREVIEW".equalsIgnoreCase(job.getStatus())) {
            throw new BadRequestException("Operation Denied: Cannot re-analyze an import job that is already committed or abandoned.");
        }

        // 2. Fetch the raw data lines previously saved during initial file parsing ingestion
        List<ImportRow> existingRows = importRowRepository.findAll().stream()
                .filter(row -> row.getImportId().equals(importId))
                .toList();

        if (existingRows.isEmpty()) {
            throw new BadRequestException("No data row artifacts found linked to import ID: " + importId);
        }

        // 3. Clear out old evaluation records from the database table to prevent duplication conflicts
        importRowRepository.deleteAll(existingRows);

        // 4. Update the parent master job parameters with the new selections
        job.setKind(request.getKind().toLowerCase().trim());
        if (request.getSheetName() != null && !request.getSheetName().isBlank()) {
            job.setSheet(request.getSheetName().trim());
        }
        
        List<Map<String, String>> rowDataList = new ArrayList<>();
        for (ImportRow oldRow : existingRows) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> dataMap = jacksonMapper.readValue(oldRow.getData(), Map.class);
                rowDataList.add(dataMap);
            } catch (Exception ignored) {}
        }

        List<String> headers = new ArrayList<>(rowDataList.isEmpty() ? List.of() : rowDataList.get(0).keySet());
        
        Map<String, String> userMappingMock = new HashMap<>();
        Map<String, String> freshSchemaMap = columnMappingService.resolveColumnSchemaMap(headers, rowDataList, userMappingMock);

        ValidationReport structuralReport = new ValidationReport();
        importValidationService.detectSheetTypeMismatch(job.getKind(), freshSchemaMap, structuralReport);
        
        job.setBlockedReason(structuralReport.getBlockedReason());
        ImportJob updatedJob = importJobRepository.save(job);

        // 5. Loop over the text payloads, evaluate constraints against the new rules, and save
        int lineCounter = 1;
        int errorRowCount = 0;
        int warningRowCount = 0;

        for (Map<String, String> dataMap : rowDataList) {
            List<String> freshErrors = new ArrayList<>();
            List<String> freshWarnings = new ArrayList<>();

            if (freshSchemaMap.containsKey("course_code")) {
                String code = dataMap.get(freshSchemaMap.get("course_code"));
                if (code == null || code.isBlank()) freshErrors.add("Course code cannot be blank.");
            }
            
            if ("enrolment".equalsIgnoreCase(updatedJob.getKind()) && !freshSchemaMap.containsKey("email")) {
                freshErrors.add("Enrolment validation requires a target student email property.");
            }

            try {
                ImportRow newRow = new ImportRow();
                newRow.setImportId(updatedJob.getId());
                newRow.setLine(lineCounter++);
                newRow.setData(jacksonMapper.writeValueAsString(dataMap));
                newRow.setErrors(jacksonMapper.writeValueAsString(freshErrors));
                newRow.setWarnings(jacksonMapper.writeValueAsString(freshWarnings));

                if (!freshErrors.isEmpty()) errorRowCount++;
                if (!freshWarnings.isEmpty()) warningRowCount++;

                importRowRepository.save(newRow);
            } catch (Exception e) {
                throw new BadRequestException("Failed to save re-analyzed data row entries: " + e.getMessage());
            }
        }

        // 6. Build the structural overview response package
        Map<String, Object> summaryAnalysis = new HashMap<>();
        summaryAnalysis.put("totalParsedRows", rowDataList.size());
        summaryAnalysis.put("failedRowsCount", errorRowCount);
        summaryAnalysis.put("warningsRowsCount", warningRowCount);
        summaryAnalysis.put("resolvedSchemaColumns", freshSchemaMap);
        summaryAnalysis.put("sheetValidationStatus", structuralReport.isValid() ? "PASSED" : "BLOCKED");

        List<String> sheetsList = new ArrayList<>();
        sheetsList.add(updatedJob.getSheet());

        return ResponseEntity.ok(new ImportAnalysisResponse(
            updatedJob.getId(),
            updatedJob.getFileName(),
            sheetsList,
            summaryAnalysis
        ));
    }

    @Autowired
    private AppUserRepository appUserRepository;
    
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

     @Autowired
    private com.iitrpr.calendar.sync.SyncTaskDispatcher syncTaskDispatcher;

    // =========================================================================
    // 4. POST /api/admin/imports/{id}/confirm - DATA TRANSACTION PIPELINE
    // =========================================================================
    @PostMapping("/{id}/confirm")
    @Transactional
    public ResponseEntity<ImportConfirmResponse> confirmImportJob(
            @PathVariable("id") Long importId,
            @RequestParam("userId") Long adminId) {

        // 1. Fetch and lock down parent import job configuration rules
        ImportJob job = importJobRepository.findById(importId)
                .orElseThrow(() -> new BadRequestException("Staged import configuration index ID '" + importId + "' not found."));

        if (!"PREVIEW".equalsIgnoreCase(job.getStatus())) {
            throw new BadRequestException("Operation Denied: This data ingestion job is already processed or expired.");
        }

        if (job.getBlockedReason() != null) {
            throw new BadRequestException("Operation Denied: Cannot commit a spreadsheet marked as BLOCKED due to: " + job.getBlockedReason());
        }

        // 2. Fetch row elements staged during parsing iterations
        List<ImportRow> stagedRows = importRowRepository.findAll().stream()
                .filter(row -> row.getImportId().equals(importId))
                .toList();

        if (stagedRows.isEmpty()) {
            throw new BadRequestException("No staging data rows found bound to import ID: " + importId);
        }

        int successSavedCounter = 0;
        String jobKind = job.getKind().toLowerCase().trim();

        // 3. Loop over records, run final validation checks, and apply natural key upsert logic
        for (ImportRow stagedRow : stagedRows) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> rowData = jacksonMapper.readValue(stagedRow.getData(), Map.class);
                
                // If row formatting evaluations contain blocking error tags, skip to protect records
                @SuppressWarnings("unchecked")
                List<String> rowErrors = jacksonMapper.readValue(stagedRow.getErrors(), List.class);
                if (rowErrors != null && !rowErrors.isEmpty()) {
                    continue; 
                }

                if ("classes".equalsIgnoreCase(jobKind) || "courses".equalsIgnoreCase(jobKind)) {
                    // Extract data criteria
                    String rawCode = rowData.getOrDefault("course_code", rowData.get("code"));
                    String rawTitle = rowData.getOrDefault("title", "Advanced Class Topic");
                    String rawInstructor = rowData.getOrDefault("instructor_names", rowData.get("instructor"));

                    if (rawCode == null || rawCode.isBlank()) continue;
                    String targetCode = rawCode.trim().toUpperCase();

                    // Natural Key Upsert Strategy: Look up course by string primary text key code token
                    Course course = courseRepository.findById(targetCode).orElse(new Course());
                    course.setCode(targetCode);
                    course.setTitle(rawTitle.trim());
                    course.setInstructorNames(rawInstructor != null ? rawInstructor.trim() : "Faculty Staff");
                    course.setTermId(1L); // Baseline development anchor

                    courseRepository.save(course);
                    successSavedCounter++;
                } 
                else if ("enrolment".equalsIgnoreCase(jobKind)) {
                    String targetCode = rowData.get("course_code");
                    String rawEmail = rowData.get("email");

                    if (targetCode == null || rawEmail == null) continue;
                    
                    // Match referencing system identity fields to look up target user ID
                    Optional<AppUser> userOpt = appUserRepository.findByEmail(rawEmail.trim().toLowerCase());
                    if (userOpt.isEmpty()) continue; // Skip if user record is entirely untracked

                    Long studentUserId = userOpt.get().getId();
                    String cleanCourseCode = targetCode.trim().toUpperCase();

                    // Natural Key Upsert Strategy: Find entry by composite user + course keys to prevent uniqueness crashes
                    List<Enrolment> checks = enrolmentRepository.findByUserIdAndStatus(studentUserId, "ENRO");
                    Enrolment enrolment = checks.stream()
                            .filter(en -> en.getCourseCode().equalsIgnoreCase(cleanCourseCode))
                            .findFirst()
                            .orElse(new Enrolment());

                    enrolment.setUserId(studentUserId);
                    enrolment.setCourseCode(cleanCourseCode);
                    enrolment.setGrp(rowData.getOrDefault("grp", "G1").trim().toUpperCase());
                    enrolment.setStatus("ENRO");

                    enrolmentRepository.save(enrolment);
                    successSavedCounter++;
                }
            } catch (Exception e) {
                throw new BadRequestException("Data ingestion engine encountered a processing crash segment: " + e.getMessage());
            }
        }

        // 4. Update the parent master job execution history indicators
        job.setStatus("DONE"); // Commits transactional safety seal
        job.setSavedCount(successSavedCounter);
        job.setConfirmedAt(java.time.ZonedDateTime.now());
        importJobRepository.save(job);

        // 5. Write historical tracking entry to the immutable database audit trail log layout
        AuditLog audit = new AuditLog();
        audit.setUserId(adminId);
        audit.setAction("CONFIRM_IMPORT");
        audit.setDetail(String.format("Admin committed Import Job ID %d (%s). Total upserted database rows: %d", 
                importId, jobKind, successSavedCounter));
        audit.setCallingSite("http://localhost:5175"); // Admin Management dashboard portal domain origin
        auditLogRepository.save(audit);

        if (successSavedCounter > 0) {
            syncTaskDispatcher.dispatchTasksForEvent(null, "ALL", null, null, null);
        }
        // 6. Present structured execution report response data package back to the workspace client
        return ResponseEntity.ok(new ImportConfirmResponse(importId, "DONE", successSavedCounter));
    }


    

} // <─── THIS IS THE FINAL CLOSING BRACE OF YOUR CLASS



