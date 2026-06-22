package com.jobradar.dto;

import java.util.List;

/** 投递复盘分析结果，结构与前端 data/review.js 的 summary 一致。 */
public record ReviewDTO(
        Stages stages,
        List<Rate> rates,
        List<Dist> distribution,
        List<String> suggestions
) {
    public record Stages(int total, int submitted, int exam, int interview, int oc) {}
    public record Rate(String label, String cls, double value, String note) {}
    public record Dist(String label, int count, int width, String color) {}
}
