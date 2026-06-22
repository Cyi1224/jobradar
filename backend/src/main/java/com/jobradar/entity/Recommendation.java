package com.jobradar.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 推荐岗位。matchScore 序列化为前端的 "match"（见 RecommendationDTO）。
 */
@Entity
@Table(name = "recommendation")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String co;
    private String pos;
    private String city;
    private String target;
    private String recruitType;
    private String deadline;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reco_tag", joinColumns = @JoinColumn(name = "reco_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Column(name = "match_score")
    private int matchScore;

    public Recommendation() {}

    public Recommendation(String co, String pos, String city, String target,
                          String recruitType, String deadline, List<String> tags, int matchScore) {
        this.co = co;
        this.pos = pos;
        this.city = city;
        this.target = target;
        this.recruitType = recruitType;
        this.deadline = deadline;
        this.tags = tags;
        this.matchScore = matchScore;
    }

    // ── getters / setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCo() { return co; }
    public void setCo(String co) { this.co = co; }
    public String getPos() { return pos; }
    public void setPos(String pos) { this.pos = pos; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getRecruitType() { return recruitType; }
    public void setRecruitType(String recruitType) { this.recruitType = recruitType; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }
}
