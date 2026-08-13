package com.jobradar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Zpay（易支付类）客户端：MD5 签名 / 下单 / 验签。
 * 签名算法（来自 zpay 文档）：
 *   1. 参数名 ASCII 升序排序（sign、sign_type、空值不参与）
 *   2. a=b&c=d 拼接（参数值不 url 编码）
 *   3. 拼接串 + 商户密钥 KEY → MD5 小写
 */
@Component
public class ZpayClient {

    private final RestTemplate restTemplate;
    private final String pid;
    private final String key;
    private final String submitUrl;

    public ZpayClient(RestTemplate restTemplate,
                      @Value("${jobradar.payment.zpay.pid:}") String pid,
                      @Value("${jobradar.payment.zpay.key:}") String key,
                      @Value("${jobradar.payment.zpay.submit-url:https://zpayz.cn/mapi.php}") String submitUrl) {
        this.restTemplate = restTemplate;
        this.pid = pid;
        this.key = key;
        this.submitUrl = submitUrl;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(pid) && StringUtils.hasText(key);
    }

    /** 生成签名（不含 sign、sign_type 和空值参数）。 */
    public String sign(Map<String, String> params) {
        String joined = params.entrySet().stream()
                .filter(e -> StringUtils.hasText(e.getValue()))
                .filter(e -> !"sign".equals(e.getKey()) && !"sign_type".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())   // ASCII 升序
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return md5(joined + key);
    }

    /** 校验回调签名。 */
    public boolean verify(Map<String, String> params, String sign) {
        if (!StringUtils.hasText(sign)) return false;
        return sign.equalsIgnoreCase(sign(params));
    }

    /** 调用 mapi.php 下单，返回 zpay 响应（含 payurl / qrcode / img）。 */
    public Map<String, Object> createOrder(String outTradeNo, String type, String name,
                                           String money, String clientIp, String notifyUrl,
                                           String returnUrl) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", pid);
        params.put("type", type);
        params.put("out_trade_no", outTradeNo);
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("name", name);
        params.put("money", money);
        params.put("clientip", clientIp);
        params.put("device", "pc");
        params.put("param", "plan=" + name);
        params.put("sign", sign(params));
        params.put("sign_type", "MD5");

        // mapi.php 要求 form-data 提交；用 String 接收再手动解析 JSON（兼容 charset 缺失）
        org.springframework.util.LinkedMultiValueMap<String, String> body = new org.springframework.util.LinkedMultiValueMap<>();
        params.forEach(body::add);
        try {
            String raw = restTemplate.postForObject(submitUrl, body, String.class);
            if (raw == null || raw.isBlank()) return Map.of("code", "error", "msg", "zpay 无响应");
            ObjectMapper mapper = new ObjectMapper();
            try {
                // zpay 可能返回嵌套 JSON 字符串（"{\"code\":1,...}"），先剥一层引号
                String inner = raw.trim();
                if (inner.startsWith("\"") && inner.endsWith("\"")) {
                    inner = mapper.readValue(inner, String.class);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = mapper.readValue(inner, Map.class);
                return resp != null ? resp : Map.of();
            } catch (Exception jsonErr) {
                return Map.of("code", "error", "msg", "zpay 响应解析失败: " + raw.substring(0, Math.min(raw.length(), 120)));
            }
        } catch (Exception e) {
            return Map.of("code", "error", "msg", "下单失败：" + e.getMessage());
        }
    }

    /** 查询订单状态：GET api.php?act=order → { code, status(1成功/0未支付), trade_no, money, ... } */
    public Map<String, Object> queryOrder(String outTradeNo) {
        try {
            String url = submitUrl.replace("/mapi.php", "/api.php") +
                    "?act=order&pid=" + pid + "&key=" + key + "&out_trade_no=" + outTradeNo;
            String raw = restTemplate.getForObject(url, String.class);
            if (raw == null || raw.isBlank()) return Map.of("code", "error", "msg", "查询无响应");
            ObjectMapper mapper = new ObjectMapper();
            try {
                String inner = raw.trim();
                if (inner.startsWith("\"") && inner.endsWith("\"")) inner = mapper.readValue(inner, String.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = mapper.readValue(inner, Map.class);
                return resp != null ? resp : Map.of();
            } catch (Exception e) {
                return Map.of("code", "error", "msg", "查询响应解析失败");
            }
        } catch (Exception e) {
            return Map.of("code", "error", "msg", "查询失败：" + e.getMessage());
        }
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 计算失败", e);
        }
    }
}
