package com.iitrpr.calendar.domain;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "sync_task", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "status"})})
public class SyncTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reason")
    private String reason; // 'IMPORT', 'EVENT', 'ENROLMENT', 'MANUAL', 'BACKFILL'

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // 'PENDING', 'RUNNING', 'DONE', 'FAILED'

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_run_at", nullable = false)
    private ZonedDateTime nextRunAt = ZonedDateTime.now();

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    // Getters, Setters
}
