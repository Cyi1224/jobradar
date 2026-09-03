package com.jobradar.repository;

import com.jobradar.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 原子抢占订单：仅当订单仍为 PENDING 时置为 PAID（返回受影响行数）。
     * 用于支付回调与前端轮询两条开通路径并发时，保证只有一方成功开通会员，避免双开通。
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = 'PAID', o.tradeNo = :tradeNo, o.paidAt = :paidAt " +
           "WHERE o.id = :id AND o.status = 'PENDING'")
    int claimOrderPaid(@Param("id") Long id,
                       @Param("tradeNo") String tradeNo,
                       @Param("paidAt") LocalDateTime paidAt);
}
