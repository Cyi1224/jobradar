package com.jobradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 访问日志：记录每次 API 请求或前端页面浏览。
 * visitType = "api" 由 VisitLoggingFilter 自动记录，"page" 由前端 ping 上报。
 */
@Entity
@Table(name = "visit_log")
public class VisitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 请求路径，如 /api/jobs、/page-dashboard */
    @Column(nullable = false, length = 512)
    private String path;

    /** HTTP 方法：GET / POST / PUT / DELETE 等 */
    @Column(length = 10)
    private String method;

    /** 客户端 IP（优先取 X-Forwarded-For 首段） */
    @Column(length = 64)
    private String ip;

    /** 浏览器 User-Agent，截断到 512 字符 */
    @Column(length = 512)
    private String userAgent;

    /** Referer 请求头，可为空 */
    @Column(length = 512)
    private String referer;

    /** 登录用户 ID（匿名访问为 null） */
    private Long userId;

    /** 登录用户名（displayName，匿名访问为 null） */
    @Column(length = 32)
    private String username;

    /** SPA 页面名称（仅前端 ping 上报时填充），如 dashboard / jobdb / applications */
    @Column(length = 64)
    private String pageName;

    /** 访问类型：api（后端 API 请求）或 page（前端页面浏览） */
    @Column(nullable = false, length = 10)
    private String visitType;

    private LocalDateTime createdAt = LocalDateTime.now();

    public VisitLog() {}

    public VisitLog(String path, String method, String ip, String userAgent, String referer,
                    Long userId, String username, String visitType) {
        this.path = path;
        this.method = method;
        this.ip = ip;
        this.userAgent = userAgent;
        this.referer = referer;
        this.userId = userId;
        this.username = username;
        this.visitType = visitType;
    }

    // ── getters / setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getReferer() { return referer; }
    public void setReferer(String referer) { this.referer = referer; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }

    public String getVisitType() { return visitType; }
    public void setVisitType(String visitType) { this.visitType = visitType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
