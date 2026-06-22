package com.jobradar.service;

import com.jobradar.dto.JobPageDTO;
import com.jobradar.entity.Job;
import com.jobradar.repository.JobRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobService {

    /** 免费用户最多可浏览的页数（登录用户可看前 5 页校招信息）。 */
    private static final int FREE_MAX_PAGES = 5;

    private final JobRepository repo;

    public JobService(JobRepository repo) {
        this.repo = repo;
    }

    /** 分页 + 服务端筛选；按更新时间倒序（ISO 日期字符串字典序即时间序）。
     *  免费版仅放行前 FREE_MAX_PAGES 页；unlimited=true（会员）不限页。 */
    public JobPageDTO search(String q, String recruitType, String industry, String city,
                             boolean apply, boolean urgent, boolean soe, boolean inst, boolean foreign,
                             int page, int size, boolean unlimited) {
        Specification<Job> spec = build(q, recruitType, industry, city, apply, urgent, soe, inst, foreign);
        Sort sort = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));
        Page<Job> p = repo.findAll(spec, PageRequest.of(Math.max(0, page), clampSize(size), sort));

        if (unlimited) {
            return new JobPageDTO(p.getContent(), p.getTotalElements(), p.getNumber(), p.getSize(), p.getTotalPages(), false);
        }
        boolean capped = p.getTotalPages() > FREE_MAX_PAGES;
        int allowedPages = Math.min(p.getTotalPages(), FREE_MAX_PAGES);
        List<Job> content = (p.getNumber() >= FREE_MAX_PAGES) ? List.of() : p.getContent();
        return new JobPageDTO(content, p.getTotalElements(), p.getNumber(), p.getSize(), allowedPages, capped);
    }

    private int clampSize(int size) {
        if (size <= 0) return 30;
        return Math.min(size, 100);
    }

    private Specification<Job> build(String q, String recruitType, String industry, String city,
                                     boolean apply, boolean urgent, boolean soe, boolean inst, boolean foreign) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("co"), like),
                        cb.like(root.get("positions"), like),
                        cb.like(root.get("city"), like),
                        cb.like(root.get("industry"), like),
                        cb.like(cb.coalesce(root.get("note"), ""), like)
                ));
            }
            if (StringUtils.hasText(recruitType)) ps.add(cb.equal(root.get("recruitType"), recruitType));
            if (StringUtils.hasText(industry))    ps.add(cb.equal(root.get("industry"), industry));
            if (StringUtils.hasText(city))        ps.add(cb.like(root.get("city"), "%" + city + "%"));
            if (apply) ps.add(cb.and(cb.isNotNull(root.get("applyUrl")), cb.notEqual(root.get("applyUrl"), "")));
            if (soe)     ps.add(cb.equal(root.get("coType"), "央国企"));
            if (foreign) ps.add(cb.equal(root.get("coType"), "外企"));
            if (inst) ps.add(cb.or(
                    cb.like(root.get("industry"), "%研究%"),
                    cb.like(root.get("co"), "%研究%"),
                    cb.equal(root.get("coType"), "事业单位")
            ));
            if (urgent) {
                // deadline 存的是 'YYYY-MM-DD' 字符串，ISO 字典序即时间序；
                // '招满为止' 等非日期值 > 任何 '2026-..'，会被上界自然排除。
                String today = LocalDate.now().toString();
                String plus7 = LocalDate.now().plusDays(7).toString();
                ps.add(cb.between(root.get("deadline"), today, plus7));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** 统计卡 + 下拉选项（全局聚合，与当前筛选无关）。 */
    public Map<String, Object> stats() {
        String today = repo.maxUpdatedAt();
        long todayCount = (today == null) ? 0 : repo.countByUpdatedAt(today);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("companies", repo.countDistinctCo());
        out.put("total", repo.count());
        out.put("open", repo.countOpen());
        out.put("today", todayCount);
        out.put("todayDate", today == null ? "" : today);
        out.put("recruitTypes", repo.distinctRecruitTypes());
        out.put("industries", repo.distinctIndustries());
        return out;
    }
}
