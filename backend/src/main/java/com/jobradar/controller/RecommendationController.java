package com.jobradar.controller;

import com.jobradar.dto.RecommendationDTO;
import com.jobradar.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** AI 推荐：GET /api/recommendations[?refresh=1] */
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecommendationDTO> list(@RequestParam(required = false) Integer refresh) {
        return service.list(refresh != null && refresh == 1);
    }
}
