package com.jobradar.repository;

import com.jobradar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAccount(String account);
    boolean existsByAccount(String account);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
