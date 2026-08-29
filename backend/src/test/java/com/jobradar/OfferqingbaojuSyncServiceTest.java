package com.jobradar;

import com.jobradar.dto.JobSyncReq;
import com.jobradar.entity.Job;
import com.jobradar.repository.JobRepository;
import com.jobradar.service.JobService;
import com.jobradar.service.OfferqingbaojuSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * offerqingbaoju 同步冒烟测试：直调真实接口抓取最新批次 → 映射 → 去重入库。
 * <p>
 * 整个测试处于事务中，结束时整体回滚，不污染 dev 库。
 * 需要联网访问 https://offerqingbaoju.cn（公开接口，无需登录）。
 */
@SpringBootTest
@Transactional
class OfferqingbaojuSyncServiceTest {

    @Autowired
    private OfferqingbaojuSyncService service;

    @Autowired
    private JobRepository jobRepo;

    @Autowired
    private JobService jobService;

    @Test
    void syncNow_fetchesNewestAndInsertsMappedJobs() {
        // 同步前已有记录的业务主键集合，用于精确识别本次新入库的行
        Set<String> beforeKeys = new HashSet<>();
        for (Job j : jobRepo.findAll()) {
            beforeKeys.add(bizKey(j));
        }
        long before = jobRepo.count();

        OfferqingbaojuSyncService.SyncResult result = service.syncNow();

        // 抓取到数据，且成功写入
        assertTrue(result.fetched() > 0, "应抓取到源站数据");
        assertTrue(result.inserted() > 0, "应有新记录入库");
        assertEquals(result.inserted(), jobRepo.count() - before,
                "入库条数应与 count 增长一致（事务内可见）");

        // 本次同步新入库的行（不在同步前的业务主键集合中）
        List<Job> synced = jobRepo.findAll().stream()
                .filter(j -> !beforeKeys.contains(bizKey(j)))
                .toList();
        assertEquals(result.inserted(), synced.size());

        // ── 映射正确性校验 ──
        assertFalse(synced.isEmpty());
        assertTrue(synced.stream().allMatch(j -> "秋招".equals(j.getRecruitType())),
                "最新批次应均为秋招");
        assertTrue(synced.stream().allMatch(j -> j.getTarget() != null && j.getTarget().contains("2027届")),
                "毕业年份应映射为 2027届");
        // 投递地址：绝大多数为 http 链接；个别源站行无直接投递链接（为空但公告链接有效），如实透传
        assertTrue(synced.stream().allMatch(j -> j.getApplyUrl() == null || j.getApplyUrl().isBlank()
                        || j.getApplyUrl().startsWith("http")),
                "投递地址应为空或完整链接");
        assertTrue(synced.stream().allMatch(j -> j.getAnnounceUrl() != null
                        && j.getAnnounceUrl().startsWith("http")),
                "公告链接应为完整链接");
        assertTrue(synced.stream().allMatch(j -> j.getCo() != null && !j.getCo().isBlank()),
                "企业名称不应为空");
        // updatedAt 应透传源站更新时间，为近期合法 ISO 日期
        assertTrue(synced.stream().allMatch(j -> j.getUpdatedAt() != null
                        && j.getUpdatedAt().matches("\\d{4}-\\d{2}-\\d{2}")),
                "updatedAt 应为 YYYY-MM-DD");
        LocalDate oldest = LocalDate.now().minusDays(30);
        assertTrue(synced.stream().allMatch(j ->
                        !LocalDate.parse(j.getUpdatedAt()).isBefore(oldest)),
                "源站更新时间应在近 30 天内");

        // ── 入库前数据预览：打印实际映射结果（事务回滚，不落库），供核对格式与日期 ──
        System.out.println("\n===== offerqingbaoju 映射数据预览（共 " + synced.size()
                + " 条，此处仅显示前 6 条）=====");
        synced.stream().limit(6).forEach(j -> System.out.println(
                "  co=" + j.getCo() + " | recruitType=" + j.getRecruitType()
                        + " | target=" + j.getTarget() + " | city=" + j.getCity()
                        + "\n    deadline=" + j.getDeadline() + " | updatedAt=" + j.getUpdatedAt()
                        + " | coType=" + j.getCoType() + " | industry=" + j.getIndustry()
                        + "\n    positions=" + (j.getPositions() != null && j.getPositions().length() > 50
                                ? j.getPositions().substring(0, 50) + "…" : j.getPositions())
                        + " | applyUrl=" + (j.getApplyUrl() != null && j.getApplyUrl().length() > 60
                                ? j.getApplyUrl().substring(0, 60) + "…" : j.getApplyUrl())
                        + "\n    note=" + j.getNote() + "\n"));
        System.out.println("============================================================");

        // ── 去重验证：同一业务主键再次入库应被跳过 ──
        Job sample = synced.get(0);
        JobSyncReq same = new JobSyncReq(
                sample.getCo(), sample.getCoType(), sample.getIndustry(),
                sample.getRecruitType(), sample.getTarget(), sample.getCity(),
                sample.getPositions(), sample.getUpdatedAt(), sample.getDeadline(),
                sample.getApplyUrl(), sample.getAnnounceUrl(), sample.getNote());
        Map<String, Integer> dup = jobService.insertNewJobs(List.of(same));
        assertEquals(0, dup.get("inserted"), "重复记录不应再新增");
        assertEquals(1, dup.get("skipped"), "重复记录应被跳过");
    }

    private String bizKey(Job j) {
        return j.getCo() + "|" + j.getPositions() + "|" + j.getRecruitType()
                + "|" + j.getCity() + "|" + j.getDeadline();
    }
}
