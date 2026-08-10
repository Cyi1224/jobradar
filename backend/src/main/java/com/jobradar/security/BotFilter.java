package com.jobradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 反爬虫/反扫描过滤器（Order 0，最先执行）。
 * 拦截规则：
 *   1. 空/缺失 User-Agent → 403
 *   2. 已知恶意 UA → 403
 *   3. 漏洞扫描路径 → 403 + 立即拉黑
 *   4. 高频请求（同 IP 10 秒 > 60 次） → 3 次犯规后拉黑 5 分钟
 */
@Component
@Order(0)
public class BotFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BotFilter.class);

    /** 恶意 User-Agent 特征（小写匹配） */
    private static final Set<String> BAD_UA_PATTERNS = Set.of(
            "curl", "wget", "python", "go-http", "scrap", "spider",
            "scan", "nmap", "nikto", "sqlmap", "masscan", "zgrab",
            "axios", "node-fetch", "okhttp", "apache-httpclient",
            "java/", "libwww", "perl", "ruby"
    );

    /** 漏洞扫描路径特征 */
    private static final Pattern SCAN_PATH = Pattern.compile(
            "(?i)(/wp-admin|/wp-login|/phpmyadmin|/admin\\.php|/\\.env|\\.git|/\\.git" +
            "|/actuator|/swagger|/api-docs|/config\\.json|/backup|/sql|/dump" +
            "|/owa/auth|/ecp/|/autodiscover|\\.aspx|\\.asp\\.|/solr|/jenkins" +
            "|/cgi-bin|/boaform|/HNAP1|/tmUnblock|/GponForm|/vendor/phpunit)");

    /** 白名单路径：静态资源不在过滤范围 */
    private static final Pattern STATIC_RESOURCE = Pattern.compile(
            "\\.(css|js|woff2?|ttf|svg|png|jpe?g|webp|ico|map)(\\?.*)?$");

    /** IP 请求计数: IP → [窗口起始时间戳, 计数] */
    private final Map<String, long[]> ipHitCounts = new ConcurrentHashMap<>();
    /** IP 临时黑名单: IP → 解封时间戳 */
    private final Map<String, Long> ipBlockUntil = new ConcurrentHashMap<>();
    /** IP 高频犯规计数: IP → [计数窗口起始时间戳, 犯规次数] */
    private final Map<String, long[]> ipStrikeCounts = new ConcurrentHashMap<>();

    private static final long HIT_WINDOW_MS = 10_000;    // 10 秒窗口
    private static final int  HIT_MAX      = 60;          // 窗口内最大请求数（SPA 页面正常会并发多个 API）
    private static final int  STRIKE_LIMIT = 3;           // 连续犯规次数阈值
    private static final long STRIKE_WINDOW_MS = 5 * 60_000; // 犯规计数重置窗口
    private static final long BLOCK_MS     = 5 * 60_000;  // 封禁 5 分钟

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        String ip   = clientIp(req);
        String ua   = req.getHeader("User-Agent");

        // 跳过静态资源
        if (STATIC_RESOURCE.matcher(path).find()) {
            chain.doFilter(req, res);
            return;
        }

        // 注册/登录接口放行（已有 RateLimitFilter 限流保护）
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(req, res);
            return;
        }

        // 1. 检查是否在黑名单中
        Long unblockAt = ipBlockUntil.get(ip);
        if (unblockAt != null) {
            if (System.currentTimeMillis() < unblockAt) {
                reject(res, 403, "IP 已被临时封禁，请稍后再试");
                return;
            }
            ipBlockUntil.remove(ip);
        }

        // 2. 空/缺失 User-Agent
        if (ua == null || ua.isBlank()) {
            logBlock(ip, path, "空 UA");
            reject(res, 403, "请求缺少 User-Agent");
            return;
        }

        // 3. 已知恶意 UA
        String uaLower = ua.toLowerCase();
        for (String bad : BAD_UA_PATTERNS) {
            if (uaLower.contains(bad)) {
                logBlock(ip, path, "恶意 UA: " + bad);
                reject(res, 403, "非法请求");
                return;
            }
        }

        // 4. 漏洞扫描路径
        if (SCAN_PATH.matcher(path).find()) {
            logBlock(ip, path, "扫描路径");
            ipBlockUntil.put(ip, System.currentTimeMillis() + BLOCK_MS);
            reject(res, 403, "非法路径");
            return;
        }

        // 5. 高频请求检测（3 次犯规才拉黑，避免 SPA 正常并发被误伤）
        if (isHighFrequency(ip)) {
            int strikes = recordStrike(ip);
            if (strikes >= STRIKE_LIMIT) {
                logBlock(ip, path, "高频请求(第" + strikes + "次犯规，已拉黑)");
                ipBlockUntil.put(ip, System.currentTimeMillis() + BLOCK_MS);
                reject(res, 403, "请求过于频繁，IP 已被临时封禁，请稍后再试");
                return;
            }
            logBlock(ip, path, "高频请求(第" + strikes + "次警告)");
            reject(res, 429, "请求过于频繁，请稍后重试");
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isHighFrequency(String ip) {
        long now = System.currentTimeMillis();
        long[] w = ipHitCounts.computeIfAbsent(ip, k -> new long[]{ now, 0 });
        synchronized (w) {
            if (now - w[0] > HIT_WINDOW_MS) { w[0] = now; w[1] = 0; }
            w[1]++;
            return w[1] > HIT_MAX;
        }
    }

    /** 记录一次高频犯规，返回该 IP 在当前窗口内的犯规次数 */
    private int recordStrike(String ip) {
        long now = System.currentTimeMillis();
        long[] s = ipStrikeCounts.computeIfAbsent(ip, k -> new long[]{ now, 0 });
        synchronized (s) {
            if (now - s[0] > STRIKE_WINDOW_MS) { s[0] = now; s[1] = 0; }
            s[1]++;
            return (int) s[1];
        }
    }

    private void logBlock(String ip, String path, String reason) {
        log.warn("[反爬] IP={} 路径={} 原因={}", ip, truncate(path, 100), reason);
    }

    private void reject(HttpServletResponse res, int status, String reason) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"访问被拒绝\",\"reason\":\"" + reason + "\"}");
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
