package com.jobradar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobradar.entity.ResumeDoc;
import com.jobradar.repository.ResumeDocRepository;
import com.jobradar.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 简历编辑器文档：按当前登录用户读写（多设备打开即同步最新）。 */
@Service
public class ResumeDocService {

    private final ResumeDocRepository repo;
    private final ObjectMapper om;

    public ResumeDocService(ResumeDocRepository repo, ObjectMapper om) {
        this.repo = repo;
        this.om = om;
    }

    /** 当前用户已保存的简历文档；无则返回 null。 */
    @Transactional(readOnly = true)
    public JsonNode get() {
        Long uid = UserContext.require();
        return repo.findByUserId(uid)
                .map(d -> {
                    try { return om.readTree(d.getContent()); }
                    catch (Exception e) { return (JsonNode) null; }
                })
                .orElse(null);
    }

    /** upsert 当前用户的简历文档。 */
    @Transactional
    public void save(JsonNode content) {
        Long uid = UserContext.require();
        ResumeDoc d = repo.findByUserId(uid).orElseGet(() -> {
            ResumeDoc n = new ResumeDoc();
            n.setUserId(uid);
            return n;
        });
        d.setContent(content == null ? "{}" : content.toString());
        d.setUpdatedAt(LocalDateTime.now());
        repo.save(d);
    }
}
