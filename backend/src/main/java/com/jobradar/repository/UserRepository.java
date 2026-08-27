package com.jobradar.repository;

import com.jobradar.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAccount(String account);
    boolean existsByAccount(String account);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 开通过会员的用户（按到期时间倒序），含已过期 */
    Page<User> findByMemberUntilIsNotNullOrderByMemberUntilDesc(Pageable pageable);
    long countByMemberUntilIsNotNull();

    /** 活跃会员数（memberUntil 未过期） */
    @Query("SELECT COUNT(u) FROM User u WHERE u.memberUntil IS NOT NULL AND u.memberUntil > :now")
    long countActiveMembers(@Param("now") LocalDateTime now);
}
