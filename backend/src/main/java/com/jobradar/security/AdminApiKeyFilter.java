package com.jobradar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Admin API Key 校验过滤器：保护 /api/admin/** 路径。
 * 使用 X-Admin-Key 请求头认证，不走 JWT 用户体系。
 */
@Component
@Order(0)
public class AdminApiKeyFilter extends OncePerRequestFilter {

    @Value("${jobradar.admin.api-key:admin-demo-key-change-me}")
    private String adminApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!req.getRequestURI().startsWith("/api/admin/")) {
            chain.doFilter(req, res);
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String key = req.getHeader("X-Admin-Key");
        if (adminApiKey == null || adminApiKey.isBlank() || !adminApiKey.equals(key)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"invalid admin key\"}");
            return;
        }

        chain.doFilter(req, res);
    }
}
