package com.jobradar.dto;

import jakarta.validation.constraints.NotBlank;

/** 开通会员请求：套餐 month / quarter / half / year。 */
public record SubscribeReq(@NotBlank String plan) {}
