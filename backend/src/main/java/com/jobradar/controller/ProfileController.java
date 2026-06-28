package com.jobradar.controller;

import com.jobradar.entity.Profile;
import com.jobradar.service.ProfileService;
import org.springframework.web.bind.annotation.*;

/** 用户资料：GET /api/profile   PUT /api/profile */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public Profile get() {
        return service.get();
    }

    @PutMapping
    public Profile save(@RequestBody Profile body) {
        return service.save(body);
    }
}
