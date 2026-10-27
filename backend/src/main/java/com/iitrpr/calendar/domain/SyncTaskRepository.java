package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SyncTaskRepository extends JpaRepository<SyncTask, Long> {
    // Native query optimization matching your structural index configurations
    @Query(value = "SELECT * FROM sync_task WHERE status = 'PENDING' AND next_run_at <= NOW() ORDER BY next_run_at ASC", nativeQuery = true)
    List<SyncTask> findDuePendingTasks();
}
