package com.jobradar.controller;

import com.jobradar.dto.MembershipDTO;
import com.jobradar.dto.SubscribeReq;
import com.jobradar.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 付费会员：
 *   GET  /api/membership            当前会员状态
 *   POST /api/membership/subscribe  开通/续费 { plan }
 * 均需登录（JwtAuthFilter 保护 /api/** 中非 jobs/auth 的路径）。
 */
@RestController
@RequestMapping("/api/membership")
public class MembershipController {

    private final MembershipService service;
    private final boolean demoMode;

    public MembershipController(MembershipService service,
                               @Value("${jobradar.payment.demo-mode:true}") boolean demoMode) {
        this.service = service;
        this.demoMode = demoMode;
    }

    @GetMapping
    public MembershipDTO status() {
        return service.status();
    }

    /**
     * 开通会员。
     * 演示模式（dev）：点击即开通，便于联调。
     * 生产（demo-mode=false）：禁止前端直接开通——会员只能由真实支付回调授予，
     * 这里返回 403，引导走支付。（前端不可信，价格与授权都在服务端。）
     */
    @PostMapping("/subscribe")
    public MembershipDTO subscribe(@Valid @RequestBody SubscribeReq req) {
        if (!demoMode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "请通过支付开通会员（演示开通已在生产环境关闭）");
        }
        return service.subscribe(req.plan());
    }
}
