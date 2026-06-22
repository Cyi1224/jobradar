package com.jobradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 简历编辑器文档：每个用户一份，内容是编辑器的 JSON 字符串。 */
@Entity
@Table(name = "resume_doc", uniqueConstraints = @UniqueConstraint(columnNames = "userId"))
public class ResumeDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Lob
    private String content;          // 编辑器文档 JSON（dialect 自动映射为 CLOB/LONGTEXT）

    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
