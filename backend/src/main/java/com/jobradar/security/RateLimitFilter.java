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
 * 简易固定窗口限流（按 IP）：保护登录/注册（防暴破）与开通会员（防滥用）。
 * 单实例内存版；多实例部署请改用 Redis 计数。
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 15 * 60 * 1000; // 15 分钟
    private final Map<String, long[]> hits = new ConcurrentHashMap<>(); // key -> [windowStart, count]

    private int limitFor(String path) {
        if (path.startsWith("/api/auth/")) return 20;                  // 登录/注册：15 分钟 20 次
        if (path.startsWith("/api/membership/subscribe")) return 10;   // 开通会员：15 分钟 10 次
        return -1;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        int limit = limitFor(req.getRequestURI());
        if (limit > 0 && !"OPTIONS".equalsIgnoreCase(req.getMethod())) {
            String group = req.getRequestURI().startsWith("/api/auth/") ? "auth" : "sub";
            if (!allow(clientIp(req) + "|" + group, limit)) {
                res.setStatus(429);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"message\":\"请求过于频繁，请稍后重试\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private synchronized boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        long[] w = hits.computeIfAbsent(key, k -> new long[]{ now, 0 });
        if (now - w[0] > WINDOW_MS) { w[0] = now; w[1] = 0; }
        w[1]++;
        return w[1] <= limit;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
