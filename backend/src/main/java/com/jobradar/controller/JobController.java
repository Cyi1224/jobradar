package com.jobradar.controller;

import com.jobradar.dto.JobPageDTO;
import com.jobradar.service.JobService;
import com.jobradar.service.MembershipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 校招信息库：
 *   GET /api/jobs        分页 + 筛选（page,size,q,recruitType,industry,city,apply,urgent,soe,inst,foreign）
 *   GET /api/jobs/stats  统计卡 + 下拉选项
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService service;
    private final MembershipService membership;

    public JobController(JobService service, MembershipService membership) {
        this.service = service;
        this.membership = membership;
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
            @RequestParam(defaultValue = "false") boolean foreign) {
        boolean unlimited = membership.isCurrentUserMember();   // 会员不限页
        return service.search(q, recruitType, industry, city, apply, urgent, soe, inst, foreign, page, size, unlimited);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }
}
