package nus.edu.u.framework.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Security audit logger for tracking authentication and authorization events.
 * Logs to both structured log output and Redis for retention.
 */
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditLogger {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String AUDIT_KEY_PREFIX = "security:audit:log:";
    private static final long AUDIT_RETENTION_DAYS = 7;

    /** Security events to track. */
    public enum SecurityEvent {
        // Authentication events
        LOGIN_SUCCESS,
        LOGIN_FAILED_BAD_CREDENTIALS,
        LOGIN_FAILED_EMAIL_NOT_VERIFIED,
        LOGIN_FAILED_USER_NOT_FOUND,
        LOGIN_FAILED_ACCOUNT_DISABLED,
        LOGIN_FAILED_INVALID_TOKEN,

        // Registration events
        USER_REGISTERED,

        // Token events
        TOKEN_REFRESHED,
        TOKEN_FINGERPRINT_MISMATCH,
        REFRESH_TOKEN_REUSE_DETECTED,

        // Session events
        LOGOUT,

        // Authorization events
        PERMISSION_DENIED,

        // Rate limiting events
        RATE_LIMIT_EXCEEDED
    }

    /**
     * Log a security event.
     *
     * @param event Security event type
     * @param userId User ID (Firebase UID or internal ID)
     * @param clientIp Client IP address
     * @param detail Additional detail about the event
     */
    public void log(SecurityEvent event, String userId, String clientIp, String detail) {
        SecurityAuditEntry entry =
                SecurityAuditEntry.builder()
                        .event(event.name())
                        .userId(userId)
                        .clientIp(clientIp)
                        .detail(detail)
                        .timestamp(Instant.now().toString())
                        .build();

        try {
            String json = objectMapper.writeValueAsString(entry);
            log.info("[SECURITY_AUDIT] {}", json);

            // Store in Redis for retention
            String dateKey = AUDIT_KEY_PREFIX + LocalDate.now().toString();
            redisTemplate.opsForList().rightPush(dateKey, json);
            redisTemplate.expire(dateKey, AUDIT_RETENTION_DAYS, TimeUnit.DAYS);

        } catch (JsonProcessingException e) {
            log.warn(
                    "[SECURITY_AUDIT] event={} userId={} ip={} detail={}",
                    event,
                    userId,
                    clientIp,
                    detail);
        }

        // Critical events get additional logging
        if (event == SecurityEvent.REFRESH_TOKEN_REUSE_DETECTED
                || event == SecurityEvent.TOKEN_FINGERPRINT_MISMATCH) {
            log.error("CRITICAL SECURITY EVENT: {} | User: {} | IP: {}", event, userId, clientIp);
        }
    }

    /** Audit log entry structure. */
    @Data
    @Builder
    public static class SecurityAuditEntry {
        private String event;
        private String userId;
        private String clientIp;
        private String detail;
        private String timestamp;
    }
}
