package com.jobradar.service;

import com.jobradar.dto.RecommendationDTO;
import com.jobradar.repository.RecommendationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class RecommendationService {

    private final RecommendationRepository repo;

    public RecommendationService(RecommendationRepository repo) {
        this.repo = repo;
    }

    /** 默认按匹配度降序；refresh=true 时随机打乱（模拟重新推荐）。 */
    public List<RecommendationDTO> list(boolean refresh) {
        List<RecommendationDTO> all = new ArrayList<>(
                repo.findAll().stream().map(RecommendationDTO::from).toList());
        if (refresh) {
            Collections.shuffle(all);
        } else {
            all.sort(Comparator.comparingInt(RecommendationDTO::match).reversed());
        }
        return all;
    }
}
