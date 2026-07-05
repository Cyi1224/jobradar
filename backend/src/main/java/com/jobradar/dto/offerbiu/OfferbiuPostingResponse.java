package com.jobradar.dto.offerbiu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * offerbiu /api/recruitment/postings 分页响应。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferbiuPostingResponse(
        boolean success,
        String code,
        String message,
        OfferbiuPostingData data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfferbiuPostingData(
            List<OfferbiuPostingItem> items,
            Integer totalPages,
            Long totalElements,
            Integer number,
            Integer size
    ) {}
}
