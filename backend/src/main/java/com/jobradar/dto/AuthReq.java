package com.jobradar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册 / 登录请求体。
 *  注册时需传 account + displayName + password；
 *  登录时只需 account + password。 */
public record AuthReq(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "账号只能包含数字和英文字母")
        @Size(min = 3, max = 32) String account,
        String displayName,
        @NotBlank @Size(min = 6, max = 64) String password
) {}
