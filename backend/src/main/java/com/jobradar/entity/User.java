package com.jobradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 平台用户。密码只存 BCrypt 哈希，绝不存明文。 */
@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime memberUntil;   // 会员到期时间；null 或已过期 = 免费版

    public User() {}

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getMemberUntil() { return memberUntil; }
    public void setMemberUntil(LocalDateTime memberUntil) { this.memberUntil = memberUntil; }
}
