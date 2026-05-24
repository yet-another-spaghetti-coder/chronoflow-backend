package nus.edu.u.framework.security.lockout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptCounterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private LoginAttemptCounter counter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        counter = new LoginAttemptCounter(redisTemplate);
        ReflectionTestUtils.setField(counter, "maxAttempts", 5);
        ReflectionTestUtils.setField(counter, "counterWindowSeconds", 900);
        ReflectionTestUtils.setField(counter, "lockSeconds", 900);
    }

    // --- isLocked -----------------------------------------------------------

    @Test
    void isLocked_nullUsername_returnsFalse() {
        assertThat(counter.isLocked(null)).isFalse();
    }

    @Test
    void isLocked_blankUsername_returnsFalse() {
        assertThat(counter.isLocked("   ")).isFalse();
    }

    @Test
    void isLocked_redisHasNoLockKey_returnsFalse() {
        when(redisTemplate.hasKey("auth:lockout:lock:alice")).thenReturn(false);
        assertThat(counter.isLocked("alice")).isFalse();
    }

    @Test
    void isLocked_redisHasLockKey_returnsTrue() {
        when(redisTemplate.hasKey("auth:lockout:lock:alice")).thenReturn(true);
        assertThat(counter.isLocked("alice")).isTrue();
    }

    @Test
    void isLocked_caseInsensitive_usesLowerCase() {
        when(redisTemplate.hasKey("auth:lockout:lock:alice")).thenReturn(true);
        // Whether we ask with "Alice" or "ALICE", we get the same answer
        assertThat(counter.isLocked("Alice")).isTrue();
        assertThat(counter.isLocked("ALICE")).isTrue();
    }

    // --- recordFailure ------------------------------------------------------

    @Test
    void recordFailure_nullUsername_returnsFalse() {
        assertThat(counter.recordFailure(null)).isFalse();
    }

    @Test
    void recordFailure_firstAttempt_setsExpiryReturnsFalse() {
        when(valueOps.increment("auth:lockout:counter:alice")).thenReturn(1L);

        boolean justLocked = counter.recordFailure("alice");

        assertThat(justLocked).isFalse();
        verify(redisTemplate).expire("auth:lockout:counter:alice", 900L, TimeUnit.SECONDS);
        // Lock key is NOT set yet
        verify(valueOps, never()).setIfAbsent(eq("auth:lockout:lock:alice"), any(), any());
    }

    @Test
    void recordFailure_attemptsBelowMax_returnsFalseNoLock() {
        when(valueOps.increment("auth:lockout:counter:alice")).thenReturn(3L);

        boolean justLocked = counter.recordFailure("alice");

        assertThat(justLocked).isFalse();
        verify(valueOps, never()).setIfAbsent(eq("auth:lockout:lock:alice"), any(), any());
    }

    @Test
    void recordFailure_attemptHitsMax_setsLockReturnsTrue() {
        when(valueOps.increment("auth:lockout:counter:alice")).thenReturn(5L);
        when(valueOps.setIfAbsent(
                        eq("auth:lockout:lock:alice"), eq("1"), eq(Duration.ofSeconds(900))))
                .thenReturn(true);

        boolean justLocked = counter.recordFailure("alice");

        assertThat(justLocked).isTrue();
        verify(valueOps)
                .setIfAbsent("auth:lockout:lock:alice", "1", Duration.ofSeconds(900));
        // Counter is dropped once the lock is set
        verify(redisTemplate).delete("auth:lockout:counter:alice");
    }

    @Test
    void recordFailure_attemptBeyondMax_locksButReturnsFalse() {
        // Second time we hit/exceed the lock threshold within the lock window
        when(valueOps.increment("auth:lockout:counter:alice")).thenReturn(6L);
        // setIfAbsent returns false because the lock already exists
        when(valueOps.setIfAbsent(
                        eq("auth:lockout:lock:alice"), eq("1"), eq(Duration.ofSeconds(900))))
                .thenReturn(false);

        boolean justLocked = counter.recordFailure("alice");

        assertThat(justLocked).isFalse();
    }

    // --- recordSuccess ------------------------------------------------------

    @Test
    void recordSuccess_clearsCounterAndLock() {
        counter.recordSuccess("alice");

        verify(redisTemplate).delete("auth:lockout:counter:alice");
        verify(redisTemplate).delete("auth:lockout:lock:alice");
    }

    @Test
    void recordSuccess_nullUsername_noOp() {
        counter.recordSuccess(null);

        verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.<String>any());
    }

    @Test
    void recordSuccess_caseInsensitive_usesLowerCase() {
        counter.recordSuccess("Alice");

        verify(redisTemplate).delete("auth:lockout:counter:alice");
        verify(redisTemplate).delete("auth:lockout:lock:alice");
    }

    // --- getLockTtlSeconds --------------------------------------------------

    @Test
    void getLockTtlSeconds_returnsTtlFromRedis() {
        when(redisTemplate.getExpire("auth:lockout:lock:alice", TimeUnit.SECONDS))
                .thenReturn(720L);

        assertThat(counter.getLockTtlSeconds("alice")).isEqualTo(720L);
    }

    @Test
    void getLockTtlSeconds_nullUsername_returnsMinusTwo() {
        assertThat(counter.getLockTtlSeconds(null)).isEqualTo(-2L);
    }

    @Test
    void getLockTtlSeconds_redisReturnsNull_returnsMinusTwo() {
        when(redisTemplate.getExpire("auth:lockout:lock:alice", TimeUnit.SECONDS))
                .thenReturn(null);

        assertThat(counter.getLockTtlSeconds("alice")).isEqualTo(-2L);
    }
}
