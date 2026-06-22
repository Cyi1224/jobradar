package com.jobradar.service;

import com.jobradar.common.ResourceNotFoundException;
import com.jobradar.dto.ApplicationDTO;
import com.jobradar.dto.CreateApplicationReq;
import com.jobradar.dto.UpdateStatusReq;
import com.jobradar.entity.Application;
import com.jobradar.entity.StatusLog;
import com.jobradar.repository.ApplicationRepository;
import com.jobradar.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投递业务逻辑。全部按当前登录用户（UserContext）隔离：
 * 每个用户只能看到/改动自己的投递。时间戳由服务端生成。
 */
@Service
public class ApplicationService {

    private final ApplicationRepository repo;

    public ApplicationService(ApplicationRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ApplicationDTO> list() {
        return repo.findByUserIdOrderByIdDesc(UserContext.require()).stream().map(ApplicationDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDTO get(Long id) {
        return ApplicationDTO.from(find(id));
    }

    @Transactional
    public ApplicationDTO create(CreateApplicationReq req) {
        Application a = new Application();
        a.setUserId(UserContext.require());
        a.setCo(req.co());
        a.setPos(req.pos());
        a.setType(orDefault(req.type(), "秋招"));
        a.setCity(orDefault(req.city(), "—"));
        a.setDeadline(orDefault(req.deadline(), "招满为止"));
        a.setStatus(orDefault(req.status(), "未投递"));
        a.setNote(req.note() == null ? "" : req.note());

        if (!"未投递".equals(a.getStatus())) {
            a.addLog(new StatusLog(a.getStatus(), LocalDateTime.now(), ""));
        }
        return ApplicationDTO.from(repo.save(a));
    }

    @Transactional
    public ApplicationDTO updateStatus(Long id, UpdateStatusReq req) {
        Application a = find(id);
        String prev = a.getStatus();
        String next = req.status();
        a.setStatus(next);

        Map<String, String> f = req.fields() == null ? Map.of() : req.fields();
        if (f.containsKey("note")) {
            a.setNote(f.get("note"));
        }

        if (prev != null && !prev.equals(next)) {
            String logNote = "";
            if (notBlank(f.get("exam-note")))  logNote = f.get("exam-note");
            if (notBlank(f.get("int-note")))   logNote = f.get("int-note");
            if (notBlank(f.get("oc-salary")))  logNote = "薪资：" + f.get("oc-salary");
            if (notBlank(f.get("fail-stage"))) logNote = "挂在" + f.get("fail-stage");
            a.addLog(new StatusLog(next, LocalDateTime.now(), logNote));
        }
        return ApplicationDTO.from(repo.save(a));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsByIdAndUserId(id, UserContext.require())) {
            throw new ResourceNotFoundException("投递记录不存在：" + id);
        }
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Long uid = UserContext.require();
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("全部", repo.countByUserId(uid));
        for (String s : List.of("未投递", "已投递", "已笔试", "已面试", "已挂", "已OC")) {
            out.put(s, repo.countByUserIdAndStatus(uid, s));
        }
        return out;
    }

    // ── helpers ──
    private Application find(Long id) {
        return repo.findByIdAndUserId(id, UserContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("投递记录不存在：" + id));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
