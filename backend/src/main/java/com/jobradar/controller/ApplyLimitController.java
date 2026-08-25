package com.jobradar.controller;

import com.jobradar.security.UserContext;
import com.jobradar.service.MembershipService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 免费投递次数（反薅羊毛）：
 *   GET  /api/apply-limit/check  查询当前 IP 今日已用次数 + 是否可用
 *   POST /api/apply-limit/use    消耗一次（IP 级计数）
 * 计数键：IP + 日期，换账号/换设备均共享同一 IP 额度。
 * 游客 3 次/日，登录非会员 5 次/日，会员不限制。
 */
@RestController
@RequestMapping("/api/apply-limit")
public class ApplyLimitController {

    private final MembershipService membershipService;

    /** IP+日期 → 已用次数 */
    private final Map<String, Integer> counters = new ConcurrentHashMap<>();

    public ApplyLimitController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/check")
    public Map<String, Object> check(HttpServletRequest req) {
        int limit = limitFor();
        String key = key(req);
        int used = counters.getOrDefault(key, 0);
        boolean allowed = limit < 0 || used < limit;
        return Map.of(
                "used", used,
                "limit", limit < 0 ? -1 : limit,
                "allowed", allowed
        );
    }

    @PostMapping("/use")
    public Map<String, Object> use(HttpServletRequest req) {
        int limit = limitFor();
        if (limit < 0) return Map.of("used", -1, "allowed", true);  // 会员无限
        String key = key(req);
        int used = counters.merge(key, 1, Integer::sum);
        return Map.of("used", used, "limit", limit, "allowed", used < limit);
    }

    /** 会员不限；登录非会员 3；游客 3。 */
    private int limitFor() {
        Long uid = UserContext.get();
        if (uid == null) return 3;                       // 未登录
        if (membershipService.isCurrentUserMember()) return -1;  // 会员不限制
        return 3;                                        // 登录非会员 3 次
    }

    private String key(HttpServletRequest req) {
        return clientIp(req) + ":" + LocalDate.now();
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
