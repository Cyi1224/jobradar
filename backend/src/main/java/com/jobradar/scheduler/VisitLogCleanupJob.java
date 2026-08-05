package com.jobradar.scheduler;

import com.jobradar.repository.VisitLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 每天凌晨 3:00 清理 7 天前的 visit_log 记录。
 * 防止表无限增长导致 OOM。
 */
@Component
public class VisitLogCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(VisitLogCleanupJob.class);
    private static final int RETENTION_DAYS = 7;

    private final VisitLogRepository visitLogRepo;

    public VisitLogCleanupJob(VisitLogRepository visitLogRepo) {
        this.visitLogRepo = visitLogRepo;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldVisitLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = visitLogRepo.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("VisitLog cleanup: deleted {} records older than {} (retention={}d)",
                    deleted, cutoff.toLocalDate(), RETENTION_DAYS);
        }
    }
}
