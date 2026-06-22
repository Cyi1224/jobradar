package com.jobradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 投递状态变更日志（时间线节点）。
 */
@Entity
@Table(name = "status_log")
public class StatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @Column(name = "status")
    private String s;          // 该节点的状态

    private LocalDateTime time; // 由后端生成，比前端可信

    @Column(length = 500)
    private String note;

    public StatusLog() {}

    public StatusLog(String s, LocalDateTime time, String note) {
        this.s = s;
        this.time = time;
        this.note = note;
    }

    // ── getters / setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }
    public String getS() { return s; }
    public void setS(String s) { this.s = s; }
    public LocalDateTime getTime() { return time; }
    public void setTime(LocalDateTime time) { this.time = time; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
