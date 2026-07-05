package com.jobradar.service;

import com.jobradar.config.OfferbiuProperties;
import com.jobradar.dto.JobSyncReq;
import com.jobradar.dto.offerbiu.OfferbiuPostingItem;
import com.jobradar.dto.offerbiu.OfferbiuPostingResponse;
import com.jobradar.dto.offerbiu.OfferbiuSeasonResponse;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 每日从 offerbiu.com 同步校招信息到 JobRadar。
 * <p>
 * 启动后会自动执行一次首次同步（异步），之后按 offerbiu.sync.cron 定时执行。
 * 使用 AtomicBoolean 防止并发重复执行。
 */
@Service
public class OfferbiuSyncService {

    private static final Logger log = LoggerFactory.getLogger(OfferbiuSyncService.class);

    private final RestTemplate restTemplate;
    private final OfferbiuProperties properties;
    private final JobService jobService;

    /** 防止并发重复同步 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OfferbiuSyncService(RestTemplate restTemplate,
                               OfferbiuProperties properties,
                               JobService jobService) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.jobService = jobService;
    }

    /**
     * 应用启动完成后 30 秒，异步执行首次同步（仅当 sync.enabled=true 时）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (properties.getSync().isEnabled()) {
            log.info("[offerbiu-sync] 应用已启动，将在 30 秒后执行首次同步...");
            new Thread(() -> {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                SyncResult result = syncFromOfferbiu();
                log.info("[offerbiu-sync] 首次同步完成：拉取 {} 条，新增 {} 条，跳过 {} 条，耗时 {} 秒",
                        result.fetched, result.inserted, result.skipped, result.durationSeconds);
            }, "offerbiu-init-sync").start();
        } else {
            log.info("[offerbiu-sync] 同步已禁用 (offerbiu.sync.enabled=false)");
        }
    }

    /**
     * 定时同步：按 offerbiu.sync.cron 表达式执行。
     */
    @Scheduled(cron = "${offerbiu.sync.cron:0 0 6 * * ?}")
    public void scheduledSync() {
        if (properties.getSync().isEnabled()) {
            log.info("[offerbiu-sync] 定时同步触发...");
            new Thread(() -> {
                SyncResult result = syncFromOfferbiu();
                log.info("[offerbiu-sync] 定时同步完成：拉取 {} 条，新增 {} 条，跳过 {} 条，耗时 {} 秒",
                        result.fetched, result.inserted, result.skipped, result.durationSeconds);
            }, "offerbiu-scheduled-sync").start();
        }
    }

    /**
     * 公开方法：手动触发同步（供 admin 端点调用）。同步执行，返回结果。
     */
    public SyncResult syncNow() {
        return syncFromOfferbiu();
    }

    /**
     * 核心同步逻辑：只抓取每个招聘季最新几页做增量对比，不全量同步。
     */
    private SyncResult syncFromOfferbiu() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[offerbiu-sync] 上一次同步尚未完成，跳过本次执行");
            return new SyncResult(0, 0, 0, 0);
        }

        Instant start = Instant.now();
        int totalFetched = 0;
        int totalInserted = 0;
        int totalSkipped = 0;

        try {
            String baseUrl = properties.getBaseUrl();
            int pageSize = properties.getSync().getPageSize();
            int maxPages = properties.getSync().getMaxPages();
            long pageDelayMs = properties.getSync().getPageDelayMs();

            // 1. 获取活跃的招聘季年份
            List<Integer> seasons = fetchSeasons(baseUrl);
            log.info("[offerbiu-sync] 获取到 {} 个招聘季: {}，每个最多拉取 {} 页",
                    seasons.size(), seasons, maxPages);

            // 2. 每个招聘季只拉取最新几页做增量对比
            for (Integer seasonYear : seasons) {
                for (int page = 0; page < maxPages; page++) {
                    String url = baseUrl + "/api/recruitment/postings?seasonYear="
                            + seasonYear + "&page=" + page + "&size=" + pageSize;
                    OfferbiuPostingResponse resp;
                    try {
                        resp = restTemplate.getForObject(url, OfferbiuPostingResponse.class);
                    } catch (RestClientException e) {
                        String msg = e.getMessage();
                        if (msg != null && msg.contains("403")) {
                            log.debug("[offerbiu-sync] 第 {} 页需会员 (seasonYear={})，停止该季",
                                    page + 1, seasonYear);
                        } else {
                            log.error("[offerbiu-sync] 请求失败 (seasonYear={}, page={}): {}",
                                    seasonYear, page, msg);
                        }
                        break;
                    }

                    if (resp == null || !resp.success() || resp.data() == null) {
                        log.warn("[offerbiu-sync] 响应异常 (seasonYear={}, page={})", seasonYear, page);
                        break;
                    }

                    List<OfferbiuPostingItem> items = resp.data().items();
                    if (items == null || items.isEmpty()) {
                        break;
                    }

                    totalFetched += items.size();

                    List<JobSyncReq> dtos = items.stream()
                            .map(this::mapToJobSyncReq)
                            .collect(Collectors.toList());

                    Map<String, Integer> result = jobService.insertNewJobs(dtos);
                    totalInserted += result.getOrDefault("inserted", 0);
                    totalSkipped += result.getOrDefault("skipped", 0);

                    log.info("[offerbiu-sync] seasonYear={} page={}: 拉取 {} 条，新增 {} 条，跳过 {} 条",
                            seasonYear, page, items.size(),
                            result.getOrDefault("inserted", 0),
                            result.getOrDefault("skipped", 0));

                    if (pageDelayMs > 0 && page < maxPages - 1) {
                        try {
                            Thread.sleep(pageDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("[offerbiu-sync] 同步过程异常: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }

        long durationSeconds = Duration.between(start, Instant.now()).getSeconds();
        return new SyncResult(totalFetched, totalInserted, totalSkipped, (int) durationSeconds);
    }

    /**
     * 获取 offerbiu 上活跃的招聘季年份列表。
     */
    private List<Integer> fetchSeasons(String baseUrl) {
        try {
            OfferbiuSeasonResponse resp = restTemplate.getForObject(
                    baseUrl + "/api/recruitment/seasons", OfferbiuSeasonResponse.class);
            if (resp != null && resp.success() && resp.data() != null) {
                return resp.data().stream()
                        .map(OfferbiuSeasonResponse.SeasonItem::seasonYear)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
            }
        } catch (RestClientException e) {
            log.error("[offerbiu-sync] 获取招聘季列表失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 将 offerbiu 单条岗位映射为 JobRadar 的同步请求 DTO。
     */
    private JobSyncReq mapToJobSyncReq(OfferbiuPostingItem item) {
        // 招聘对象：targetYears → "2027届" 或 "2026届,2027届"
        String target = "";
        if (item.targetYears() != null && !item.targetYears().isEmpty()) {
            target = item.targetYears().stream()
                    .map(y -> y + "届")
                    .collect(Collectors.joining(","));
        }

        // 城市：locations → 逗号拼接
        String city = "";
        if (item.locations() != null && !item.locations().isEmpty()) {
            city = String.join(",", item.locations());
        }

        // 岗位文本截断至 2000 字符
        String positions = item.positionsText() != null ? item.positionsText() : "";
        if (positions.length() > 2000) {
            positions = positions.substring(0, 1997) + "...";
        }

        // 更新时间：提取日期部分
        String updatedAt = "";
        if (item.sourceUpdatedAt() != null && item.sourceUpdatedAt().length() >= 10) {
            updatedAt = item.sourceUpdatedAt().substring(0, 10); // "2026-07-03"
        }

        // 截止时间：优先 deadlineAt，否则用 deadlineText
        String deadline = "";
        if (item.deadlineAt() != null && !item.deadlineAt().isBlank()) {
            deadline = item.deadlineAt();
        } else if (item.deadlineText() != null && !item.deadlineText().isBlank()) {
            deadline = item.deadlineText();
        }

        // 备注：笔试政策 + 备注
        StringBuilder noteBuilder = new StringBuilder();
        if (item.examPolicy() != null && !item.examPolicy().isBlank()) {
            noteBuilder.append(item.examPolicy());
        }
        if (item.noteText() != null && !item.noteText().isBlank()) {
            if (noteBuilder.length() > 0) {
                noteBuilder.append("; ");
            }
            noteBuilder.append(item.noteText());
        }
        String note = noteBuilder.toString();
        if (note.length() > 500) {
            note = note.substring(0, 497) + "...";
        }

        return new JobSyncReq(
                item.companyName() != null ? item.companyName() : "",
                item.companyNature() != null ? item.companyNature() : "",
                item.industry() != null ? item.industry() : "",
                item.recruitType() != null ? item.recruitType() : "",
                target,
                city,
                positions,
                updatedAt,
                deadline,
                item.applyUrl() != null ? item.applyUrl() : "",
                item.announcementUrl() != null ? item.announcementUrl() : "",
                note
        );
    }

    /**
     * 同步结果。
     */
    public record SyncResult(int fetched, int inserted, int skipped, int durationSeconds) {}
}
