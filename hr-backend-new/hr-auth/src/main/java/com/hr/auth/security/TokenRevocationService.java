package com.hr.auth.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed JWT revocation list. Entries live only until the original JWT expires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String KEY_PREFIX = "hr:security:jwt:revoked:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final TokenProvider tokenProvider;

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Claims claims = tokenProvider.parseToken(token);
            String jti = claims.getId();
            if (jti == null || jti.isBlank() || claims.getExpiration() == null) {
                return;
            }
            long seconds = Math.max(1, claims.getExpiration().toInstant()
                    .minusSeconds(Instant.now().getEpochSecond()).getEpochSecond());
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(seconds));
        } catch (Exception e) {
            // Logout remains idempotent if the token is already expired or malformed.
            log.debug("JWT revocation skipped for invalid token");
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("JWT revocation store unavailable");
            return false;
        }
    }
}
