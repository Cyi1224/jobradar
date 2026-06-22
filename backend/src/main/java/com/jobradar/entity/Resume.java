package com.jobradar.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/** 简历。字段与前端 JSON 一致。 */
@Entity
@Table(name = "resume")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;       // 所属用户（数据隔离）

    private String name;
    private String updatedAt;
    private String size;
    private String color;      // blue / green，决定图标与标签配色
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resume_tag", joinColumns = @JoinColumn(name = "resume_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    public Resume() {}

    public Resume(String name, String updatedAt, String size, String color,
                  boolean active, List<String> tags) {
        this.name = name;
        this.updatedAt = updatedAt;
        this.size = size;
        this.color = color;
        this.active = active;
        this.tags = tags;
    }

    // ── getters / setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
