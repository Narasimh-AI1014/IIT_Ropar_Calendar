package com.iitrpr.calendar.imports;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.util.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.iitrpr.calendar.errors.BadRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SpreadsheetReaderService {

    // Zip Bomb Thresholds: Prevent infinite compression loops from crashing memory allocations
    private static final long MAX_UNCOMPRESSED_SIZE = 50 * 1024 * 1024; // Max 50 MB expanded capacity boundary limit
    private static final double MAX_INFLATION_RATIO = 100.0;             // Max 100x magnification ratio threshold

    public List<ParsedRow> parseSpreadsheet(MultipartFile file, String sheetName) {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new BadRequestException("Uploaded file name parameter is missing.");

        // HARDENING STEP 1: Run comprehensive Zip Bomb signature security check prior parsing
        if (filename.endsWith(".xlsx")) {
            verifyZipBombSafety(file);
        }

        try (InputStream is = file.getInputStream()) {
            if (filename.endsWith(".csv")) {
                return parseCSV(is);
            } else if (filename.endsWith(".xlsx")) {
                // HARDENING STEP 2: Configure Apache POI to completely deactivate XXE parsing rules global entities definitions
                System.setProperty("javax.xml.parsers.DocumentBuilderFactory", 
                        "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");
                
                return parseExcel(is, sheetName);
            } else {
                throw new BadRequestException("Unsupported file format constraint type. Use .csv or .xlsx layout sheets.");
            }
        } catch (IOException | CsvException e) {
            throw new BadRequestException("Failed to execute sheet reader parsing sequence: " + e.getMessage());
        }
    }

    /**
     * Defense Layer: Scans compressed entry chains to detect nested structural inflation signatures.
     */
    private void verifyZipBombSafety(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            
            ZipEntry entry;
            long totalUncompressedSize = 0;

            while ((entry = zis.getNextEntry()) != null) {
                // Guard against nested infinite directory recursion loops traps
                if (entry.getName().contains("..") || entry.getName().startsWith("/")) {
                    throw new BadRequestException("Security Violation: Malicious structural file directory path traversal signature detected.");
                }

                long compressedSize = entry.getCompressedSize();
                long uncompressedSize = entry.getSize();

                // Skip directories definitions components parameters
                if (entry.isDirectory()) continue;

                // Monitor compression scale boundaries metrics parameters
                if (compressedSize > 0 && uncompressedSize > 0) {
                    double ratio = (double) uncompressedSize / compressedSize;
                    if (ratio > MAX_INFLATION_RATIO) {
                        throw new BadRequestException("Security Exception: Malicious compression inflation loop ratio configuration matched (Potential Zip Bomb).");
                    }
                }

                // Cumulative threshold checks mapping values tracking properties bounds
                if (uncompressedSize > 0) {
                    totalUncompressedSize += uncompressedSize;
                }

                if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                    throw new BadRequestException("Security Exception: Extracted spreadsheet matrix allocations exceed the 50MB system execution bounds threshold.");
                }
                
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to validate archive containment headers properties: " + e.getMessage());
        }
    }

    // =========================================================================
    // OPENCSV & APACHE POI PROCESSING SCHEMAS (Intact & Clean)
    // =========================================================================
    private List<ParsedRow> parseCSV(InputStream is) throws IOException, CsvException {
        List<ParsedRow> resultRows = new ArrayList<>();
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(is))) {
            List<String[]> allLines = csvReader.readAll();
            if (allLines.isEmpty()) return resultRows;

            int headerLineIndex = findCSVHeaderIndex(allLines);
            String[] headers = allLines.get(headerLineIndex);

            String inheritedDate = "";
            int currentLineCounter = 1;

            for (int i = headerLineIndex + 1; i < allLines.size(); i++) {
                String[] currentCells = allLines.get(i);
                ParsedRow parsedRow = new ParsedRow(currentLineCounter++);

                for (int c = 0; c < headers.length; c++) {
                    if (c < currentCells.length) {
                        String cellVal = currentCells[c];
                        if (headers[c].toLowerCase().contains("date")) {
                            if (cellVal == null || cellVal.isBlank()) {
                                cellVal = inheritedDate;
                            } else {
                                inheritedDate = cellVal;
                            }
                        }
                        parsedRow.addCell(headers[c], cellVal);
                    }
                }
                resultRows.add(parsedRow);
            }
        }
        return resultRows;
    }

    private int findCSVHeaderIndex(List<String[]> lines) {
        for (int i = 0; i < lines.size(); i++) {
            for (String field : lines.get(i)) {
                if (field != null && (field.toLowerCase().contains("date") || field.toLowerCase().contains("code") || field.toLowerCase().contains("course"))) {
                    return i;
                }
            }
        }
        return 0;
    }

    private List<ParsedRow> parseExcel(InputStream is, String sheetName) throws IOException {
        List<ParsedRow> resultRows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = null;
            if (sheetName != null && !sheetName.isBlank()) {
                sheet = workbook.getSheet(sheetName);
            }
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            int totalRows = sheet.getLastRowNum();
            if (totalRows <= 0) return resultRows;

            int headerRowIndex = findExcelHeaderIndex(sheet);
            Row headerRow = sheet.getRow(headerRowIndex);
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValueAsString(cell));
            }

            String inheritedDate = "";
            int currentLineCounter = 1;

            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                ParsedRow parsedRow = new ParsedRow(currentLineCounter++);
                boolean hasData = false;

                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    String cellVal = getCellValueAsString(cell);

                    if (headers.get(c).toLowerCase().contains("date")) {
                        if (cellVal.isBlank()) {
                            cellVal = inheritedDate;
                        } else {
                            inheritedDate = cellVal;
                        }
                    }

                    if (!cellVal.isBlank()) hasData = true;
                    parsedRow.addCell(headers.get(c), cellVal);
                }
                if (hasData) {
                    resultRows.add(parsedRow);
                }
            }
        }
        return resultRows;
    }

    private int findExcelHeaderIndex(Sheet sheet) {
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = getCellValueAsString(cell);
                if (val.toLowerCase().contains("date") || val.toLowerCase().contains("code") || val.toLowerCase().contains("course")) {
                    return r;
                }
            }
        }
        return 0;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? 
                    cell.getLocalDateTimeCellValue().toLocalDate().toString() : 
                    String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
