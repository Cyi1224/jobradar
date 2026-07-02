package com.jobradar.controller;

import com.jobradar.dto.AuthReq;
import com.jobradar.dto.AuthResp;
import com.jobradar.entity.User;
import com.jobradar.repository.UserRepository;
import com.jobradar.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户系统：
 *   POST /api/auth/register  注册 { account, displayName, password } → { token, account, displayName }
 *   POST /api/auth/login     登录 { account, password } → { token, account, displayName }
 * 这两个接口在 JwtAuthFilter 中放行，其余 /api/** 均需带 Bearer 令牌。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final JwtUtil jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository users, JwtUtil jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public AuthResp register(@Valid @RequestBody AuthReq req) {
        // 注册时 displayName 必填，长度不超过 15
        String dn = req.displayName();
        if (dn == null || dn.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (dn.length() > 15) {
            throw new IllegalArgumentException("用户名长度不能超过 15 位");
        }
        if (users.existsByAccount(req.account())) {
            throw new IllegalArgumentException("账号已被占用");
        }
        User u = users.save(new User(req.account(), dn.trim(), encoder.encode(req.password())));
        return new AuthResp(jwt.issue(u.getId(), u.getAccount(), u.getDisplayName()), u.getAccount(), u.getDisplayName());
    }

    @PostMapping("/login")
    public AuthResp login(@Valid @RequestBody AuthReq req) {
        User u = users.findByAccount(req.account())
                .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        // displayName 可能为 null（历史用户），给个兜底值
        String dn = u.getDisplayName() != null ? u.getDisplayName() : u.getAccount();
        return new AuthResp(jwt.issue(u.getId(), u.getAccount(), dn), u.getAccount(), dn);
    }
}
