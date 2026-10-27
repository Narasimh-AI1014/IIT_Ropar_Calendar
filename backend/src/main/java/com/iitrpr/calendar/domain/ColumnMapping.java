package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode; // ─── CRITICAL IMPORT 1 ───
import org.hibernate.type.SqlTypes;           // ─── CRITICAL IMPORT 2 ───
import java.time.ZonedDateTime;

@Entity
@Table(name = "column_mapping", uniqueConstraints = {@UniqueConstraint(columnNames = {"kind", "header_hash"})})
public class ColumnMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "header_hash", nullable = false)
    private String headerHash;

    @JdbcTypeCode(SqlTypes.JSON) // Safely maps Java String fields to PostgreSQL native JSONB fields
    @Column(name = "mapping", nullable = false, columnDefinition = "jsonb")
    private String mapping;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @PreUpdate
    protected void onUpdate() { 
        this.updatedAt = ZonedDateTime.now(); 
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getHeaderHash() { return headerHash; }
    public void setHeaderHash(String headerHash) { this.headerHash = headerHash; }

    public String getMapping() { return mapping; }
    public void setMapping(String mapping) { this.mapping = mapping; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }
}
