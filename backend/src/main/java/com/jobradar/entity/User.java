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

    /** 登录账号（仅数字/英文字母），映射到 DB 的 username 列确保向后兼容 */
    @Column(name = "username", nullable = false, length = 64)
    private String account;

    /** 显示昵称（支持中文等任意字符，最长 15 位） */
    @Column(length = 32)
    private String displayName;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime memberUntil;   // 会员到期时间；null 或已过期 = 免费版

    public User() {}

    public User(String account, String displayName, String passwordHash) {
        this.account = account;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getMemberUntil() { return memberUntil; }
    public void setMemberUntil(LocalDateTime memberUntil) { this.memberUntil = memberUntil; }
}
