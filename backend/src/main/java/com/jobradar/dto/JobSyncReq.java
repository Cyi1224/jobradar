package com.jobradar.dto;

/** 每日同步脚本 POST /api/jobs/sync 的请求体单条记录（字段与 Job 实体及 jobs.json 保持一致）。 */
public record JobSyncReq(
        String co,
        String coType,
        String industry,
        String recruitType,
        String target,
        String city,
        String positions,
        String updatedAt,
        String deadline,
        String applyUrl,
        String announceUrl,
        String note
) {}
