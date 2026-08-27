package com.jobradar.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员登录：账号+密码校验通过后返回 Admin API Key（后续请求用 X-Admin-Key）。
 * 凭据通过环境变量 ADMIN_ACCOUNT / ADMIN_PASSWORD 配置（默认 2426155413 / 123456）。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final String adminAccount;
    private final String adminPassword;
    private final String adminApiKey;

    public AdminAuthController(
            @Value("${jobradar.admin.account:2426155413}") String adminAccount,
            @Value("${jobradar.admin.password:123456}") String adminPassword,
            @Value("${jobradar.admin.api-key:admin-demo-key-change-me}") String adminApiKey) {
        this.adminAccount = adminAccount;
        this.adminPassword = adminPassword;
        this.adminApiKey = adminApiKey;
    }

    /** 管理员登录 → { code=1, key, account }；失败 → { code=0, msg } */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String account = body.getOrDefault("account", "");
        String password = body.getOrDefault("password", "");
        if (!adminAccount.equals(account) || !adminPassword.equals(password)) {
            return Map.of("code", 0, "msg", "账号或密码错误");
        }
        return Map.of("code", 1, "key", adminApiKey, "account", account);
    }
}
