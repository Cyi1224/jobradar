package com.jobradar.dto;

import com.jobradar.entity.Application;
import java.util.List;

/** 返回给前端的投递结构，字段与前端 data 层完全一致。 */
public record ApplicationDTO(
        Long id,
        String co,
        String pos,
        String type,
        String city,
        String deadline,
        String status,
        String note,
        List<StatusLogDTO> logs
) {
    public static ApplicationDTO from(Application a) {
        return new ApplicationDTO(
                a.getId(), a.getCo(), a.getPos(), a.getType(), a.getCity(),
                a.getDeadline(), a.getStatus(), a.getNote() == null ? "" : a.getNote(),
                a.getLogs().stream().map(StatusLogDTO::from).toList()
        );
    }
}
