package com.jobradar.dto;

import com.jobradar.entity.Job;

import java.util.List;

/** 校招信息库分页返回结构（与前端约定一致）。 */
public record JobPageDTO(
        List<Job> content,
        long total,
        int page,
        int size,
        int totalPages,
        boolean capped   // 是否因免费上限被截断（前端据此提示升级）
) {}
