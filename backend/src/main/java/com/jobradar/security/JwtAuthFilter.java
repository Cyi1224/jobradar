package com.jobradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 鉴权过滤器：保护 /api/**（放行 /api/auth/** 与 CORS 预检 OPTIONS）。
 * 从 Authorization: Bearer <token> 解析出 userId 存入 UserContext，供 Service 做数据隔离。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwt;

    public JwtAuthFilter(JwtUtil jwt) {
        this.jwt = jwt;
    }

    private boolean needsAuth(HttpServletRequest req) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return false;
        String path = req.getRequestURI();
        if (!path.startsWith("/api/")) return false;
        if (path.startsWith("/api/auth/")) return false;
        if (path.startsWith("/api/jobs")) return false;   // 校招信息库公开：匿名可浏览（前 5 页）
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (!"OPTIONS".equalsIgnoreCase(req.getMethod())) {
                // 只要带了有效令牌就解析出用户（公开接口如 /api/jobs 也借此识别会员）
                Long userId = null;
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    userId = jwt.parseUserId(auth.substring(7));
                }
                if (userId != null) UserContext.set(userId);

                // 受保护接口（非 auth / 非 jobs）必须有有效令牌
                if (needsAuth(req) && userId == null) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"未登录或登录已过期\"}");
                    return;
                }
            }
            chain.doFilter(req, res);
        } finally {
            UserContext.clear();
        }
    }
}
