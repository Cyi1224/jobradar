package com.jobradar.config;

import com.jobradar.entity.Application;
import com.jobradar.entity.Job;
import com.jobradar.entity.Profile;
import com.jobradar.entity.Recommendation;
import com.jobradar.entity.Resume;
import com.jobradar.entity.StatusLog;
import com.jobradar.repository.ApplicationRepository;
import com.jobradar.repository.JobRepository;
import com.jobradar.repository.ProfileRepository;
import com.jobradar.repository.RecommendationRepository;
import com.jobradar.entity.User;
import com.jobradar.repository.ResumeRepository;
import com.jobradar.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 启动时灌入数据：
 *   · 校招信息库（jobs）—— 库为空就灌入（dev / 生产都灌，这是共享的真实数据）；
 *   · 演示用的「我的投递 / 推荐 / 资料 / 简历」—— 仅在 jobradar.seed-demo=true 时灌（生产关闭）。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationRepository appRepo;
    private final JobRepository jobRepo;
    private final RecommendationRepository recoRepo;
    private final ProfileRepository profileRepo;
    private final ResumeRepository resumeRepo;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;
    private final boolean seedDemo;

    private Long demoUserId;   // 演示数据归属的用户（dev）

    public DataSeeder(ApplicationRepository appRepo, JobRepository jobRepo,
                      RecommendationRepository recoRepo, ProfileRepository profileRepo,
                      ResumeRepository resumeRepo, UserRepository userRepo, ObjectMapper objectMapper,
                      @Value("${jobradar.seed-demo:true}") boolean seedDemo) {
        this.appRepo = appRepo;
        this.jobRepo = jobRepo;
        this.recoRepo = recoRepo;
        this.profileRepo = profileRepo;
        this.resumeRepo = resumeRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
        this.seedDemo = seedDemo;
    }

    @Override
    public void run(String... args) {
        if (jobRepo.count() == 0) seedJobs();          // 校招库：库空则灌入（生产也灌）
        if (seedDemo && appRepo.count() == 0) {        // 演示数据：仅 dev
            demoUserId = ensureDemoUser();             // 演示账号：demo / demo123
            seedApplications();
            seedRecommendations();
            seedProfile();
            seedResumes();
        }
    }

    private Long ensureDemoUser() {
        return userRepo.findByUsername("demo").map(User::getId).orElseGet(() ->
                userRepo.save(new User("demo", new BCryptPasswordEncoder().encode("demo123"))).getId());
    }

    private void seedApplications() {
        Application a1 = app("字节跳动", "后端开发工程师", "秋招", "上海", "2026-06-20", "已面试", "内推渠道，HR 叫 Alice");
        a1.addLog(log("已投递", "2026-06-01 10:23", "通过内推链接投递"));
        a1.addLog(log("已笔试", "2026-06-08 14:00", "编程题 2 道，约 90 分钟，通过"));
        a1.addLog(log("已面试", "2026-06-15 15:30", "一面，技术面，聊了系统设计"));

        Application a2 = app("腾讯", "产品经理", "秋招", "深圳", "2026-06-21", "已笔试", "");
        a2.addLog(log("已投递", "2026-06-03 09:15", ""));
        a2.addLog(log("已笔试", "2026-06-10 10:00", "行测 + 专项，100 分钟"));

        Application a3 = app("阿里巴巴", "算法工程师", "秋招", "杭州", "2026-06-22", "已投递", "");
        a3.addLog(log("已投递", "2026-06-05 11:00", ""));

        Application a4 = app("华为", "硬件工程师", "秋招提前批", "深圳", "2026-06-26", "未投递", "");

        Application a5 = app("米哈游", "游戏后端", "秋招提前批", "上海", "招满为止", "未投递", "");

        Application a6 = app("网易", "游戏策划", "秋招", "杭州", "招满为止", "已挂", "二面挂，感觉是 HC 原因");
        a6.addLog(log("已投递", "2026-05-20 09:00", ""));
        a6.addLog(log("已笔试", "2026-05-25 14:00", "通过"));
        a6.addLog(log("已面试", "2026-06-01 10:00", "一面通过，二面挂"));
        a6.addLog(log("已挂",   "2026-06-08 18:00", "收到拒信"));

        Application a7 = app("百度", "推荐算法", "实习", "北京", "招满为止", "已OC", "导师很好，可以转正");
        a7.addLog(log("已投递", "2026-04-10 10:00", ""));
        a7.addLog(log("已笔试", "2026-04-15 14:00", "算法题，通过"));
        a7.addLog(log("已面试", "2026-04-22 15:00", "三面，全程技术"));
        a7.addLog(log("已OC",   "2026-05-06 16:00", "口头 OC，薪资 XX"));

        Application a8 = app("美团", "后端开发", "秋招", "北京", "招满为止", "已OC", "");
        a8.addLog(log("已投递", "2026-05-01 10:00", ""));
        a8.addLog(log("已笔试", "2026-05-10 14:00", ""));
        a8.addLog(log("已面试", "2026-05-18 15:00", "两轮技术面 + HR 面"));
        a8.addLog(log("已OC",   "2026-06-01 10:00", "正式 OC"));

        appRepo.saveAll(List.of(a1, a2, a3, a4, a5, a6, a7, a8));
    }

    /** 从 resources/jobs.json 加载校招信息库（由 givemeoc 招聘汇总.xlsx 导入，约 1.4w 条，分批写入）。 */
    private void seedJobs() {
        try (InputStream is = new ClassPathResource("jobs.json").getInputStream()) {
            List<Job> jobs = objectMapper.readValue(is, new TypeReference<List<Job>>() {});
            int batch = 1000;
            for (int i = 0; i < jobs.size(); i += batch) {
                jobRepo.saveAll(jobs.subList(i, Math.min(i + batch, jobs.size())));
            }
        } catch (Exception e) {
            throw new RuntimeException("加载 jobs.json 失败：" + e.getMessage(), e);
        }
    }

    private void seedRecommendations() {
        recoRepo.saveAll(List.of(
            new Recommendation("米哈游",   "游戏后端开发工程师", "上海", "2027届", "秋招提前批", "2026-07-10", List.of("秋招提前批", "Java", "分布式"),  92),
            new Recommendation("商汤科技", "CV 算法实习生",      "北京", "2027届", "实习",       "招满为止",   List.of("实习", "Python", "深度学习"),   88),
            new Recommendation("美团",     "后端开发工程师",     "北京", "2027届", "秋招",       "2026-08-05", List.of("秋招", "Java", "高并发"),       84),
            new Recommendation("蔚来",     "自动驾驶算法工程师", "上海", "2027届", "秋招",       "2026-08-12", List.of("秋招", "C++", "自动驾驶"),      81),
            new Recommendation("大疆创新", "嵌入式软件工程师",   "深圳", "2027届", "秋招",       "2026-08-01", List.of("秋招", "C++", "嵌入式"),        79),
            new Recommendation("字节跳动", "后端开发工程师",     "上海", "2027届", "秋招",       "2026-07-15", List.of("秋招", "Go", "微服务"),         76)
        ));
    }

    private void seedProfile() {
        Profile p = new Profile(
                "张三", "138xxxx0000", "zs@example.com", "男", "某知名高校",
                "本科", "计算机科学与技术", "2027", "3.8 / 4.0", "上海、北京、深圳", "免费版");
        p.setUserId(demoUserId);
        profileRepo.save(p);
    }

    private void seedResumes() {
        Resume r1 = new Resume("互联网通用版", "2026-06-10", "1.2 MB", "blue",  true,  List.of("后端", "算法"));
        Resume r2 = new Resume("金融行业版",   "2026-05-28", "0.9 MB", "green", false, List.of("量化", "风控"));
        r1.setUserId(demoUserId);
        r2.setUserId(demoUserId);
        resumeRepo.saveAll(List.of(r1, r2));
    }

    private Application app(String co, String pos, String type, String city,
                            String deadline, String status, String note) {
        Application a = new Application();
        a.setUserId(demoUserId);
        a.setCo(co); a.setPos(pos); a.setType(type); a.setCity(city);
        a.setDeadline(deadline); a.setStatus(status); a.setNote(note);
        return a;
    }

    private StatusLog log(String s, String time, String note) {
        return new StatusLog(s, LocalDateTime.parse(time, FMT), note);
    }
}
