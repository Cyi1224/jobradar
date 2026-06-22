package com.jobradar.repository;

import com.jobradar.entity.ResumeDoc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeDocRepository extends JpaRepository<ResumeDoc, Long> {
    Optional<ResumeDoc> findByUserId(Long userId);
}
