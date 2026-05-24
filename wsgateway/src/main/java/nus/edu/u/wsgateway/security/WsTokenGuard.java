package nus.edu.u.wsgateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis-backed guardrails around WS token minting and consumption. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WsTokenGuard {

    private static final String TOKEN_RATE_PREFIX = "chronoflow:ws:token-rate:";
    private static final String JTI_PREFIX = "chronoflow:ws:jti:";

    private final StringRedisTemplate redis;
    private final WsSecurityProperties props;

    public boolean allowMint(String userId) {
        String key = TOKEN_RATE_PREFIX + userId;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, props.getTokenRateWindow());
            }
            return count != null && count <= props.getTokenRateLimit();
        } catch (Exception e) {
            log.warn("[WS] token rate limit check failed userId={}: {}", userId, e.toString());
            return false;
        }
    }

    public boolean consumeJti(String userId, String jti, long expEpochSeconds) {
        if (!props.isReplayProtectionEnabled()) {
            return true;
        }
        if (jti == null || jti.isBlank()) {
            return false;
        }
        long ttlSeconds = Math.max(1L, expEpochSeconds - Instant.now().getEpochSecond() + 5L);
        String key = JTI_PREFIX + jti;
        try {
            Boolean stored =
                    redis.opsForValue().setIfAbsent(key, userId, Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(stored);
        } catch (Exception e) {
            log.warn("[WS] jti consume failed userId={}: {}", userId, e.toString());
            return false;
        }
    }

    public boolean isInternalTokenValid(String provided) {
        String expected = props.getInternalServiceToken();
        if (expected == null || expected.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
