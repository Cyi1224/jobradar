package com.jobradar.security;

import com.jobradar.entity.VisitLog;
import com.jobradar.repository.VisitLogRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 访问日志过滤器：自动记录所有 /api/** 请求到 visit_log 表。
 * 排除 /api/admin/**（避免看板自身刷新污染数据）和 OPTIONS 预检。
 * 日志写入失败不影响正常请求。
 */
@Component
@Order(2)
public class VisitLoggingFilter extends OncePerRequestFilter {

    private final VisitLogRepository visitLogRepo;
    private final SecretKey jwtKey;

    public VisitLoggingFilter(VisitLogRepository visitLogRepo,
                              @Value("${jobradar.jwt.secret:jobradar-dev-secret-change-me-please-32bytes!}") String jwtSecret) {
        this.visitLogRepo = visitLogRepo;
        this.jwtKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        // 只记录 /api/** 请求，排除 admin 自身和 OPTIONS
        String path = req.getRequestURI();
        boolean shouldLog = path.startsWith("/api/")
                && !path.startsWith("/api/admin/")
                && !"OPTIONS".equalsIgnoreCase(req.getMethod());

        if (shouldLog) {
            try {
                VisitLog log = new VisitLog();
                log.setPath(truncate(path, 512));
                log.setMethod(req.getMethod());
                log.setIp(clientIp(req));
                log.setUserAgent(truncate(req.getHeader("User-Agent"), 512));
                log.setReferer(truncate(req.getHeader("Referer"), 512));
                log.setVisitType("api");

                // 尝试解析 JWT 获取用户信息（不强制要求登录）
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    try {
                        Jws<Claims> jws = Jwts.parser().verifyWith(jwtKey).build()
                                .parseSignedClaims(auth.substring(7));
                        log.setUserId(Long.valueOf(jws.getPayload().getSubject()));
                        String displayName = jws.getPayload().get("displayName", String.class);
                        log.setUsername(displayName != null ? truncate(displayName, 32) : null);
                    } catch (Exception ignored) {
                        // 令牌无效视为匿名访问
                    }
                }

                visitLogRepo.save(log);
            } catch (Exception ignored) {
                // 日志写入失败不影响正常请求
            }
        }

        chain.doFilter(req, res);
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
