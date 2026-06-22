package com.jobradar.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** 签发 / 校验 JWT（HS256）。密钥走环境变量 JWT_SECRET（生产务必改）。 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtUtil(
            @Value("${jobradar.jwt.secret:jobradar-dev-secret-change-me-please-32bytes!}") String secret,
            @Value("${jobradar.jwt.ttl-days:7}") long ttlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlDays * 24 * 3600 * 1000L;
    }

    public String issue(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /** 校验并返回 userId；无效/过期返回 null。 */
    public Long parseUserId(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return Long.valueOf(jws.getPayload().getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
