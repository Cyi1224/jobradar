package com.jobradar.dto;

/** 登录 / 注册成功返回：JWT 令牌 + 用户名。 */
public record AuthResp(String token, String username) {}
