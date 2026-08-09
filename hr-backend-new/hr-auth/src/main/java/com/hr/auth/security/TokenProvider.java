package com.hr.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验，对齐 Flask middleware/auth.py 的 HS256 实现。
 */
@Component
public class TokenProvider {

    private final SecretKey key;
    private final long expiresSeconds;
    private final long rememberMeSeconds;

    public TokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expires-seconds:3600}") long expiresSeconds,
            @Value("${jwt.remember-me-seconds:2592000}") long rememberMeSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiresSeconds = expiresSeconds;
        this.rememberMeSeconds = rememberMeSeconds;
    }

    /**
     * 签发 JWT。
     */
    public String createToken(Long userId, String role, Long tenantId, String username, boolean rememberMe) {
        long ttl = rememberMe ? rememberMeSeconds : expiresSeconds;
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("user_id", userId)
                .claim("role", role)
                .claim("tenant_id", tenantId)
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 JWT，返回 Claims。非法/过期时抛出 JwtException。
     */
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
