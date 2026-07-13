package com.jobradar.service;

import com.jobradar.entity.Job;
import com.jobradar.entity.SyncLog;
import com.jobradar.entity.VisitLog;
import com.jobradar.repository.JobRepository;
import com.jobradar.repository.SyncLogRepository;
import com.jobradar.repository.UserRepository;
import com.jobradar.repository.VisitLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

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

    /** 汇总卡片数据 */
    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long totalVisits = visitLogRepo.count();
        long todayVisits = visitLogRepo.countByCreatedAtBetween(todayStart, now);
        long uniqueIps = visitLogRepo.countDistinctIpByCreatedAtBetween(LocalDate.now().minusDays(30).atStartOfDay(), now);
        long totalUsers = userRepo.count();
        long newUsersToday = userRepo.countByCreatedAtBetween(todayStart, now);
        long activeUsers7d = visitLogRepo.countDistinctUserIdByCreatedAtBetween(
                LocalDate.now().minusDays(7).atStartOfDay(), now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalVisits", totalVisits);
        result.put("todayVisits", todayVisits);
        result.put("uniqueIps", uniqueIps);
        result.put("totalUsers", totalUsers);
        result.put("newUsersToday", newUsersToday);
        result.put("activeUsers7d", activeUsers7d);
        return result;
    }

    /** 每日访问量 + 每日注册量 */
    @Transactional(readOnly = true)
    public Map<String, Object> dailyStats(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        // 按天聚合访问量
        List<VisitLog> visits = visitLogRepo.findByCreatedAtBetween(start, end);
        Map<LocalDate, Long> visitByDay = visits.stream()
                .collect(Collectors.groupingBy(v -> v.getCreatedAt().toLocalDate(), Collectors.counting()));

        // 按天聚合注册量（查询用户表）
        Map<LocalDate, Long> regByDay = userRepo.findByCreatedAtBetween(start, end).stream()
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate(), Collectors.counting()));

        // 补齐空缺日期
        List<Map<String, Object>> visitDays = new ArrayList<>();
        List<Map<String, Object>> regDays = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Map<String, Object> vd = new LinkedHashMap<>();
            vd.put("date", d.toString());
            vd.put("count", visitByDay.getOrDefault(d, 0L));
            visitDays.add(vd);

            Map<String, Object> rd = new LinkedHashMap<>();
            rd.put("date", d.toString());
            rd.put("count", regByDay.getOrDefault(d, 0L));
            regDays.add(rd);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("visits", visitDays);
        result.put("registrations", regDays);
        return result;
    }

    /** 页面热度排行 */
    @Transactional(readOnly = true)
    public Map<String, Object> pageStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<VisitLog> pageVisits = visitLogRepo.findByVisitTypeAndCreatedAtBetween("page", start, end);

        // 按 pageName 分组统计；无 pageName 的按 path 首段
        Map<String, Long> counts = pageVisits.stream()
                .collect(Collectors.groupingBy(v -> {
                    String pn = v.getPageName();
                    if (pn != null && !pn.isBlank()) return pn;
                    // 回退：从 path 提取页面名
                    String path = v.getPath();
                    if (path != null && path.startsWith("/page-")) {
                        return path.substring(6);
                    }
                    return "其他";
                }, Collectors.counting()));

        List<Map<String, Object>> pages = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("page", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("pages", pages);
        return result;
    }

    /** 最近访问记录 */
    @Transactional(readOnly = true)
    public Map<String, Object> recentVisits(int limit) {
        LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<VisitLog> logs = visitLogRepo.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        List<Map<String, Object>> visits = logs.stream()
                .limit(limit)
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

    /** 时段流量分布（24小时） */
    @Transactional(readOnly = true)
    public Map<String, Object> hourlyStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<VisitLog> logs = visitLogRepo.findByCreatedAtBetween(start, end);
        Map<Integer, Long> hourCounts = logs.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getCreatedAt().getHour(),
                        Collectors.counting()
                ));

        // 补齐 24 小时
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("hour", String.format("%02d:00", h));
            entry.put("count", hourCounts.getOrDefault(h, 0L));
            hours.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("hours", hours);
        return result;
    }

    /** 流量来源分布 */
    @Transactional(readOnly = true)
    public Map<String, Object> sourceStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<VisitLog> logs = visitLogRepo.findByCreatedAtBetween(start, end);
        Map<String, Long> sourceCounts = logs.stream()
                .filter(v -> v.getSource() != null && !v.getSource().isBlank())
                .collect(Collectors.groupingBy(VisitLog::getSource, Collectors.counting()));

        // 确保三类来源都存在
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

    /** 访问地区分布 Top10 */
    @Transactional(readOnly = true)
    public Map<String, Object> regionStats(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<VisitLog> logs = visitLogRepo.findByCreatedAtBetween(start, end);
        Map<String, Long> regionCounts = logs.stream()
                .filter(v -> v.getRegion() != null && !v.getRegion().isBlank())
                .map(v -> {
                    // "中国-广东-深圳" → "广东"
                    String[] parts = v.getRegion().split("-");
                    return parts.length >= 2 ? parts[1] : parts[0];
                })
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

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

    /** 岗位数据库概览 */
    @Transactional(readOnly = true)
    public Map<String, Object> jobsStats() {
        String today = LocalDate.now().toString();
        long totalJobs = jobRepo.count();
        long openJobs = jobRepo.countOpen(today);
        long expiredJobs = jobRepo.countExpired(today);
        long distinctCompanies = jobRepo.countDistinctCo();
        long todayNew = jobRepo.countByUpdatedAt(today);
        String lastSync = jobRepo.maxUpdatedAt();

        List<Job> allJobs = jobRepo.findAll();

        // 按行业分组 Top10
        List<Map<String, Object>> byIndustry = allJobs.stream()
                .filter(j -> j.getIndustry() != null && !j.getIndustry().isBlank())
                .collect(Collectors.groupingBy(Job::getIndustry, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // 按招聘类型分组
        List<Map<String, Object>> byRecruitType = allJobs.stream()
                .filter(j -> j.getRecruitType() != null && !j.getRecruitType().isBlank())
                .collect(Collectors.groupingBy(Job::getRecruitType, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalJobs", totalJobs);
        result.put("openJobs", openJobs);
        result.put("expiredJobs", expiredJobs);
        result.put("distinctCompanies", distinctCompanies);
        result.put("todayNew", todayNew);
        result.put("lastSync", lastSync != null ? lastSync : "");
        result.put("byIndustry", byIndustry);
        result.put("byRecruitType", byRecruitType);
        return result;
    }

    /** 会员统计 */
    @Transactional(readOnly = true)
    public Map<String, Object> memberStats() {
        LocalDateTime now = LocalDateTime.now();
        long totalUsers = userRepo.count();

        long activeMembers = userRepo.findAll().stream()
                .filter(u -> u.getMemberUntil() != null && u.getMemberUntil().isAfter(now))
                .count();

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

    /** 同步历史记录 */
    @Transactional(readOnly = true)
    public Map<String, Object> syncHistory(int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1).atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<SyncLog> logs = syncLogRepo.findBySyncTimeBetweenOrderBySyncTimeDesc(start, end);

        // 按天聚合
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

    /** 每日活跃用户（DAU）—— 登录用户访问数 */
    @Transactional(readOnly = true)
    public Map<String, Object> dailyActiveUsers(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        // 从 visit_log 按天聚合去重 user_id
        List<VisitLog> logs = visitLogRepo.findByCreatedAtBetween(start, end);
        Map<LocalDate, Long> dauByDay = logs.stream()
                .filter(v -> v.getUserId() != null)
                .collect(Collectors.groupingBy(
                        v -> v.getCreatedAt().toLocalDate(),
                        Collectors.mapping(VisitLog::getUserId, Collectors.toSet())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size()));

        // 补齐空缺日期
        List<Map<String, Object>> dauList = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", d.toString());
            entry.put("count", dauByDay.getOrDefault(d, 0L));
            dauList.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("dau", dauList);
        return result;
    }

    /** 记录前端页面浏览 ping */
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

    /** 注册用户列表（分页） */
    @Transactional(readOnly = true)
    public Map<String, Object> users(int page, int size) {
        var pageResult = userRepo.findAll(
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("createdAt").descending()));

        List<Map<String, Object>> list = pageResult.getContent().stream()
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("account", u.getAccount());
                    m.put("displayName", u.getDisplayName());
                    m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
                    // 会员状态：memberUntil 不为空且未过期 = 会员
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

    private String maskIp(String ip) {
        if (ip == null) return null;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) return ip.substring(0, lastDot) + ".*";
        return ip;
    }
}
