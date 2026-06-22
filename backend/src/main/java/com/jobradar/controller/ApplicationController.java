package com.jobradar.controller;

import com.jobradar.dto.ApplicationDTO;
import com.jobradar.dto.CreateApplicationReq;
import com.jobradar.dto.UpdateStatusReq;
import com.jobradar.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 投递相关接口。对应前端 data/http.js。
 *   GET    /api/applications
 *   GET    /api/applications/{id}
 *   POST   /api/applications
 *   PATCH  /api/applications/{id}/status
 *   DELETE /api/applications/{id}
 *   GET    /api/applications/stats
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplicationDTO> list() {
        return service.list();
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return service.counts();
    }

    @GetMapping("/{id}")
    public ApplicationDTO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ApplicationDTO> create(@Valid @RequestBody CreateApplicationReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PatchMapping("/{id}/status")
    public ApplicationDTO updateStatus(@PathVariable Long id, @RequestBody UpdateStatusReq req) {
        return service.updateStatus(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
