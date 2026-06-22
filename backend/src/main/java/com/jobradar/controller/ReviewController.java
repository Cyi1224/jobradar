package com.jobradar.controller;

import com.jobradar.dto.ReviewDTO;
import com.jobradar.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 投递复盘：GET /api/review/summary */
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ReviewDTO summary() {
        return service.summary();
    }
}
