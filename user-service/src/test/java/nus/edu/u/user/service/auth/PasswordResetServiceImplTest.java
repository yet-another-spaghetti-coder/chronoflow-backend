package nus.edu.u.user.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.common.exception.ServiceException;
import nus.edu.u.framework.security.audit.SecurityAuditLogger;
import nus.edu.u.framework.security.audit.SecurityAuditLogger.SecurityEvent;
import nus.edu.u.framework.security.password.PasswordPolicyService;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceImplTest {

    private static final String KEY_PREFIX = "auth:password_reset:";

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SecurityAuditLogger auditLogger;
    @Mock private PasswordPolicyService passwordPolicyService;

    @InjectMocks private PasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
    }

    private static String sha256(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static UserDO enabledUser(long id) {
        return UserDO.builder()
                .id(id)
                .username("alice")
                .email("alice@example.com")
                .status(CommonStatusEnum.ENABLE.getStatus())
                .build();
    }

    // ============================================================
    // requestReset
    // ============================================================

    @Test
    void requestReset_existingEnabledUser_writesTokenAndAudits() {
        UserDO user = enabledUser(42L);
        when(userService.getUserByEmailWithoutTenant("alice@example.com")).thenReturn(user);

        service.requestReset("Alice@Example.COM", "10.0.0.1");

        // audit emitted with userId + truncated normalised email
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_REQUESTED),
                        eq("42"),
                        eq("10.0.0.1"),
                        eq("alice@example.com"));
        // a SET to Redis with 30-min TTL, payload = userId
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCap.capture(), valCap.capture(), eq(Duration.ofMinutes(30)));
        assertThat(keyCap.getValue()).startsWith(KEY_PREFIX);
        assertThat(valCap.getValue()).isEqualTo("42");
    }

    @Test
    void requestReset_unknownEmail_auditsButNoRedisWrite() {
        when(userService.getUserByEmailWithoutTenant(anyString())).thenReturn(null);

        service.requestReset("nobody@nowhere.invalid", "10.0.0.1");

        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_REQUESTED),
                        eq(null),
                        eq("10.0.0.1"),
                        eq("nobody@nowhere.invalid"));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void requestReset_disabledUser_auditsButNoRedisWrite() {
        UserDO user = enabledUser(43L);
        user.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(userService.getUserByEmailWithoutTenant("alice@example.com")).thenReturn(user);

        service.requestReset("alice@example.com", "10.0.0.1");

        verify(auditLogger).log(eq(SecurityEvent.PASSWORD_RESET_REQUESTED), any(), any(), any());
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void requestReset_nullEmail_normalisedToEmpty() {
        when(userService.getUserByEmailWithoutTenant("")).thenReturn(null);

        service.requestReset(null, "10.0.0.1");

        verify(auditLogger)
                .log(eq(SecurityEvent.PASSWORD_RESET_REQUESTED), eq(null), eq("10.0.0.1"), eq(""));
    }

    // ============================================================
    // resetPassword — rejection paths
    // ============================================================

    @Test
    void resetPassword_nullToken_auditsAndThrows() {
        assertThatThrownBy(() -> service.resetPassword(null, "Zphyr-7Brave-Wolf!", "10.0.0.1"))
                .isInstanceOf(ServiceException.class);
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_TOKEN_INVALID),
                        eq(null),
                        eq("10.0.0.1"),
                        eq("blank"));
    }

    @Test
    void resetPassword_blankToken_auditsAndThrows() {
        assertThatThrownBy(() -> service.resetPassword("   ", "Zphyr-7Brave-Wolf!", "10.0.0.1"))
                .isInstanceOf(ServiceException.class);
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_TOKEN_INVALID),
                        eq(null),
                        eq("10.0.0.1"),
                        eq("blank"));
    }

    @Test
    void resetPassword_unknownToken_auditsAndThrows() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(
                        () -> service.resetPassword("any-token", "Zphyr-7Brave-Wolf!", "10.0.0.1"))
                .isInstanceOf(ServiceException.class);
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_TOKEN_INVALID),
                        eq(null),
                        eq("10.0.0.1"),
                        eq("not_found_or_expired"));
    }

    @Test
    void resetPassword_corruptUserIdInRedis_deletesKeyAndAuditsAndThrows() {
        when(valueOps.get(KEY_PREFIX + sha256("tok"))).thenReturn("not-a-number");

        assertThatThrownBy(() -> service.resetPassword("tok", "Zphyr-7Brave-Wolf!", "10.0.0.1"))
                .isInstanceOf(ServiceException.class);

        verify(redis).delete(KEY_PREFIX + sha256("tok"));
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_TOKEN_INVALID),
                        eq(null),
                        eq("10.0.0.1"),
                        eq("corrupt_payload"));
    }

    @Test
    void resetPassword_userNotFound_deletesKeyAndAuditsAndThrows() {
        when(valueOps.get(KEY_PREFIX + sha256("tok"))).thenReturn("42");
        when(userService.selectUserByIdWithoutTenant(42L)).thenReturn(null);

        assertThatThrownBy(() -> service.resetPassword("tok", "Zphyr-7Brave-Wolf!", "10.0.0.1"))
                .isInstanceOf(ServiceException.class);

        verify(redis).delete(KEY_PREFIX + sha256("tok"));
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_TOKEN_INVALID),
                        eq("42"),
                        eq("10.0.0.1"),
                        eq("user_not_found"));
    }

    @Test
    void resetPassword_userDisabled_deletesKeyAndAuditsAndThrows() {
        UserDO user = enabledUser(42L);
        user.setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(valueOps.get(KEY_PREFIX + sha256("tok"))).thenReturn("42");
        when(userService.selectUserByIdWithoutTenant(42L)).thenReturn(user);

        assertThatThrownBy(() -> service.resetPassword("tok", "Zphyr-7Brave-Wolf!", "10.0.0.1"))
                .isInstanceOf(ServiceException.class);

        verify(redis).delete(KEY_PREFIX + sha256("tok"));
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_TOKEN_INVALID),
                        eq("42"),
                        eq("10.0.0.1"),
                        eq("user_disabled"));
    }

    // ============================================================
    // resetPassword — happy path
    // ============================================================

    @Test
    void resetPassword_validToken_updatesPasswordDeletesTokenAndAudits() {
        UserDO user = enabledUser(42L);
        when(valueOps.get(KEY_PREFIX + sha256("tok"))).thenReturn("42");
        when(userService.selectUserByIdWithoutTenant(42L)).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("BCRYPT_HASH");
        when(redis.delete(anyString())).thenReturn(Boolean.TRUE);

        service.resetPassword("tok", "Zphyr-7Brave-Wolf!", "10.0.0.1");

        // Cross-field policy was consulted
        verify(passwordPolicyService)
                .assertNotIdentity(eq("Zphyr-7Brave-Wolf!"), eq("alice"), eq("alice@example.com"));

        // Password persisted via UserService.updatePassword
        ArgumentCaptor<String> encodedCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> saltCap = ArgumentCaptor.forClass(String.class);
        verify(userService).updatePassword(eq(42L), encodedCap.capture(), saltCap.capture());
        assertThat(encodedCap.getValue()).isEqualTo("BCRYPT_HASH");
        assertThat(saltCap.getValue()).isNotBlank();

        // Token deleted (single-use)
        verify(redis).delete(KEY_PREFIX + sha256("tok"));

        // Completion audit
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_COMPLETED),
                        eq("42"),
                        eq("10.0.0.1"),
                        eq("alice"));
    }

    @Test
    void resetPassword_validToken_policyRejection_propagatesAndDoesNotUpdatePassword() {
        UserDO user = enabledUser(42L);
        when(valueOps.get(KEY_PREFIX + sha256("tok"))).thenReturn("42");
        when(userService.selectUserByIdWithoutTenant(42L)).thenReturn(user);
        org.mockito.Mockito.doThrow(new ServiceException(1001014, "policy"))
                .when(passwordPolicyService)
                .assertNotIdentity(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.resetPassword("tok", "alice", "10.0.0.1"))
                .isInstanceOf(ServiceException.class);

        verify(userService, never()).updatePassword(anyLong(), anyString(), anyString());
        // Token NOT deleted on policy rejection — that's by design (lets the user retry without
        // having to request a fresh reset email).
        verify(redis, never()).delete(anyString());
    }

    @Test
    void resetPassword_concurrentlyConsumedToken_stillSucceedsForFirstCaller() {
        // delete() returns FALSE meaning another thread/request already deleted it
        UserDO user = enabledUser(42L);
        when(valueOps.get(KEY_PREFIX + sha256("tok"))).thenReturn("42");
        when(userService.selectUserByIdWithoutTenant(42L)).thenReturn(user);
        when(passwordEncoder.encode(anyString())).thenReturn("BCRYPT_HASH");
        when(redis.delete(anyString())).thenReturn(Boolean.FALSE);

        // Should NOT throw — the password was still updated, audit was still emitted
        service.resetPassword("tok", "Zphyr-7Brave-Wolf!", "10.0.0.1");

        verify(userService).updatePassword(eq(42L), anyString(), anyString());
        verify(auditLogger)
                .log(
                        eq(SecurityEvent.PASSWORD_RESET_COMPLETED),
                        eq("42"),
                        eq("10.0.0.1"),
                        eq("alice"));
    }
}
