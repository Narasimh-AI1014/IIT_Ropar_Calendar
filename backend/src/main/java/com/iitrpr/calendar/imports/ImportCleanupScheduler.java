package com.iitrpr.calendar.imports;

import com.iitrpr.calendar.domain.ImportJob;
import com.iitrpr.calendar.domain.ImportJobRepository;
import com.iitrpr.calendar.domain.ImportRowRepository;
import com.iitrpr.calendar.domain.ImportRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
public class ImportCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ImportCleanupScheduler.class);

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private ImportRowRepository importRowRepository;

    /**
     * Wakes up every 10 minutes to clean up stale upload artifacts.
     * Identifies preview import records where the current system time sits past the expires_at boundary.
     */
    @Scheduled(fixedRate = 600000) // 600,000 milliseconds = 10 minutes interval loop
    @Transactional
    public void purgeStaleImportPreviews() {
        ZonedDateTime now = ZonedDateTime.now();
        logger.info("Executing background scheduled calendar import cleanup cycle at {}", now);

        // 1. Fetch all parent import records still stuck in preview mode that have passed their 1-hour shelf life
        List<ImportJob> expiredJobs = importJobRepository.findAll().stream()
                .filter(job -> "PREVIEW".equalsIgnoreCase(job.getStatus()) && job.getExpiresAt().isBefore(now))
                .toList();

        if (expiredJobs.isEmpty()) {
            logger.info("Cleanup iteration complete: Zero expired preview import jobs found.");
            return;
        }

        logger.info("Found {} expired import preview jobs scheduled for decommissioning.", expiredJobs.size());

        for (ImportJob job : expiredJobs) {
            // 2. Mark the master record state status as EXPIRED permanently
            job.setStatus("EXPIRED");
            job.setError("Preview session window expired after remaining unconfirmed for 1 hour.");
            importJobRepository.save(job);

            // 3. Purge the highly granular staged row line copies from the import_row database table cache
            List<ImportRow> linkedRows = importRowRepository.findAll().stream()
                    .filter(row -> row.getImportId().equals(job.getId()))
                    .toList();

            if (!linkedRows.isEmpty()) {
                importRowRepository.deleteAll(linkedRows);
                logger.info("Successfully dropped {} cached temporary data rows for expired job ID {}", linkedRows.size(), job.getId());
            }
        }

        logger.info("Background calendar import cleanup cycle completed successfully.");
    }
}
