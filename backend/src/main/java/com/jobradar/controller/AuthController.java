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
 *   POST /api/auth/register  注册 { username, password } → { token, username }
 *   POST /api/auth/login     登录 { username, password } → { token, username }
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
        if (users.existsByUsername(req.username())) {
            throw new IllegalArgumentException("用户名已被占用");
        }
        User u = users.save(new User(req.username(), encoder.encode(req.password())));
        return new AuthResp(jwt.issue(u.getId(), u.getUsername()), u.getUsername());
    }

    @PostMapping("/login")
    public AuthResp login(@Valid @RequestBody AuthReq req) {
        User u = users.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return new AuthResp(jwt.issue(u.getId(), u.getUsername()), u.getUsername());
    }
}
