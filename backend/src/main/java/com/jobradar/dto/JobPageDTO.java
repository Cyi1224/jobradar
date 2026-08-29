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
        boolean capped,  // 是否因免费上限被截断（前端据此提示升级）
        boolean locked   // 未登录锁定：不返回任何岗位数据（前端显示登录墙）
) {
    /** 未登录访问：空内容 + locked 标记，防止匿名爬取岗位数据。 */
    public static JobPageDTO locked(int page, int size) {
        return new JobPageDTO(List.of(), 0, page, size, 0, false, true);
    }
}
