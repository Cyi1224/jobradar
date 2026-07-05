package com.jobradar.repository;

import com.jobradar.entity.VisitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitLogRepository extends JpaRepository<VisitLog, Long> {

    /** 按时间范围统计访问量 */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 按时间范围统计登录用户访问量 */
    long countByUserIdIsNotNullAndCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 按类型和时间范围查询（用于按天聚合） */
    List<VisitLog> findByVisitTypeAndCreatedAtBetween(String visitType, LocalDateTime start, LocalDateTime end);

    /** 全部类型按时间范围查询（用于按天聚合） */
    List<VisitLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 最近访问记录（按时间倒序） */
    List<VisitLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /** 去重 IP 计数（唯一访客） */
    @Query("SELECT COUNT(DISTINCT v.ip) FROM VisitLog v WHERE v.createdAt BETWEEN :start AND :end")
    long countDistinctIpByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 活跃用户数（去重 userId） */
    @Query("SELECT COUNT(DISTINCT v.userId) FROM VisitLog v WHERE v.userId IS NOT NULL AND v.createdAt BETWEEN :start AND :end")
    long countDistinctUserIdByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
