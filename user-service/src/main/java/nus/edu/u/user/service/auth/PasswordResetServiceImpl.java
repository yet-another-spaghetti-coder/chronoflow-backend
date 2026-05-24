package nus.edu.u.user.service.auth;

import static nus.edu.u.common.enums.ErrorCodeConstants.PASSWORD_RESET_TOKEN_INVALID;
import static nus.edu.u.common.enums.ErrorCodeConstants.PASSWORD_RESET_USER_DISABLED;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.hutool.core.util.IdUtil;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.framework.security.audit.SecurityAuditLogger;
import nus.edu.u.framework.security.audit.SecurityAuditLogger.SecurityEvent;
import nus.edu.u.framework.security.password.PasswordPolicyService;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.service.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password reset service implementation (C-16, C-17, C-18).
 *
 * <p>Token model: 32 bytes from {@link SecureRandom}, base64url-encoded (no padding). Only the
 * SHA-256 hash of the token is persisted in Redis, so a Redis snapshot does not yield usable
 * tokens. The mapping {@code auth:password_reset:{sha256(token)} → userId} expires after 30
 * minutes. The mapping is deleted atomically with the password update, making the token single-use.
 */
@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final String RESET_TOKEN_KEY_PREFIX = "auth:password_reset:";
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final int RESET_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Resource private UserService userService;

    @Resource private PasswordEncoder passwordEncoder;

    @Resource private StringRedisTemplate stringRedisTemplate;

    @Resource private SecurityAuditLogger auditLogger;

    @Resource private PasswordPolicyService passwordPolicyService;

    @Value("${chronoflow.security.password-reset.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void requestReset(String email, String clientIp) {
        String normalisedEmail = email == null ? "" : email.trim().toLowerCase();
        UserDO userDO = userService.getUserByEmailWithoutTenant(normalisedEmail);

        // Always audit the request, regardless of whether the email exists.
        auditLogger.log(
                SecurityEvent.PASSWORD_RESET_REQUESTED,
                userDO == null ? null : userDO.getId().toString(),
                clientIp,
                truncate(normalisedEmail, 64));

        if (userDO == null) {
            // No-op for unknown emails — same response is returned to the client.
            return;
        }

        // Refuse to issue a reset for disabled accounts, but the response shape is unchanged.
        if (CommonStatusEnum.isDisable(userDO.getStatus())) {
            log.warn(
                    "Password reset requested for disabled account userId={} email={}",
                    userDO.getId(),
                    normalisedEmail);
            return;
        }

        String rawToken = generateRawToken();
        String tokenHash = sha256Base64Url(rawToken);
        stringRedisTemplate
                .opsForValue()
                .set(
                        RESET_TOKEN_KEY_PREFIX + tokenHash,
                        userDO.getId().toString(),
                        RESET_TOKEN_TTL);

        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;
        // TODO(F-9 follow-up): replace this stdout delivery with a call into notification-service
        // (SES) once the inter-service contract is finalised. Until then, the reset link is logged
        // here so dev/demo flows do not depend on email infrastructure.
        log.info(
                "[PASSWORD_RESET] Delivered reset link for userId={} email={} link={} (expires in"
                        + " {}m)",
                userDO.getId(),
                normalisedEmail,
                resetLink,
                RESET_TOKEN_TTL.toMinutes());
    }

    @Override
    public void resetPassword(String token, String newPassword, String clientIp) {
        if (token == null || token.isBlank()) {
            auditLogger.log(SecurityEvent.PASSWORD_RESET_TOKEN_INVALID, null, clientIp, "blank");
            throw exception(PASSWORD_RESET_TOKEN_INVALID);
        }

        String tokenHash = sha256Base64Url(token);
        String redisKey = RESET_TOKEN_KEY_PREFIX + tokenHash;
        String userIdStr = stringRedisTemplate.opsForValue().get(redisKey);
        if (userIdStr == null) {
            auditLogger.log(
                    SecurityEvent.PASSWORD_RESET_TOKEN_INVALID,
                    null,
                    clientIp,
                    "not_found_or_expired");
            throw exception(PASSWORD_RESET_TOKEN_INVALID);
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException nfe) {
            stringRedisTemplate.delete(redisKey);
            auditLogger.log(
                    SecurityEvent.PASSWORD_RESET_TOKEN_INVALID, null, clientIp, "corrupt_payload");
            throw exception(PASSWORD_RESET_TOKEN_INVALID);
        }

        UserDO userDO = userService.selectUserByIdWithoutTenant(userId);
        if (userDO == null) {
            stringRedisTemplate.delete(redisKey);
            auditLogger.log(
                    SecurityEvent.PASSWORD_RESET_TOKEN_INVALID,
                    userIdStr,
                    clientIp,
                    "user_not_found");
            throw exception(PASSWORD_RESET_TOKEN_INVALID);
        }
        if (CommonStatusEnum.isDisable(userDO.getStatus())) {
            stringRedisTemplate.delete(redisKey);
            auditLogger.log(
                    SecurityEvent.PASSWORD_RESET_TOKEN_INVALID,
                    userIdStr,
                    clientIp,
                    "user_disabled");
            throw exception(PASSWORD_RESET_USER_DISABLED);
        }

        // Cross-field policy: reject if the new password equals or contains the username/email.
        // Throws ServiceException(PASSWORD_POLICY_VIOLATION) → 400 from the global handler.
        passwordPolicyService.assertNotIdentity(
                newPassword, userDO.getUsername(), userDO.getEmail());

        // Update password using the same scheme as registration: BCrypt(rawPassword + salt). A
        // fresh salt is generated so token-leakage at rest cannot be combined with the old salt.
        String newSalt = IdUtil.fastSimpleUUID();
        String encoded = passwordEncoder.encode(newPassword + newSalt);
        userService.updatePassword(userId, encoded, newSalt);

        // Single-use: drop the token before returning. If the delete races with another request,
        // either both attempts hit the same key and one will fail at this point — which is fine.
        Boolean deleted = stringRedisTemplate.delete(redisKey);
        if (Boolean.FALSE.equals(deleted)) {
            log.warn("Password reset token already consumed concurrently for userId={}", userId);
        }

        auditLogger.log(
                SecurityEvent.PASSWORD_RESET_COMPLETED, userIdStr, clientIp, userDO.getUsername());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Base64Url(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JRE.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Constant-time equality, kept for callers that need to compare raw tokens. */
    @SuppressWarnings("unused")
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
