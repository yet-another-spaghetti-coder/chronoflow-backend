package nus.edu.u.framework.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityAuditLoggerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;
    @Mock private AuditLogWriterService writerService;

    private SecurityAuditLogger logger;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        logger = new SecurityAuditLogger(redisTemplate, new ObjectMapper());
    }

    @Test
    void log_writesToRedis() {
        logger.log(SecurityAuditLogger.SecurityEvent.LOGIN_SUCCESS, "100", "10.0.0.1", "OK");

        verify(listOperations).rightPush(anyString(), anyString());
        verify(redisTemplate).expire(anyString(), eq(7L), eq(TimeUnit.DAYS));
    }

    @Test
    void log_withWriterService_persistsToDbAsync() {
        logger.setAuditLogWriterService(writerService);

        logger.log(SecurityAuditLogger.SecurityEvent.LOGIN_SUCCESS, "100", "10.0.0.1", "OK");

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        AuditLogDO log = captor.getValue();
        assertThat(log.getModule()).isEqualTo("security");
        assertThat(log.getOperation()).isEqualTo("LOGIN_SUCCESS");
        assertThat(log.getType()).isEqualTo(AuditType.SECURITY.getValue());
        assertThat(log.getUserId()).isEqualTo(100L);
        assertThat(log.getUserIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void log_criticalEvent_persistsToDbSync() {
        logger.setAuditLogWriterService(writerService);

        logger.log(SecurityAuditLogger.SecurityEvent.REFRESH_TOKEN_REUSE_DETECTED,
                "200", "192.168.1.1", "Reuse detected");

        verify(writerService).writeSync(any(AuditLogDO.class));
        verify(writerService, never()).writeAsync(any());
    }

    @Test
    void log_tokenFingerprintMismatch_persistsSync() {
        logger.setAuditLogWriterService(writerService);

        logger.log(SecurityAuditLogger.SecurityEvent.TOKEN_FINGERPRINT_MISMATCH,
                "300", "10.0.0.2", "Fingerprint mismatch");

        verify(writerService).writeSync(any(AuditLogDO.class));
        verify(writerService, never()).writeAsync(any());
    }

    @Test
    void log_withoutWriterService_doesNotPersistToDb() {
        // No writer service set
        logger.log(SecurityAuditLogger.SecurityEvent.LOGIN_SUCCESS, "100", "10.0.0.1", "OK");

        // Should still write to Redis but not to DB
        verify(listOperations).rightPush(anyString(), anyString());
    }

    @Test
    void log_nonNumericUserId_storesInExtra() {
        logger.setAuditLogWriterService(writerService);

        logger.log(SecurityAuditLogger.SecurityEvent.LOGIN_SUCCESS,
                "firebase-uid-abc123", "10.0.0.1", "Firebase login");

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        AuditLogDO log = captor.getValue();
        assertThat(log.getUserId()).isNull();
        assertThat(log.getExtra()).contains("firebase-uid-abc123");
    }

    @Test
    void log_nullUserId_handlesGracefully() {
        logger.setAuditLogWriterService(writerService);

        logger.log(SecurityAuditLogger.SecurityEvent.RATE_LIMIT_EXCEEDED,
                null, "10.0.0.1", "Rate limited");

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        AuditLogDO log = captor.getValue();
        assertThat(log.getUserId()).isNull();
        assertThat(log.getExtra()).isNull();
    }

    @Test
    void log_allNormalEvents_useAsyncWrite() {
        logger.setAuditLogWriterService(writerService);

        SecurityAuditLogger.SecurityEvent[] normalEvents = {
                SecurityAuditLogger.SecurityEvent.LOGIN_SUCCESS,
                SecurityAuditLogger.SecurityEvent.LOGIN_FAILED_BAD_CREDENTIALS,
                SecurityAuditLogger.SecurityEvent.LOGOUT,
                SecurityAuditLogger.SecurityEvent.TOKEN_REFRESHED,
                SecurityAuditLogger.SecurityEvent.PERMISSION_DENIED,
                SecurityAuditLogger.SecurityEvent.RATE_LIMIT_EXCEEDED,
                SecurityAuditLogger.SecurityEvent.USER_REGISTERED
        };

        for (SecurityAuditLogger.SecurityEvent event : normalEvents) {
            logger.log(event, "1", "127.0.0.1", "test");
        }

        verify(writerService, never()).writeSync(any());
    }
}
