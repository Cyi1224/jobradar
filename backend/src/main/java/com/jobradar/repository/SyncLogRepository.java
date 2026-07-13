package com.jobradar.repository;

import com.jobradar.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    /** 按时间范围倒序查询同步记录 */
    List<SyncLog> findBySyncTimeBetweenOrderBySyncTimeDesc(LocalDateTime start, LocalDateTime end);

    /** 统计成功次数 */
    @Query("SELECT COUNT(s) FROM SyncLog s WHERE s.status = 'SUCCESS' AND s.syncTime BETWEEN :start AND :end")
    long countSuccessBySyncTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 统计失败次数 */
    @Query("SELECT COUNT(s) FROM SyncLog s WHERE s.status = 'FAILURE' AND s.syncTime BETWEEN :start AND :end")
    long countFailureBySyncTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按天统计新增数 */
    @Query("SELECT FUNCTION('DATE', s.syncTime) as day, SUM(s.inserted) as total " +
           "FROM SyncLog s WHERE s.syncTime BETWEEN :start AND :end " +
           "GROUP BY FUNCTION('DATE', s.syncTime) ORDER BY day")
    List<Object[]> dailyInsertedBySyncTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
