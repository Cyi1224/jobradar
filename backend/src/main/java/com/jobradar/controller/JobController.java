package com.jobradar.controller;

import com.jobradar.dto.JobPageDTO;
import com.jobradar.dto.JobSyncReq;
import com.jobradar.security.UserContext;
import com.jobradar.service.JobService;
import com.jobradar.service.MembershipService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 校招信息库：
 *   GET  /api/jobs        分页 + 筛选（page,size,q,recruitType,industry,city,apply,urgent,soe,inst,foreign）
 *   GET  /api/jobs/stats  统计卡 + 下拉选项
 *   POST /api/jobs/sync   每日同步脚本热推送增量（需 X-Sync-Token header）
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService service;
    private final MembershipService membershipService;
    private final String syncToken;

    public JobController(JobService service,
                         MembershipService membershipService,
                         @Value("${jobradar.sync-token:changeme}") String syncToken) {
        this.service = service;
        this.membershipService = membershipService;
        this.syncToken = syncToken;
    }

    @GetMapping
    public JobPageDTO list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String recruitType,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "false") boolean apply,
            @RequestParam(defaultValue = "false") boolean urgent,
            @RequestParam(defaultValue = "false") boolean soe,
            @RequestParam(defaultValue = "false") boolean inst,
            @RequestParam(defaultValue = "false") boolean foreign,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String updatedAt) {
        // 未登录：不返回任何岗位数据（防匿名爬取），前端显示登录墙
        if (UserContext.get() == null) {
            return JobPageDTO.locked(page, size);
        }
        // 会员无限浏览；非会员（含登录的免费用户）仅放行前 5 页
        boolean unlimited = membershipService.isCurrentUserMember();
        return service.search(q, recruitType, industry, city, target, apply, urgent, soe, inst, foreign, updatedAt, page, size, unlimited);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    /** 每日同步脚本调用：批量 upsert 增量校招条目。需携带 X-Sync-Token header。 */
    @PostMapping("/sync")
    public ResponseEntity<?> sync(
            @RequestBody List<JobSyncReq> jobs,
            @RequestHeader(value = "X-Sync-Token", required = false) String token) {
        if (!syncToken.equals(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "无效的 sync token"));
        }
        Map<String, Integer> result = service.insertNewJobs(jobs);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "inserted", result.get("inserted"),
                "skipped", result.get("skipped"),
                "total", jobs.size()
        ));
    }
}
