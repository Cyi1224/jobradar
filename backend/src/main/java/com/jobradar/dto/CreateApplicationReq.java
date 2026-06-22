package com.jobradar.dto;

import jakarta.validation.constraints.NotBlank;

/** 新增投递请求体（对应前端「添加岗位」表单）。 */
public record CreateApplicationReq(
        @NotBlank(message = "公司名称不能为空") String co,
        @NotBlank(message = "岗位名称不能为空") String pos,
        String type,
        String city,
        String deadline,
        String status,
        String note
) {}
