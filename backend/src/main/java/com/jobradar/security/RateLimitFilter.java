package com.jobradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易固定窗口限流（按 IP）：
 *   - 登录/注册（防暴破）: 20次/15分钟
 *   - 开通会员（防滥用）: 10次/15分钟
 *   - 岗位列表（防爬取）: 60次/分钟
 *   - 全站 API（防刷）: 200次/分钟
 * 单实例内存版；多实例部署请改用 Redis 计数。
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, long[]> hits = new ConcurrentHashMap<>();

    private static final long WINDOW_AUTH  = 15 * 60_000;  // 15 分钟
    private static final long WINDOW_FAST  =  1 * 60_000;  // 1 分钟

    private LimitConfig limitFor(String path) {
        if (path.startsWith("/api/auth/"))
            return new LimitConfig("auth", 20, WINDOW_AUTH);
        if (path.startsWith("/api/membership/subscribe"))
            return new LimitConfig("sub", 10, WINDOW_AUTH);
        if (path.startsWith("/api/jobs"))
            return new LimitConfig("jobs", 60, WINDOW_FAST);
        if (path.startsWith("/api/"))
            return new LimitConfig("api", 200, WINDOW_FAST);
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        LimitConfig cfg = limitFor(req.getRequestURI());
        if (cfg != null && !"OPTIONS".equalsIgnoreCase(req.getMethod())) {
            if (!allow(clientIp(req) + "|" + cfg.group, cfg.max, cfg.windowMs)) {
                res.setStatus(429);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"请求过于频繁，请稍后重试\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private synchronized boolean allow(String key, int limit, long windowMs) {
        long now = System.currentTimeMillis();
        long[] w = hits.computeIfAbsent(key, k -> new long[]{ now, 0 });
        if (now - w[0] > windowMs) { w[0] = now; w[1] = 0; }
        w[1]++;
        return w[1] <= limit;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private record LimitConfig(String group, int max, long windowMs) {}
}
