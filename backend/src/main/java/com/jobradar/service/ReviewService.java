package com.jobradar.service;

import com.jobradar.dto.ReviewDTO;
import com.jobradar.entity.Application;
import com.jobradar.entity.StatusLog;
import com.jobradar.repository.ApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 投递复盘：从投递数据派生通过率漏斗、城市分布与建议，
 * 算法与前端 data/review.js 一致，保证 mock / 后端结果相同。
 */
@Service
public class ReviewService {

    private static final String[] COLORS = {"#1A56DB", "#0E9F6E", "#FF5A1F", "#534AB7", "#5B8AF5"};

    private final ApplicationRepository repo;

    public ReviewService(ApplicationRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)   // 需在事务内访问 Application 的懒加载 logs
    public ReviewDTO summary() {
        List<Application> apps = repo.findAll();
        int total     = apps.size();
        int submitted = (int) apps.stream().filter(a -> !"未投递".equals(a.getStatus())).count();
        int exam      = (int) apps.stream().filter(a -> reached(a, "已笔试")).count();
        int interview = (int) apps.stream().filter(a -> reached(a, "已面试")).count();
        int oc        = (int) apps.stream().filter(a -> "已OC".equals(a.getStatus())).count();

        List<ReviewDTO.Rate> rates = List.of(
                new ReviewDTO.Rate("简历通过率", "blue",   pct(exam, submitted),
                        "投递 " + submitted + " → 笔试 " + exam),
                new ReviewDTO.Rate("笔试通过率", "green",  pct(interview, exam),
                        "笔试 " + exam + " → 面试 " + interview),
                new ReviewDTO.Rate("面试通过率", "orange", pct(oc, interview),
                        "面试 " + interview + " → OC " + oc),
                new ReviewDTO.Rate("最终 OC 率", "green",  pct(oc, submitted),
                        "全流程综合转化")
        );

        // 城市分布
        Map<String, Integer> cityMap = new LinkedHashMap<>();
        for (Application a : apps) {
            String c = (a.getCity() == null || a.getCity().isBlank()) ? "其它" : a.getCity();
            cityMap.merge(c, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(cityMap.entrySet());
        entries.sort((x, y) -> y.getValue() - x.getValue());
        if (entries.size() > 5) entries = entries.subList(0, 5);
        int max = entries.stream().mapToInt(Map.Entry::getValue).max().orElse(1);

        List<ReviewDTO.Dist> distribution = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            int width = (int) Math.round(e.getValue() * 100.0 / max);
            distribution.add(new ReviewDTO.Dist(e.getKey(), e.getValue(), width, COLORS[i % COLORS.length]));
        }

        // 建议
        List<String> suggestions = new ArrayList<>();
        double ocRate = pct(oc, interview);
        if (interview > 0 && ocRate < 40) {
            suggestions.add("面试通过率偏低（" + trim(ocRate) + "%），建议加强系统设计与项目深挖练习。");
        }
        if (!entries.isEmpty() && total > 0) {
            var top = entries.get(0);
            int share = (int) Math.round(top.getValue() * 100.0 / total);
            if (share >= 40) {
                suggestions.add("投递集中在 " + top.getKey() + "（" + share + "%），可适当拓展其它城市分散风险。");
            }
        }
        if (submitted == 0) suggestions.add("还没有已投递的记录，先去「添加岗位」开始投递吧。");
        if (suggestions.isEmpty()) suggestions.add("整体节奏不错，继续保持，并及时跟进面试反馈。");

        return new ReviewDTO(
                new ReviewDTO.Stages(total, submitted, exam, interview, oc),
                rates, distribution, suggestions);
    }

    private boolean reached(Application a, String stage) {
        if (stage.equals(a.getStatus())) return true;
        for (StatusLog l : a.getLogs()) {
            if (stage.equals(l.getS())) return true;
        }
        return false;
    }

    /** 百分比，保留 1 位小数 */
    private double pct(int a, int b) {
        return b == 0 ? 0 : Math.round(a * 1000.0 / b) / 10.0;
    }

    private String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }
}
