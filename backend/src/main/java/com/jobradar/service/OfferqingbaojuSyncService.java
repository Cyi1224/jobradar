package com.jobradar.service;

import com.jobradar.config.OfferqingbaojuProperties;
import com.jobradar.dto.JobSyncReq;
import com.jobradar.dto.offerqingbaoju.OfferqingbaojuDataResponse;
import com.jobradar.entity.SyncLog;
import com.jobradar.repository.SyncLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 每日从 offerqingbaoju.cn 同步最新更新的校招信息到 JobRadar。
 * <p>
 * 与 {@link OfferbiuSyncService} 同一套骨架：启动后 30 秒首次异步同步 + cron 定时同步 +
 * 手动触发，AtomicBoolean 防并发。只处理源站「最新一次更新（更新时间字段最大）」的批次，
 * updatedAt 透传源站「更新时间」，与 offerbiu 数据一起通过业务主键去重入库。
 */
@Service
public class OfferqingbaojuSyncService {

    private static final Logger log = LoggerFactory.getLogger(OfferqingbaojuSyncService.class);

    private final RestTemplate restTemplate;
    private final OfferqingbaojuProperties properties;
    private final JobService jobService;
    private final SyncLogRepository syncLogRepo;

    /** 防止并发重复同步 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 锁获取时间戳，用于超时看门狗 */
    private volatile long lockAcquiredAt = 0;
    /** 同步锁超时时间：超过此时间未释放则强制重置 */
    private static final long LOCK_TIMEOUT_MS = 10 * 60_000; // 10 分钟

    public OfferqingbaojuSyncService(RestTemplate restTemplate,
                                     OfferqingbaojuProperties properties,
                                     JobService jobService,
                                     SyncLogRepository syncLogRepo) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.jobService = jobService;
        this.syncLogRepo = syncLogRepo;
    }

    /**
     * 应用启动完成后 30 秒，异步执行首次同步（仅当 sync.enabled=true 时）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (properties.getSync().isEnabled()) {
            log.info("[offerqingbaoju-sync] 应用已启动，将在 30 秒后执行首次同步...");
            new Thread(() -> {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                SyncResult result = syncFromOfferqingbaoju("STARTUP");
                log.info("[offerqingbaoju-sync] 首次同步完成：拉取 {} 条，新增 {} 条，跳过 {} 条，耗时 {} 秒",
                        result.fetched, result.inserted, result.skipped, result.durationSeconds);
            }, "offerqingbaoju-init-sync").start();
        } else {
            log.info("[offerqingbaoju-sync] 同步已禁用 (offerqingbaoju.sync.enabled=false)");
        }
    }

    /**
     * 定时同步：按 offerqingbaoju.sync.cron 表达式执行。
     */
    @Scheduled(cron = "${offerqingbaoju.sync.cron:0 0 6 * * ?}")
    public void scheduledSync() {
        if (properties.getSync().isEnabled()) {
            log.info("[offerqingbaoju-sync] 定时同步触发...");
            new Thread(() -> {
                SyncResult result = syncFromOfferqingbaoju("SCHEDULED");
                log.info("[offerqingbaoju-sync] 定时同步完成：拉取 {} 条，新增 {} 条，跳过 {} 条，耗时 {} 秒",
                        result.fetched, result.inserted, result.skipped, result.durationSeconds);
            }, "offerqingbaoju-scheduled-sync").start();
        }
    }

    /**
     * 公开方法：手动触发同步（供 admin 端点调用）。同步执行，返回结果。
     */
    public SyncResult syncNow() {
        return syncFromOfferqingbaoju("MANUAL");
    }

    /**
     * 核心同步逻辑：抓取配置的 navigation 全部分页，仅保留源站「最新一次更新」的批次，
     * 映射后经业务主键去重入库（与 offerbiu 共用同一套 insertNewJobs）。
     */
    private SyncResult syncFromOfferqingbaoju(String triggerType) {
        // 超时看门狗：如果锁被持有超过 10 分钟，强制重置（防止线程卡死导致永久阻塞）
        long lockHeldMs = System.currentTimeMillis() - lockAcquiredAt;
        if (running.get() && lockHeldMs > LOCK_TIMEOUT_MS) {
            log.error("[offerqingbaoju-sync] ⚠️ 同步锁已持有 {} 秒未释放，强制重置！上次同步可能异常卡死",
                    lockHeldMs / 1000);
            running.set(false);
            lockAcquiredAt = 0;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("[offerqingbaoju-sync] 上一次同步尚未完成（已运行 {} 秒），跳过本次执行",
                    (System.currentTimeMillis() - lockAcquiredAt) / 1000);
            return new SyncResult(0, 0, 0, 0);
        }
        lockAcquiredAt = System.currentTimeMillis();

        Instant start = Instant.now();
        int totalFetched = 0;
        int totalInserted = 0;
        int totalSkipped = 0;

        try {
            String baseUrl = properties.getBaseUrl();
            List<Integer> navIds = properties.getSync().getNavigationIds();
            int pageSize = properties.getSync().getPageSize();
            int maxPages = properties.getSync().getMaxPages();
            long pageDelayMs = properties.getSync().getPageDelayMs();

            List<OfferqingbaojuDataResponse.Row> allRows = new ArrayList<>();

            // 1. 分页抓取每个 navigation 的全部数据（源站第 2 页起需登录，默认靠大 per_page 一页拉全）
            for (Integer navId : navIds) {
                int totalPages = Integer.MAX_VALUE;
                int fetchedPages = 0;
                for (int page = 1; page <= totalPages; page++) {
                    if (maxPages > 0 && page > maxPages) {
                        break;
                    }
                    String url = baseUrl + "/api/simple/navigation/" + navId
                            + "/data?page=" + page + "&per_page=" + pageSize;
                    OfferqingbaojuDataResponse resp;
                    try {
                        resp = restTemplate.getForObject(url, OfferqingbaojuDataResponse.class);
                    } catch (RestClientException e) {
                        log.error("[offerqingbaoju-sync] 请求失败 (navigation={}, page={}): {}",
                                navId, page, e.getMessage());
                        break;
                    }

                    if (resp == null || !resp.success()
                            || resp.data() == null || resp.data().isEmpty()) {
                        log.warn("[offerqingbaoju-sync] 响应异常或已拉完 (navigation={}, page={})", navId, page);
                        break;
                    }

                    List<OfferqingbaojuDataResponse.Row> items = resp.data();
                    totalFetched += items.size();
                    allRows.addAll(items);
                    fetchedPages++;

                    if (resp.pagination() != null) {
                        totalPages = resp.pagination().total_pages();
                    }

                    if (pageDelayMs > 0 && page < totalPages) {
                        try {
                            Thread.sleep(pageDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                if (totalPages != Integer.MAX_VALUE && fetchedPages < totalPages) {
                    log.warn("[offerqingbaoju-sync] navigation={} 共 {} 页，仅匿名拉到 {} 页（第 2 页起需登录，可调大 page-size 一次性拉全）",
                            navId, totalPages, fetchedPages);
                }
                log.info("[offerqingbaoju-sync] navigation={} 拉取完成，当前累计 {} 条", navId, totalFetched);
            }

            // 2. 只保留源站最新一次更新（更新时间字段最大）的批次
            List<OfferqingbaojuDataResponse.Row> newestRows = filterNewest(allRows);
            log.info("[offerqingbaoju-sync] 最新更新批次 {} 条（总拉取 {} 条）", newestRows.size(), totalFetched);

            // 3. 映射 + 去重入库（updatedAt 透传源站更新时间，见 mapToJobSyncReq）
            List<JobSyncReq> dtos = newestRows.stream()
                    .map(this::mapToJobSyncReq)
                    .collect(Collectors.toList());
            Map<String, Integer> result = jobService.insertNewJobs(dtos);
            totalInserted += result.getOrDefault("inserted", 0);
            totalSkipped += result.getOrDefault("skipped", 0);

            log.info("[offerqingbaoju-sync] 入库完成：新增 {} 条，跳过 {} 条", totalInserted, totalSkipped);

            // 记录成功日志
            try {
                SyncLog successLog = new SyncLog();
                successLog.setSyncTime(LocalDateTime.now());
                successLog.setStatus("SUCCESS");
                successLog.setFetched(totalFetched);
                successLog.setInserted(totalInserted);
                successLog.setSkipped(totalSkipped);
                successLog.setDurationSeconds((int) Duration.between(start, Instant.now()).getSeconds());
                successLog.setTriggerType(triggerType);
                syncLogRepo.save(successLog);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.error("[offerqingbaoju-sync] 同步过程异常: {}", e.getMessage(), e);
            // 记录失败日志
            try {
                SyncLog failLog = new SyncLog();
                failLog.setSyncTime(LocalDateTime.now());
                failLog.setStatus("FAILURE");
                failLog.setFetched(totalFetched);
                failLog.setInserted(totalInserted);
                failLog.setSkipped(totalSkipped);
                failLog.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知错误");
                failLog.setTriggerType(triggerType);
                syncLogRepo.save(failLog);
            } catch (Exception ignored) {}
        } finally {
            running.set(false);
            lockAcquiredAt = 0;
        }

        long durationSeconds = Duration.between(start, Instant.now()).getSeconds();
        return new SyncResult(totalFetched, totalInserted, totalSkipped, (int) durationSeconds);
    }

    /**
     * 只保留「更新时间」为最新日期的行；若某行更新时间无法解析则并入最新批次。
     * 注意源站个别行日期带斜杠（如 2026/05/23），需先归一化再按 LocalDate 比较，
     * 否则字符串字典序会把带斜杠的旧日期误判为最新。
     */
    private List<OfferqingbaojuDataResponse.Row> filterNewest(List<OfferqingbaojuDataResponse.Row> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        LocalDate newest = null;
        for (OfferqingbaojuDataResponse.Row r : rows) {
            LocalDate d = parseUpdateTime(r.updateTime());
            if (d != null && (newest == null || d.isAfter(newest))) {
                newest = d;
            }
        }
        if (newest == null) {
            return rows; // 全部无有效更新时间，整体视为最新批次
        }
        final LocalDate newestDate = newest;
        return rows.stream()
                .filter(r -> {
                    LocalDate d = parseUpdateTime(r.updateTime());
                    return d == null || d.equals(newestDate);
                })
                .toList();
    }

    /**
     * 解析「更新时间」为 LocalDate；支持 YYYY-MM-DD 与 YYYY/MM/DD，解析失败返回 null。
     */
    private LocalDate parseUpdateTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim().replace('/', '-'));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将 offerqingbaoju 单行映射为 JobRadar 的同步请求 DTO。
     */
    private JobSyncReq mapToJobSyncReq(OfferqingbaojuDataResponse.Row row) {
        // 招聘对象：毕业年份 "2027" → "2027届"；"2026,2027" → "2026届,2027届"
        String target = "";
        if (row.graduationYear() != null && !row.graduationYear().isBlank()) {
            target = Arrays.stream(row.graduationYear().split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(y -> y.matches("\\d{4}") ? y + "届" : y)
                    .collect(Collectors.joining(","));
        }

        // 岗位文本截断至 2000 字符
        String positions = row.position() != null ? row.position() : "";
        if (positions.length() > 2000) {
            positions = positions.substring(0, 1997) + "...";
        }

        // 备注：学历要求，截断至 500 字符
        String note = row.education() != null ? row.education() : "";
        if (note.length() > 500) {
            note = note.substring(0, 497) + "...";
        }

        // 更新时间：透传源站「更新时间」（归一化为 YYYY-MM-DD），更真实地反映源站更新日期；
        // 源站无有效日期时兜底用同步当天。
        LocalDate sourceDate = parseUpdateTime(row.updateTime());
        String updatedAt = sourceDate != null
                ? sourceDate.toString()
                : java.time.LocalDate.now().toString();

        return new JobSyncReq(
                row.companyName() != null ? row.companyName() : "",
                row.companyNature() != null ? row.companyNature() : "",
                row.industry() != null ? row.industry() : "",
                row.recruitBatch() != null ? row.recruitBatch() : "",
                target,
                row.location() != null ? row.location() : "",
                positions,
                updatedAt,
                row.deadline() != null ? row.deadline() : "",
                row.applyUrl() != null ? row.applyUrl() : "",
                row.announcementUrl() != null ? row.announcementUrl() : "",
                note
        );
    }

    /**
     * 同步结果。
     */
    public record SyncResult(int fetched, int inserted, int skipped, int durationSeconds) {}
}
