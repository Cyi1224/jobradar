package com.jobradar.dto;

import com.jobradar.entity.StatusLog;
import java.time.format.DateTimeFormatter;

/** 时间线节点，time 格式化为前端展示用的 "yyyy-MM-dd HH:mm"。 */
public record StatusLogDTO(String s, String time, String note) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static StatusLogDTO from(StatusLog log) {
        return new StatusLogDTO(
                log.getS(),
                log.getTime() == null ? "" : log.getTime().format(FMT),
                log.getNote() == null ? "" : log.getNote()
        );
    }
}
