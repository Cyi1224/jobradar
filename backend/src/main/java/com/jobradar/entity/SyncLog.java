package com.jobradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 每日 offerbiu 数据同步记录。
 * 只记录同步结果概要（成功/失败、新增数、异常信息），不记录具体岗位。
 */
@Entity
@Table(name = "sync_log")
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 同步开始时间 */
    @Column(nullable = false)
    private LocalDateTime syncTime;

    /** 同步状态：SUCCESS / FAILURE */
    @Column(nullable = false, length = 16)
    private String status;

    /** 拉取的总条数 */
    private int fetched;

    /** 新增的条数 */
    private int inserted;

    /** 跳过的条数（重复） */
    private int skipped;

    /** 耗时（秒） */
    private int durationSeconds;

    /** 异常信息（仅失败时记录） */
    @Column(length = 500)
    private String errorMessage;

    /** 触发方式：SCHEDULED / MANUAL / STARTUP */
    @Column(length = 16)
    private String triggerType;

    public SyncLog() {}

    public SyncLog(LocalDateTime syncTime, String status, int fetched, int inserted,
                   int skipped, int durationSeconds, String triggerType) {
        this.syncTime = syncTime;
        this.status = status;
        this.fetched = fetched;
        this.inserted = inserted;
        this.skipped = skipped;
        this.durationSeconds = durationSeconds;
        this.triggerType = triggerType;
    }

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getSyncTime() { return syncTime; }
    public void setSyncTime(LocalDateTime syncTime) { this.syncTime = syncTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getFetched() { return fetched; }
    public void setFetched(int fetched) { this.fetched = fetched; }
    public int getInserted() { return inserted; }
    public void setInserted(int inserted) { this.inserted = inserted; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
}
