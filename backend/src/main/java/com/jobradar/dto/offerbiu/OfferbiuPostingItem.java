package com.jobradar.dto.offerbiu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * offerbiu /api/recruitment/postings 单条岗位记录。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferbiuPostingItem(
        String id,
        String companyId,
        Integer seasonYear,
        String companyName,
        String companyNature,
        String industry,
        String recruitType,

        @JsonProperty("targetYears")
        List<Integer> targetYears,

        List<String> locations,
        String positionsText,
        String deadlineType,
        String deadlineAt,
        String deadlineText,
        String announcementUrl,
        String applyUrl,
        String applyText,
        String examPolicy,
        String noteText,
        String visibilityTier,
        String status,
        String sourceUpdatedAt
) {}
