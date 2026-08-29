package com.jobradar.controller;

import com.jobradar.service.AnalyticsService;
import com.jobradar.service.OfferbiuSyncService;
import com.jobradar.service.OfferqingbaojuSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final OfferbiuSyncService offerbiuSyncService;
    private final OfferqingbaojuSyncService offerqingbaojuSyncService;

    public AnalyticsController(AnalyticsService analyticsService,
                               OfferbiuSyncService offerbiuSyncService,
                               OfferqingbaojuSyncService offerqingbaojuSyncService) {
        this.analyticsService = analyticsService;
        this.offerbiuSyncService = offerbiuSyncService;
        this.offerqingbaojuSyncService = offerqingbaojuSyncService;
    }

    /** 汇总卡片数据 */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return analyticsService.summary();
    }

    /** 每日访问量 + 注册量趋势 */
    @GetMapping("/daily")
    public Map<String, Object> daily(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.dailyStats(Math.min(days, 90));
    }

    /** 页面热度排行 */
    @GetMapping("/pages")
    public Map<String, Object> pages(@RequestParam(defaultValue = "7") int days) {
        return analyticsService.pageStats(Math.min(days, 30));
    }

    /** 最近访问记录 */
    @GetMapping("/recent")
    public Map<String, Object> recent(@RequestParam(defaultValue = "20") int limit) {
        return analyticsService.recentVisits(Math.min(limit, 100));
    }

    /** 注册用户列表 */
    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return analyticsService.users(Math.max(page, 0), Math.min(size, 100));
    }

    /** 时段流量分布 */
    @GetMapping("/hourly")
    public Map<String, Object> hourly(@RequestParam(defaultValue = "7") int days) {
        return analyticsService.hourlyStats(Math.min(days, 30));
    }

    /** 流量来源分布 */
    @GetMapping("/sources")
    public Map<String, Object> sources(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.sourceStats(Math.min(days, 90));
    }

    /** 访问地区分布 */
    @GetMapping("/regions")
    public Map<String, Object> regions(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.regionStats(Math.min(days, 90));
    }

    /** 岗位数据库概览 */
    @GetMapping("/jobs-stats")
    public Map<String, Object> jobsStats() {
        return analyticsService.jobsStats();
    }

    /** 会员统计 */
    @GetMapping("/members")
    public Map<String, Object> members() {
        return analyticsService.memberStats();
    }

    /** 用户转化漏斗：独立访客 → 注册用户 → 活跃会员 */
    @GetMapping("/user-funnel")
    public Map<String, Object> userFunnel(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.userFunnel(Math.min(days, 90));
    }

    /** 经常在线用户：近 N 天活跃总数 + Top 榜 */
    @GetMapping("/active-users")
    public Map<String, Object> activeUsers(@RequestParam(defaultValue = "7") int days,
                                           @RequestParam(defaultValue = "20") int limit) {
        return analyticsService.activeUsers(Math.min(days, 90), Math.min(limit, 100));
    }

    /** 会员账户列表：所有开通过会员的用户 + 到期时间（含已过期） */
    @GetMapping("/members-list")
    public Map<String, Object> memberUsers(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "15") int size) {
        return analyticsService.memberUsers(Math.max(page, 0), Math.min(size, 100));
    }

    /** 同步历史 */
    @GetMapping("/sync-history")
    public Map<String, Object> syncHistory(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.syncHistory(Math.min(days, 90));
    }

    /** 每日活跃用户 */
    @GetMapping("/dau")
    public Map<String, Object> dau(@RequestParam(defaultValue = "30") int days) {
        return analyticsService.dailyActiveUsers(Math.min(days, 90));
    }

    /** 前端页面浏览上报 */
    @PostMapping("/ping")
    public ResponseEntity<Void> ping(@RequestBody Map<String, String> body, HttpServletRequest req) {
        String pageName = body.getOrDefault("pageName", "unknown");
        String path = body.getOrDefault("path", req.getRequestURI());
        String ip = clientIp(req);
        String ua = req.getHeader("User-Agent");
        analyticsService.logPageView(pageName, path, ip, ua);
        return ResponseEntity.noContent().build();
    }

    /** 手动触发 offerbiu 数据同步（需 X-Admin-Key） */
    @PostMapping("/sync-offerbiu")
    public ResponseEntity<Map<String, Object>> syncOfferbiu() {
        OfferbiuSyncService.SyncResult result = offerbiuSyncService.syncNow();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "fetched", result.fetched(),
                "inserted", result.inserted(),
                "skipped", result.skipped(),
                "durationSeconds", result.durationSeconds()
        ));
    }

    /** 手动触发 offerqingbaoju 数据同步（需 X-Admin-Key） */
    @PostMapping("/sync-offerqingbaoju")
    public ResponseEntity<Map<String, Object>> syncOfferqingbaoju() {
        OfferqingbaojuSyncService.SyncResult result = offerqingbaojuSyncService.syncNow();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "fetched", result.fetched(),
                "inserted", result.inserted(),
                "skipped", result.skipped(),
                "durationSeconds", result.durationSeconds()
        ));
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
