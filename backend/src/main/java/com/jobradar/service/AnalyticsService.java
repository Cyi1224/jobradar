package com.jobradar.service;

import com.jobradar.entity.SyncLog;
import com.jobradar.entity.VisitLog;
import com.jobradar.repository.JobRepository;
import com.jobradar.repository.SyncLogRepository;
import com.jobradar.repository.UserRepository;
import com.jobradar.repository.VisitLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分析服务 — v2: 所有聚合在 SQL 端完成，不把全表加载到 JVM 内存。
 */
@Service
public class AnalyticsService {

    private final VisitLogRepository visitLogRepo;
    private final UserRepository userRepo;
    private final JobRepository jobRepo;
    private final SyncLogRepository syncLogRepo;

    public AnalyticsService(VisitLogRepository visitLogRepo, UserRepository userRepo,
                            JobRepository jobRepo, SyncLogRepository syncLogRepo) {
        this.visitLogRepo = visitLogRepo;
        this.userRepo = userRepo;
        this.jobRepo = jobRepo;
        this.syncLogRepo = syncLogRepo;
    }

    // ═══════════════════════ 汇总 ═══════════════════════

    /** 汇总卡片数据 — 纯 COUNT 查询，无实体加载 */
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalVisits", visitLogRepo.count());
        result.put("todayVisits", visitLogRepo.countByCreatedAtBetween(todayStart, now));
        result.put("uniqueIps", visitLogRepo.countDistinctIpByCreatedAtBetween(
                LocalDate.now().minusDays(30).atStartOfDay(), now));
        result.put("totalUsers", userRepo.count());
        result.put("newUsersToday", userRepo.countByCreatedAtBetween(todayStart, now));
        result.put("activeUsers7d", visitLogRepo.countDistinctUserIdByCreatedAtBetween(
                LocalDate.now().minusDays(7).atStartOfDay(), now));
        return result;
    }

    // ═══════════════════════ 每日访问 + 注册 ═══════════════════════

    /** 每日统计 — SQL GROUP BY DATE，不加载实体 */
    @Transactional(readOnly = true)
    public Map<String, Object> dailyStats(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        // SQL: SELECT DATE(created_at), COUNT(*) ... GROUP BY DATE(created_at)
        List<Object[]> visitRows = visitLogRepo.countByDayBetween(start, end);
        Map<String, Long> visitByDay = new LinkedHashMap<>();
        for (Object[] row : visitRows) {
            visitByDay.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        // 注册量保持原逻辑（用户量不大，findByCreatedAtBetween 可接受）
        Map<String, Long> regByDay = userRepo.findByCreatedAtBetween(start, end).stream()
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate().toString(),
                        Collectors.counting()));

        List<Map<String, Object>> visitDays = new ArrayList<>();
        List<Map<String, Object>> regDays = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String key = d.toString();
            Map<String, Object> vd = new LinkedHashMap<>();
            vd.put("date", key);
            vd.put("count", visitByDay.getOrDefault(key, 0L));
            visitDays.add(vd);

            Map<String, Object> rd = new LinkedHashMap<>();
            rd.put("date", key);
            rd.put("count", regByDay.getOrDefault(key, 0L));
            regDays.add(rd);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("visits", visitDays);
        result.put("registrations", regDays);
        return result;
    }

    // ═══════════════════════ 页面热度 ═══════════════════════

    /** 页面热度排行 — SQL GROUP BY pageName */
    @Transactional(readOnly = true)
    public Map<String, Object> pageStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<Object[]> rows = visitLogRepo.countByPageBetween(start, end);

        List<Map<String, Object>> pages = rows.stream()
                .map(row -> {
                    String pageName = row[0] != null ? String.valueOf(row[0]) : null;
                    String path = row[1] != null ? String.valueOf(row[1]) : null;
                    long count = ((Number) row[2]).longValue();

                    // 回退：无 pageName 时从 path 提取
                    String label = (pageName != null && !pageName.isBlank()) ? pageName
                            : (path != null && path.startsWith("/page-")) ? path.substring(6) : "其他";

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("page", label);
                    m.put("count", count);
                    return m;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                .limit(10)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("pages", pages);
        return result;
    }

    // ═══════════════════════ 最近访问 ═══════════════════════

    /** 最近访问 — 分页查询，只取 limit 条 */
    @Transactional(readOnly = true)
    public Map<String, Object> recentVisits(int limit) {
        LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<VisitLog> logs = visitLogRepo.findRecentByCreatedAtBetween(start, end,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<Map<String, Object>> visits = logs.stream()
                .map(v -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
                    m.put("path", v.getPath());
                    m.put("method", v.getMethod());
                    m.put("ip", maskIp(v.getIp()));
                    m.put("username", v.getUsername() != null ? v.getUsername() : "匿名");
                    m.put("visitType", v.getVisitType());
                    m.put("pageName", v.getPageName());
                    m.put("region", v.getRegion() != null ? v.getRegion() : "");
                    m.put("source", v.getSource() != null ? v.getSource() : "直接访问");
                    m.put("referer", v.getReferer() != null ? v.getReferer() : "");
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("visits", visits);
        return result;
    }

    // ═══════════════════════ 时段分布 ═══════════════════════

    /** 24 小时分布 — SQL GROUP BY HOUR */
    @Transactional(readOnly = true)
    public Map<String, Object> hourlyStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<Object[]> rows = visitLogRepo.countByHourBetween(start, end);
        Map<Integer, Long> hourMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            hourMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("hour", String.format("%02d:00", h));
            entry.put("count", hourMap.getOrDefault(h, 0L));
            hours.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("hours", hours);
        return result;
    }

    // ═══════════════════════ 来源分布 ═══════════════════════

    /** 流量来源 — SQL GROUP BY source */
    @Transactional(readOnly = true)
    public Map<String, Object> sourceStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<Object[]> rows = visitLogRepo.countBySourceBetween(start, end);
        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? String.valueOf(row[0]) : "直接访问";
            sourceCounts.put(key, ((Number) row[1]).longValue());
        }
        for (String key : List.of("搜索引擎", "外部链接", "直接访问")) {
            sourceCounts.putIfAbsent(key, 0L);
        }

        List<Map<String, Object>> sources = sourceCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("source", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("sources", sources);
        return result;
    }

    // ═══════════════════════ 地区分布 ═══════════════════════

    /** 地区 Top10 — SQL GROUP BY region */
    @Transactional(readOnly = true)
    public Map<String, Object> regionStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<Object[]> rows = visitLogRepo.countByRegionBetween(start, end);

        // group parsed province
        Map<String, Long> regionCounts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String region = row[0] != null ? String.valueOf(row[0]) : null;
            if (region == null || region.isBlank()) continue;
            String[] parts = region.split("-");
            String province = parts.length >= 2 ? parts[1] : parts[0];
            regionCounts.merge(province, ((Number) row[1]).longValue(), Long::sum);
        }

        List<Map<String, Object>> regions = regionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("region", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("regions", regions);
        return result;
    }

    // ═══════════════════════ 岗位统计 ═══════════════════════

    /** 岗位概览 — SQL 聚合，不再 jobRepo.findAll() */
    @Transactional(readOnly = true)
    public Map<String, Object> jobsStats() {
        String today = LocalDate.now().toString();

        // 行业 Top10: SQL GROUP BY
        List<Map<String, Object>> byIndustry = jobRepo.countByIndustry().stream()
                .limit(10)
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", String.valueOf(row[0]));
                    m.put("count", ((Number) row[1]).longValue());
                    return m;
                })
                .collect(Collectors.toList());

        // 招聘类型分布: SQL GROUP BY
        List<Map<String, Object>> byRecruitType = jobRepo.countByRecruitType().stream()
                .map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", String.valueOf(row[0]));
                    m.put("count", ((Number) row[1]).longValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalJobs", jobRepo.count());
        result.put("openJobs", jobRepo.countOpen(today));
        result.put("expiredJobs", jobRepo.countExpired(today));
        result.put("distinctCompanies", jobRepo.countDistinctCo());
        result.put("todayNew", jobRepo.countByUpdatedAt(today));
        String lastSync = jobRepo.maxUpdatedAt();
        result.put("lastSync", lastSync != null ? lastSync : "");
        result.put("byIndustry", byIndustry);
        result.put("byRecruitType", byRecruitType);
        return result;
    }

    // ═══════════════════════ 会员统计 ═══════════════════════

    /** 会员统计 — SQL COUNT，不再 userRepo.findAll() */
    @Transactional(readOnly = true)
    public Map<String, Object> memberStats() {
        long totalUsers = userRepo.count();
        long activeMembers = userRepo.countActiveMembers(LocalDateTime.now());
        long freeUsers = totalUsers - activeMembers;
        double conversionRate = totalUsers > 0
                ? Math.round(activeMembers * 10000.0 / totalUsers) / 100.0
                : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMembers", totalUsers);
        result.put("activeMembers", activeMembers);
        result.put("freeUsers", freeUsers);
        result.put("conversionRate", conversionRate);
        return result;
    }

    // ═══════════════════════ 会员账户列表 ═══════════════════════

    /** 所有开通过会员的用户（含已过期），按到期时间倒序，带剩余天数与状态 */
    @Transactional(readOnly = true)
    public Map<String, Object> memberUsers(int page, int size) {
        var pageResult = userRepo.findByMemberUntilIsNotNullOrderByMemberUntilDesc(
                PageRequest.of(page, size));

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> list = pageResult.getContent().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("account", u.getAccount());
            m.put("displayName", u.getDisplayName());
            m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
            m.put("memberUntil", u.getMemberUntil() != null ? u.getMemberUntil().toString() : null);
            boolean active = u.getMemberUntil() != null && u.getMemberUntil().isAfter(now);
            long daysLeft = u.getMemberUntil() != null
                    ? Duration.between(now, u.getMemberUntil()).toDays() : 0;
            m.put("daysLeft", daysLeft);
            m.put("status", active ? "有效" : "已过期");
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("members", list);
        result.put("total", pageResult.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", pageResult.getTotalPages());
        return result;
    }

    // ═══════════════════════ 同步历史 ═══════════════════════

    @Transactional(readOnly = true)
    public Map<String, Object> syncHistory(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        var logs = syncLogRepo.findBySyncTimeBetweenOrderBySyncTimeDesc(start, end);

        Map<LocalDate, List<SyncLog>> byDay = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getSyncTime().toLocalDate()));

        List<Map<String, Object>> daysList = new ArrayList<>();
        for (LocalDate d = LocalDate.now().minusDays(days - 1); !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
            List<SyncLog> dayLogs = byDay.getOrDefault(d, List.of());
            long successCount = dayLogs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
            long failureCount = dayLogs.stream().filter(l -> "FAILURE".equals(l.getStatus())).count();
            int totalInserted = dayLogs.stream().mapToInt(SyncLog::getInserted).sum();
            String status = dayLogs.isEmpty() ? "NONE" : failureCount > 0 ? "PARTIAL" : "SUCCESS";
            String errorMsg = dayLogs.stream()
                    .filter(l -> l.getErrorMessage() != null)
                    .map(SyncLog::getErrorMessage)
                    .findFirst().orElse(null);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", d.toString());
            entry.put("status", status);
            entry.put("inserted", totalInserted);
            entry.put("successCount", successCount);
            entry.put("failureCount", failureCount);
            if (errorMsg != null) entry.put("error", errorMsg);
            daysList.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("history", daysList);
        return result;
    }

    // ═══════════════════════ DAU ═══════════════════════

    /** 每日活跃用户 — SQL COUNT(DISTINCT userId) GROUP BY DATE */
    @Transactional(readOnly = true)
    public Map<String, Object> dailyActiveUsers(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<Object[]> rows = visitLogRepo.countDauByDayBetween(start, end);
        Map<String, Long> dauMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            dauMap.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        List<Map<String, Object>> dauList = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", d.toString());
            entry.put("count", dauMap.getOrDefault(d.toString(), 0L));
            dauList.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("dau", dauList);
        return result;
    }

    // ═══════════════════════ 用户转化漏斗 ═══════════════════════

    /** 用户转化漏斗：独立访客(近N天去重IP) → 新增注册(近N天) → 活跃会员(近N天活跃的有效会员)，含每级转化率。
     *  三个指标同时间窗，转化率语义干净（<100% 正常）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> userFunnel(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long visitors = visitLogRepo.countDistinctIpByCreatedAtBetween(start, end);        // 近 N 天独立访客
        long newRegistered = userRepo.countByCreatedAtBetween(start, end);                 // 近 N 天新增注册
        long activeMemberUsers = visitLogRepo.countActiveMemberUsersBetween(start, end, now); // 近 N 天活跃的有效会员

        List<Map<String, Object>> funnel = new ArrayList<>();
        funnel.add(funnelLevel("独立访客", visitors, null));
        funnel.add(funnelLevel("新增注册", newRegistered, visitors));
        funnel.add(funnelLevel("活跃会员", activeMemberUsers, newRegistered));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("currentActiveMembers", userRepo.countActiveMembers(now));   // 参考：当前有效会员总数
        result.put("funnel", funnel);
        return result;
    }

    private Map<String, Object> funnelLevel(String label, long count, Long prev) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("count", count);
        m.put("rate", prev != null && prev > 0 ? Math.round(count * 10000.0 / prev) / 100.0 : null);
        return m;
    }

    // ═══════════════════════ 经常在线用户 ═══════════════════════

    /** 近 N 天活跃用户：活跃总数 + Top 榜（用户名/访问数/活跃天数/最后活跃） */
    @Transactional(readOnly = true)
    public Map<String, Object> activeUsers(int days, int limit) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        long activeCount = visitLogRepo.countDistinctUserIdByCreatedAtBetween(start, end);
        List<Object[]> rows = visitLogRepo.countTopActiveUsers(start, end, PageRequest.of(0, limit));

        List<Map<String, Object>> top = rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", String.valueOf(row[0]));
            m.put("visits", ((Number) row[1]).longValue());
            m.put("activeDays", ((Number) row[2]).longValue());
            String last = row[3] != null ? String.valueOf(row[3]).replace("T", " ") : null;
            m.put("lastActive", last != null && last.length() >= 16 ? last.substring(0, 16) : last);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("activeCount", activeCount);
        result.put("top", top);
        return result;
    }

    // ═══════════════════════ 页面浏览上报 ═══════════════════════

    @Transactional
    public void logPageView(String pageName, String path, String ip, String userAgent) {
        VisitLog log = new VisitLog();
        log.setVisitType("page");
        log.setPageName(pageName);
        log.setPath(path);
        log.setMethod("GET");
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setCreatedAt(LocalDateTime.now());
        visitLogRepo.save(log);
    }

    // ═══════════════════════ 用户列表 ═══════════════════════

    @Transactional(readOnly = true)
    public Map<String, Object> users(int page, int size) {
        var pageResult = userRepo.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        List<Map<String, Object>> list = pageResult.getContent().stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("account", u.getAccount());
                    m.put("displayName", u.getDisplayName());
                    m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
                    boolean isMember = u.getMemberUntil() != null &&
                            u.getMemberUntil().isAfter(LocalDateTime.now());
                    m.put("memberStatus", isMember ? "会员" : "免费版");
                    m.put("memberUntil", u.getMemberUntil() != null ? u.getMemberUntil().toString() : null);
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", list);
        result.put("total", pageResult.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", pageResult.getTotalPages());
        return result;
    }

    // ═══════════════════════ Util ═══════════════════════

    private String maskIp(String ip) {
        if (ip == null) return null;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) return ip.substring(0, lastDot) + ".*";
        return ip;
    }
}