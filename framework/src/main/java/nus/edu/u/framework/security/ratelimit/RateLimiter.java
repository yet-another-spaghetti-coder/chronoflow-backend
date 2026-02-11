package nus.edu.u.framework.security.ratelimit;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-based rate limiter for protecting authentication endpoints.
 * Uses sliding window approach with configurable limits.
 */
@RequiredArgsConstructor
@Slf4j
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    @Value("${chronoflow.security.rate-limit.max-attempts:10}")
    private int maxAttempts;

    @Value("${chronoflow.security.rate-limit.window-seconds:900}")
    private int windowSeconds;

    private static final String RATE_LIMIT_KEY_PREFIX = "auth:rate_limit:";

    /**
     * Check if request is allowed under rate limit.
     *
     * @param endpoint Endpoint identifier (e.g., "firebase-login")
     * @param clientIp Client IP address
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String endpoint, String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp + ":" + endpoint;
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            return true;
        }

        // Set expiry on first request
        if (currentCount == 1) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        if (currentCount > maxAttempts) {
            log.warn(
                    "Rate limit exceeded for endpoint={} ip={} count={}",
                    endpoint,
                    clientIp,
                    currentCount);
            return false;
        }

        return true;
    }

    /**
     * Reset rate limit counter for a client.
     *
     * @param endpoint Endpoint identifier
     * @param clientIp Client IP address
     */
    public void reset(String endpoint, String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp + ":" + endpoint;
        redisTemplate.delete(key);
    }

    /**
     * Get remaining attempts for a client.
     *
     * @param endpoint Endpoint identifier
     * @param clientIp Client IP address
     * @return Remaining attempts
     */
    public int getRemainingAttempts(String endpoint, String clientIp) {
        String key = RATE_LIMIT_KEY_PREFIX + clientIp + ":" + endpoint;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return maxAttempts;
        }

        int currentCount = Integer.parseInt(value);
        return Math.max(0, maxAttempts - currentCount);
    }
}
