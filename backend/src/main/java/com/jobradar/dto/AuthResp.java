package com.jobradar.dto;

/** 登录 / 注册成功返回：JWT 令牌 + 账号 + 显示名。 */
public record AuthResp(String token, String account, String displayName) {}
