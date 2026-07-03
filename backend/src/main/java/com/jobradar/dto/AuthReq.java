package com.jobradar.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册 / 登录请求体。
 *  注册时需传 account + displayName + password；
 *  登录时只需 account + password。
 *  account 字段同时兼容旧前端的 "username" 字段名。 */
public record AuthReq(
        @JsonProperty("account")
        @JsonAlias("username")
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "账号只能包含数字和英文字母")
        @Size(min = 3, max = 32) String account,

        @JsonProperty("displayName")
        String displayName,

        @JsonProperty("password")
        @NotBlank @Size(min = 6, max = 64) String password
) {}
