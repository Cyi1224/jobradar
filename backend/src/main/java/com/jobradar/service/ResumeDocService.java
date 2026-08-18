package com.jobradar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobradar.entity.ResumeDoc;
import com.jobradar.repository.ResumeDocRepository;
import com.jobradar.security.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/** 简历编辑器文档：按当前登录用户读写（多设备打开即同步最新）。 */
@Service
public class ResumeDocService {

    private final ResumeDocRepository repo;
    private final ObjectMapper om;
    private final MembershipService membershipService;

    public ResumeDocService(ResumeDocRepository repo, ObjectMapper om, MembershipService membershipService) {
        this.repo = repo;
        this.om = om;
        this.membershipService = membershipService;
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

    /** upsert 当前用户的简历文档。非会员拒绝保存（简历为会员功能）。 */
    @Transactional
    public void save(JsonNode content) {
        Long uid = UserContext.require();
        if (!membershipService.isCurrentUserMember()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "保存简历需开通会员");
        }
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
