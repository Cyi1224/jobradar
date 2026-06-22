package com.jobradar.dto;

import com.jobradar.entity.Recommendation;
import java.util.List;

/** 推荐岗位：matchScore 在此映射为前端期望的 "match"。 */
public record RecommendationDTO(
        Long id,
        String co,
        String pos,
        String city,
        String target,
        String recruitType,
        String deadline,
        List<String> tags,
        int match
) {
    public static RecommendationDTO from(Recommendation r) {
        return new RecommendationDTO(
                r.getId(), r.getCo(), r.getPos(), r.getCity(), r.getTarget(),
                r.getRecruitType(), r.getDeadline(), r.getTags(), r.getMatchScore()
        );
    }
}
