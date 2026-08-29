package com.jobradar.dto.offerqingbaoju;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * offerqingbaoju /api/simple/navigation/{id}/data 响应。
 * <p>
 * 行数据以中文字段名为键，用 {@link JsonProperty} 显式绑定到 record 组件；
 * 未识别的字段（如 _row_number）通过 ignoreUnknown 忽略。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferqingbaojuDataResponse(
        List<Row> data,
        Pagination pagination,
        boolean success) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(
            @JsonProperty("企业名称") String companyName,
            @JsonProperty("企业性质") String companyNature,
            @JsonProperty("行业") String industry,
            @JsonProperty("招聘批次") String recruitBatch,
            @JsonProperty("毕业年份") String graduationYear,
            @JsonProperty("工作地点") String location,
            @JsonProperty("职位") String position,
            @JsonProperty("截止时间") String deadline,
            @JsonProperty("投递地址") String applyUrl,
            @JsonProperty("公告链接") String announcementUrl,
            @JsonProperty("学历要求") String education,
            @JsonProperty("更新时间") String updateTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagination(
            int page,
            int per_page,
            int total_pages,
            int total_rows,
            boolean has_next,
            boolean has_prev) {
    }
}
