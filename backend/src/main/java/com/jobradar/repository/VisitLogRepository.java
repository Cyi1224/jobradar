package com.jobradar.repository;

import com.jobradar.entity.VisitLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitLogRepository extends JpaRepository<VisitLog, Long> {

    // ── 计数（SQL 端聚合，不加载实体） ──

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByUserIdIsNotNullAndCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT v.ip) FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end")
    long countDistinctIpByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT v.userId) FROM VisitLog v WHERE v.userId IS NOT NULL AND v.createdAt BETWEEN :start AND :end")
    long countDistinctUserIdByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ── SQL 端 GROUP BY 聚合（返回聚合结果，不加载实体） ──

    /** 按天统计访问量: [date(String), count(Long)] */
    @Query("SELECT FUNCTION('DATE', v.createdAt), COUNT(v) FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('DATE', v.createdAt) ORDER BY FUNCTION('DATE', v.createdAt)")
    List<Object[]> countByDayBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按小时统计: [hour(Integer), count(Long)] */
    @Query("SELECT FUNCTION('HOUR', v.createdAt), COUNT(v) FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('HOUR', v.createdAt) ORDER BY FUNCTION('HOUR', v.createdAt)")
    List<Object[]> countByHourBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按来源统计: [source(String), count(Long)] */
    @Query("SELECT v.source, COUNT(v) FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end GROUP BY v.source")
    List<Object[]> countBySourceBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按页面统计: [pageName(String), path(String), count(Long)] */
    @Query("SELECT v.pageName, v.path, COUNT(v) FROM VisitLog v WHERE v.visitType = 'page' AND v.createdAt BETWEEN :start AND :end GROUP BY v.pageName, v.path")
    List<Object[]> countByPageBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按地区统计: [region(String), count(Long)] */
    @Query("SELECT v.region, COUNT(v) FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end GROUP BY v.region")
    List<Object[]> countByRegionBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 每日活跃用户（去重 userId）: [date(String), distinctUserIdCount(Long)] */
    @Query("SELECT FUNCTION('DATE', v.createdAt), COUNT(DISTINCT v.userId) FROM VisitLog v WHERE v.userId IS NOT NULL AND v.createdAt BETWEEN :start AND :end GROUP BY FUNCTION('DATE', v.createdAt) ORDER BY FUNCTION('DATE', v.createdAt)")
    List<Object[]> countDauByDayBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ── 分页查询（不加载全部） ──

    /** 最近访问记录（分页，只取 N 条） */
    @Query("SELECT v FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end ORDER BY v.createdAt DESC")
    List<VisitLog> findRecentByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    /** 按类型和时间范围查询（用于兼容旧代码，需要完整实体时使用） */
    @Query("SELECT v FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end ORDER BY v.createdAt ASC")
    List<VisitLog> findAllByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ── 清理 ──

    /** 删除指定时间之前的记录 */
    @Modifying
    @Query("DELETE FROM VisitLog v WHERE v.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
