package nus.edu.u.framework.security.lockout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Per-account failed-login counter with temporary lockout (F-3 / C-02).
 *
 * <p>Counter and lock are stored under separate Redis keys so that:
 *
 * <ul>
 *   <li>The counter expires automatically after a quiet period (no successful login OR no further
 *       failures), preventing eternal accumulation.
 *   <li>The lock has a deterministic TTL — once expired the next attempt is allowed and counter
 *       reset is implicit.
 * </ul>
 *
 * <p>Username is lowercased before keying so that {@code Alice} and {@code alice} share a counter.
 * Registered as a Spring bean via {@code SecurityAutoConfiguration}.
 */
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptCounter {

    private static final String COUNTER_KEY_PREFIX = "auth:lockout:counter:";
    private static final String LOCK_KEY_PREFIX = "auth:lockout:lock:";

    private final StringRedisTemplate redisTemplate;

    @Value("${chronoflow.security.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${chronoflow.security.lockout.window-seconds:900}")
    private int counterWindowSeconds;

    @Value("${chronoflow.security.lockout.lock-seconds:900}")
    private int lockSeconds;

    /** Returns true if the account is currently locked. */
    public boolean isLocked(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + key(username)));
    }

    /**
     * Record a failed login. Returns true when the failure has just tripped the lock — callers can
     * use this to emit a one-time audit event without spamming on every subsequent attempt.
     */
    public boolean recordFailure(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String counterKey = COUNTER_KEY_PREFIX + key(username);
        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count == null) {
            return false;
        }
        if (count == 1) {
            redisTemplate.expire(counterKey, counterWindowSeconds, TimeUnit.SECONDS);
        }
        if (count >= maxAttempts) {
            String lockKey = LOCK_KEY_PREFIX + key(username);
            Boolean firstLock =
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent(lockKey, "1", Duration.ofSeconds(lockSeconds));
            // Counter has done its job — drop it so it doesn't keep climbing past lock.
            redisTemplate.delete(counterKey);
            log.warn(
                    "Login attempt counter tripped lock for username={} after {} failures (lock"
                            + " for {}s)",
                    username,
                    count,
                    lockSeconds);
            return Boolean.TRUE.equals(firstLock);
        }
        return false;
    }

    /** Reset both counter and lock after a successful login. */
    public void recordSuccess(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        redisTemplate.delete(COUNTER_KEY_PREFIX + key(username));
        redisTemplate.delete(LOCK_KEY_PREFIX + key(username));
    }

    /** Visible for tests / debug — TTL on the lock in seconds, or -2 if no lock. */
    public long getLockTtlSeconds(String username) {
        if (username == null || username.isBlank()) {
            return -2L;
        }
        Long ttl = redisTemplate.getExpire(LOCK_KEY_PREFIX + key(username), TimeUnit.SECONDS);
        return ttl == null ? -2L : ttl;
    }

    private static String key(String username) {
        return username.trim().toLowerCase();
    }
}
