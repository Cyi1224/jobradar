package com.jobradar.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobradar.service.ResumeDocService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 简历编辑器文档（需登录，按用户隔离）：
 *   GET /api/resume-doc   取当前用户简历文档（无则 204）
 *   PUT /api/resume-doc   保存/覆盖当前用户简历文档
 */
@RestController
@RequestMapping("/api/resume-doc")
public class ResumeDocController {

    private final ResumeDocService service;

    public ResumeDocController(ResumeDocService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<JsonNode> get() {
        JsonNode doc = service.get();
        return doc == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(doc);
    }

    @PutMapping
    public ResponseEntity<Void> save(@RequestBody JsonNode body) {
        service.save(body);
        return ResponseEntity.noContent().build();
    }
}
