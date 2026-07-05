package com.jobradar.dto.offerbiu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * offerbiu /api/recruitment/seasons 响应。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferbiuSeasonResponse(
        boolean success,
        String code,
        String message,
        List<SeasonItem> data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeasonItem(
            Integer seasonYear,
            Integer activePostings
    ) {}
}
