package com.jobradar.controller;

import com.jobradar.entity.Order;
import com.jobradar.repository.OrderRepository;
import com.jobradar.security.UserContext;
import com.jobradar.service.MembershipService;
import com.jobradar.service.ZpayClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zpay 支付：
 *   POST /api/payment/create-order  创建订单 { plan, type } → { payurl, qrcode, img, orderNo }
 *   GET  /api/payment/notify        zpay 异步回调（验签 → 校验金额 → 幂等发货）
 * 两个路径均需在 Filter 中放行（见 JwtAuthFilter / BotFilter / RateLimitFilter）。
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final OrderRepository orderRepo;
    private final MembershipService membershipService;
    private final ZpayClient zpay;
    private final String notifyUrl;
    private final String returnUrl;
    private final String testAmount;

    /** 套餐 → 天数（与 MembershipService.daysOf 保持一致）。 */
    private static final Map<String, Integer> PLAN_DAYS = Map.of(
            "month", 30, "quarter", 90, "half", 180, "year", 365);
    private static final Map<String, String> PLAN_PRICE = Map.of(
            "month", "15", "quarter", "40", "half", "70", "year", "100");
    private static final Map<String, String> PLAN_NAME = Map.of(
            "month", "月度会员", "quarter", "季度会员", "half", "半年会员", "year", "年度会员");

    public PaymentController(OrderRepository orderRepo,
                             MembershipService membershipService,
                             ZpayClient zpay,
                             @Value("${jobradar.payment.zpay.notify-url:}") String notifyUrl,
                             @Value("${jobradar.payment.zpay.return-url:}") String returnUrl,
                             @Value("${jobradar.payment.zpay.test-amount:}") String testAmount) {
        this.orderRepo = orderRepo;
        this.membershipService = membershipService;
        this.zpay = zpay;
        this.notifyUrl = notifyUrl;
        this.returnUrl = returnUrl;
        this.testAmount = testAmount;
    }

    /** 实际下单金额：配置了 test-amount 则用它（仅本地测试），否则按套餐真实定价。 */
    private String moneyOf(String plan) {
        return (testAmount != null && !testAmount.isBlank()) ? testAmount : PLAN_PRICE.get(plan);
    }

    /** 创建订单：服务端定价，返回支付二维码/跳转链接。 */
    @PostMapping("/create-order")
    public Map<String, Object> createOrder(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long uid = UserContext.require();
        String plan = body.getOrDefault("plan", "");
        String type = body.getOrDefault("type", "alipay");
        if (!PLAN_DAYS.containsKey(plan)) return Map.of("code", "error", "msg", "未知套餐");
        if (!"alipay".equals(type)) return Map.of("code", "error", "msg", "仅支持支付宝支付");

        // 服务端定价（不信任前端价格）
        String money = moneyOf(plan);
        String orderNo = genOrderNo();
        orderRepo.save(new Order(orderNo, uid, plan, money, type));

        Map<String, Object> resp = zpay.createOrder(orderNo, type, PLAN_NAME.get(plan), money,
                clientIp(req), notifyUrl, returnUrl);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", orderNo);
        out.put("money", money);
        out.put("plan", plan);
        out.put("code", resp.get("code"));
        out.put("msg", resp.getOrDefault("msg", ""));
        if (resp.get("payurl") != null) out.put("payurl", resp.get("payurl"));
        if (resp.get("qrcode") != null) out.put("qrcode", resp.get("qrcode"));
        if (resp.get("img") != null) out.put("img", resp.get("img"));
        return out;
    }

    /** 查询订单支付状态（前端轮询用）：已支付则自动发货开通会员。 */
    @GetMapping("/order-status")
    public Map<String, Object> orderStatus(@RequestParam String orderNo) {
        Long uid = UserContext.require();
        Order order = orderRepo.findByOrderNo(orderNo).orElse(null);
        if (order == null) return Map.of("paid", false, "msg", "订单不存在");
        if (!order.getUserId().equals(uid)) return Map.of("paid", false, "msg", "无权查看该订单");

        // 已发货直接返回成功
        if ("PAID".equals(order.getStatus())) return Map.of("paid", true, "member", true);

        // 主动查询 zpay（覆盖本地测试收不到回调的情况）
        Map<String, Object> q = zpay.queryOrder(orderNo);
        Object status = q.get("status");
        if (q.get("code") != null && ("1".equals(String.valueOf(q.get("code"))) || q.get("code") instanceof Number n && n.intValue() == 1)
                && "1".equals(String.valueOf(status))) {
            order.setStatus("PAID");
            order.setTradeNo(String.valueOf(q.getOrDefault("trade_no", "")));
            order.setPaidAt(LocalDateTime.now());
            orderRepo.save(order);
            membershipService.grantByOrder(order.getUserId(), order.getPlan());
            return Map.of("paid", true, "member", true);
        }
        return Map.of("paid", false);
    }

    /** zpay 异步回调（GET，form 参数）。返回纯字符串 "success"。 */
    @GetMapping("/notify")
    public ResponseEntity<String> notify(HttpServletRequest req) {
        // 收集参数（去掉空值）
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
            if (e.getValue() != null && e.getValue().length > 0) params.put(e.getKey(), e.getValue()[0]);
        }
        String sign = params.get("sign");
        String tradeNo = params.get("trade_no");
        String outTradeNo = params.get("out_trade_no");
        String money = params.get("money");
        String status = params.get("trade_status");

        // 1. 验签
        if (!zpay.verify(params, sign)) {
            return ResponseEntity.ok("fail");
        }
        // 2. 只处理成功
        if (!"TRADE_SUCCESS".equals(status)) {
            return ResponseEntity.ok("success");  // 非成功状态也确认，避免重复通知
        }
        // 3. 查订单 + 幂等
        Optional<Order> opt = orderRepo.findByOrderNo(outTradeNo);
        if (opt.isEmpty()) return ResponseEntity.ok("fail");
        Order order = opt.get();
        if ("PAID".equals(order.getStatus())) {
            return ResponseEntity.ok("success");   // 已处理过，直接确认
        }
        // 4. 校验金额一致（防假通知）
        if (!order.getAmount().equals(money)) {
            return ResponseEntity.ok("fail");
        }
        // 5. 发货：开通会员
        order.setStatus("PAID");
        order.setTradeNo(tradeNo);
        order.setPaidAt(LocalDateTime.now());
        orderRepo.save(order);
        membershipService.grantByOrder(order.getUserId(), order.getPlan());
        return ResponseEntity.ok("success");
    }

    private String genOrderNo() {
        return System.currentTimeMillis() + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
