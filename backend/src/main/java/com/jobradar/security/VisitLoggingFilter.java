package com.jobradar.security;

import com.jobradar.entity.VisitLog;
import com.jobradar.repository.VisitLogRepository;
import com.jobradar.service.IpRegionService;
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
import java.util.regex.Pattern;

/**
 * 访问日志过滤器：自动记录所有 /api/** 请求到 visit_log 表。
 * 排除 /api/admin/**（避免看板自身刷新污染数据）和 OPTIONS 预检。
 * 日志写入后异步解析 IP 地区和访问来源（不阻塞请求）。
 */
@Component
@Order(2)
public class VisitLoggingFilter extends OncePerRequestFilter {

    private final VisitLogRepository visitLogRepo;
    private final IpRegionService ipRegionService;
    private final SecretKey jwtKey;

    /** 搜索引擎 referer 域名特征 */
    private static final Pattern SEARCH_REFERER = Pattern.compile(
            "(google|baidu|bing|sogou|so\\.com|sm\\.cn)",
            Pattern.CASE_INSENSITIVE);

    public VisitLoggingFilter(VisitLogRepository visitLogRepo,
                              IpRegionService ipRegionService,
                              @Value("${jobradar.jwt.secret:jobradar-dev-secret-change-me-please-32bytes!}") String jwtSecret) {
        this.visitLogRepo = visitLogRepo;
        this.ipRegionService = ipRegionService;
        this.jwtKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean shouldLog = path.startsWith("/api/")
                && !path.startsWith("/api/admin/")
                && !"OPTIONS".equalsIgnoreCase(req.getMethod());

        if (shouldLog) {
            try {
                String ip = clientIp(req);
                String referer = truncate(req.getHeader("Referer"), 512);

                VisitLog log = new VisitLog();
                log.setPath(truncate(path, 512));
                log.setMethod(req.getMethod());
                log.setIp(ip);
                log.setUserAgent(truncate(req.getHeader("User-Agent"), 512));
                log.setReferer(referer);
                log.setVisitType("api");
                log.setSource(detectSource(referer));

                // 尝试解析 JWT
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    try {
                        Jws<Claims> jws = Jwts.parser().verifyWith(jwtKey).build()
                                .parseSignedClaims(auth.substring(7));
                        log.setUserId(Long.valueOf(jws.getPayload().getSubject()));
                        String displayName = jws.getPayload().get("displayName", String.class);
                        log.setUsername(displayName != null ? truncate(displayName, 32) : null);
                    } catch (Exception ignored) { }
                }

                VisitLog saved = visitLogRepo.save(log);

                // 异步解析 IP 地区（不阻塞请求）
                ipRegionService.resolveAsync(ip, region -> {
                    saved.setRegion(region);
                    try { visitLogRepo.save(saved); } catch (Exception ignored) { }
                });

            } catch (Exception ignored) {
                // 日志写入失败不影响正常请求
            }
        }

        chain.doFilter(req, res);
    }

    /** 根据 referer 判断访问来源 */
    private String detectSource(String referer) {
        if (referer == null || referer.isBlank()) return "直接访问";
        if (SEARCH_REFERER.matcher(referer).find()) return "搜索引擎";
        return "外部链接";
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
