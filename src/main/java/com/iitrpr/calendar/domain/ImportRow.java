package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_row")
public class ImportRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "import_id", nullable = false)
    private Long importId;

    @Column(name = "line", nullable = false)
    private Integer line;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, columnDefinition = "jsonb")
    private String data; 

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", nullable = false, columnDefinition = "jsonb")
    private String errors = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false, columnDefinition = "jsonb")
    private String warnings = "[]";

    public ImportRow() {}

    // ==========================================
    // GETTERS & SETTERS (Strict Formatted camelCase Naming Alignment)
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public Integer getLine() { return line; }
    public void setLine(Integer line) { this.line = line; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getErrors() { return errors; }
    public void setErrors(String errors) { this.errors = errors; }

    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
}
