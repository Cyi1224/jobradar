package com.jobradar.service;

import com.jobradar.entity.VisitLog;
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

    public AnalyticsService(VisitLogRepository visitLogRepo, UserRepository userRepo) {
        this.visitLogRepo = visitLogRepo;
        this.userRepo = userRepo;
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
