package com.jobradar;

import com.jobradar.dto.ApplicationDTO;
import com.jobradar.dto.CreateApplicationReq;
import com.jobradar.dto.UpdateStatusReq;
import com.jobradar.service.ApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 业务逻辑测试：验证状态流转与时间线生成规则（与前端 mock 一致）。
 */
@SpringBootTest
class ApplicationServiceTest {

    @Autowired
    private ApplicationService service;

    @Test
    void create_withSubmittedStatus_autoAddsOneLog() {
        ApplicationDTO dto = service.create(
                new CreateApplicationReq("甲公司", "后端", "秋招", "上海", "2026-09-01", "已投递", "备注"));
        assertEquals("已投递", dto.status());
        assertEquals(1, dto.logs().size());
        assertEquals("已投递", dto.logs().get(0).s());
    }

    @Test
    void create_withUnsubmitted_hasNoLog() {
        ApplicationDTO dto = service.create(
                new CreateApplicationReq("乙公司", "前端", null, null, null, "未投递", null));
        assertEquals("未投递", dto.status());
        assertTrue(dto.logs().isEmpty());
        assertEquals("招满为止", dto.deadline());   // 默认值
    }

    @Test
    void updateStatus_transition_appendsLogWithSummary() {
        ApplicationDTO created = service.create(
                new CreateApplicationReq("丙公司", "算法", "秋招", "北京", "2026-09-10", "未投递", ""));

        ApplicationDTO updated = service.updateStatus(created.id(),
                new UpdateStatusReq("已笔试", Map.of("exam-note", "两道编程题，通过")));

        assertEquals("已笔试", updated.status());
        assertTrue(updated.logs().stream().anyMatch(l -> "已笔试".equals(l.s())));
        assertTrue(updated.logs().stream().anyMatch(l -> "两道编程题，通过".equals(l.note())));
    }

    @Test
    void counts_containsAllStatusKeys() {
        Map<String, Long> counts = service.counts();
        assertTrue(counts.containsKey("全部"));
        assertTrue(counts.containsKey("已OC"));
        assertTrue(counts.get("全部") >= 8);   // 至少包含种子数据
    }
}
