package com.jobradar.dto;

/** 会员状态：是否会员、到期时间、剩余天数、当前套餐展示名。 */
public record MembershipDTO(boolean member, String memberUntil, long daysLeft, String plan) {}
