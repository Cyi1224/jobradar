package com.jobradar.entity;

import jakarta.persistence.*;

/**
 * 校招信息库：公司岗位目录（只读展示）。
 * 字段名与前端 JSON 一致，可直接序列化返回。
 */
@Entity
@Table(name = "job")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String co;
    private String coType;       // 央国企 / 外企 / 民企 / 银行 / 事业单位
    private String industry;
    private String recruitType;  // 秋招 / 春招 / 实习 / 秋招提前批 ...
    private String target;       // 招聘对象，如 2027届

    @Column(length = 200)
    private String city;

    @Column(length = 2000)
    private String positions;    // 岗位/方向，可能较长

    private String updatedAt;
    private String deadline;

    @Column(length = 2000)
    private String applyUrl;     // 投递链接（部分带长查询参数，实测最长 ~1200）
    @Column(length = 2000)
    private String announceUrl;  // 公告链接
    @Column(length = 500)
    private String note;

    public Job() {}

    // ── getters / setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCo() { return co; }
    public void setCo(String co) { this.co = co; }
    public String getCoType() { return coType; }
    public void setCoType(String coType) { this.coType = coType; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getRecruitType() { return recruitType; }
    public void setRecruitType(String recruitType) { this.recruitType = recruitType; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPositions() { return positions; }
    public void setPositions(String positions) { this.positions = positions; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public String getApplyUrl() { return applyUrl; }
    public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }
    public String getAnnounceUrl() { return announceUrl; }
    public void setAnnounceUrl(String announceUrl) { this.announceUrl = announceUrl; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
