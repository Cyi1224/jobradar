package com.jobradar.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 支付订单。记录 zpay 下单与回调状态，保证幂等发货。
 * 状态：PENDING（待支付）/ PAID（已支付）/ CLOSED（已关闭/超时）。
 */
@Entity
@Table(name = "pay_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 商户订单号（唯一，最多32位） */
    @Column(nullable = false, unique = true, length = 32)
    private String orderNo;

    /** 下单用户 */
    @Column(nullable = false)
    private Long userId;

    /** 套餐：month/quarter/half/year */
    @Column(nullable = false, length = 16)
    private String plan;

    /** 订单金额（元，字符串，最多两位小数） */
    @Column(nullable = false, length = 16)
    private String amount;

    /** 支付方式：alipay / wxpay */
    @Column(length = 16)
    private String payType;

    /** 状态：PENDING / PAID / CLOSED */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    /** zpay 交易号（回调后写入） */
    @Column(length = 64)
    private String tradeNo;

    /** 创建时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 支付完成时间 */
    private LocalDateTime paidAt;

    public Order() {}

    public Order(String orderNo, Long userId, String plan, String amount, String payType) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.plan = plan;
        this.amount = amount;
        this.payType = payType;
    }

    // ── getters & setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getPayType() { return payType; }
    public void setPayType(String payType) { this.payType = payType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}
