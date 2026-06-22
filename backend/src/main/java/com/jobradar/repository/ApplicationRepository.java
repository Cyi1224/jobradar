package com.jobradar.repository;

import com.jobradar.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    // ── 全部按 userId 隔离 ──
    List<Application> findByUserIdOrderByIdDesc(Long userId);
    Optional<Application> findByIdAndUserId(Long id, Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    long countByUserIdAndStatus(Long userId, String status);
}
