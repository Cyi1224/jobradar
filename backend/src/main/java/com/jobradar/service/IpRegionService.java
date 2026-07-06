package com.jobradar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * IP 地区解析服务：通过 ip-api.com 免费 API 查询 IP 所属地区。
 * 结果缓存在内存中，同一 IP 只查一次。
 */
@Service
public class IpRegionService {

    private static final Logger log = LoggerFactory.getLogger(IpRegionService.class);

    private final RestTemplate restTemplate;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /** 内网 IP 模式：不查外部 API */
    private static final Pattern PRIVATE_IP = Pattern.compile(
            "^(127\\.|192\\.168\\.|10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|0\\.|::1|localhost)");

    public IpRegionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 解析 IP 地区（同步，优先缓存）。
     * @return 如 "中国-广东-深圳"，内网/失败返回 null
     */
    public String resolve(String ip) {
        if (ip == null || ip.isBlank()) return null;
        if (PRIVATE_IP.matcher(ip).find()) return null;

        return cache.computeIfAbsent(ip, this::fetchRegion);
    }

    /**
     * 异步解析并回调更新。
     */
    public void resolveAsync(String ip, RegionCallback callback) {
        CompletableFuture.runAsync(() -> {
            String region = resolve(ip);
            if (region != null) {
                try {
                    callback.onResolved(region);
                } catch (Exception e) {
                    log.debug("Region callback error: {}", e.getMessage());
                }
            }
        });
    }

    private String fetchRegion(String ip) {
        try {
            String url = "http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=country,regionName,city";
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && "success".equals(resp.get("status")) || resp != null && resp.containsKey("country")) {
                String country = str(resp.get("country"));
                String region = str(resp.get("regionName"));
                String city = str(resp.get("city"));
                if (country.isEmpty()) return null;
                StringBuilder sb = new StringBuilder(country);
                if (!region.isEmpty()) sb.append("-").append(region);
                if (!city.isEmpty() && !city.equals(region)) sb.append("-").append(city);
                return sb.toString();
            }
        } catch (Exception e) {
            log.debug("IP lookup failed for {}: {}", ip, e.getMessage());
        }
        return null;
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @FunctionalInterface
    public interface RegionCallback {
        void onResolved(String region);
    }
}
