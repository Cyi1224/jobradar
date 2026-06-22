package com.jobradar.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 投递记录主表。一条投递对应多条状态变更日志（时间线）。
 */
@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;      // 所属用户（数据隔离）

    private String co;        // 公司
    private String pos;       // 岗位

    @Column(name = "job_type")
    private String type;      // 招聘类型（type 在部分数据库是保留字，列名改 job_type）

    private String city;
    private String deadline;  // 含"招满为止"，用字符串
    private String status;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<StatusLog> logs = new ArrayList<>();

    /** 追加一条时间线记录并维护双向关联 */
    public void addLog(StatusLog log) {
        log.setApplication(this);
        this.logs.add(log);
    }

    // ── getters / setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCo() { return co; }
    public void setCo(String co) { this.co = co; }
    public String getPos() { return pos; }
    public void setPos(String pos) { this.pos = pos; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<StatusLog> getLogs() { return logs; }
    public void setLogs(List<StatusLog> logs) { this.logs = logs; }
}
