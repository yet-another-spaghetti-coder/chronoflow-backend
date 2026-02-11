package nus.edu.u.user.service.auth;

import cn.hutool.core.util.StrUtil;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Enhanced token service with rotation and reuse detection.
 * Implements token family tracking to detect refresh token reuse attacks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${chronoflow.security.refresh-token-expire:604800}")
    private long refreshTokenExpireSeconds;

    private static final String REFRESH_TOKEN_KEY = "Authorization:login:refresh_token:";
    private static final String TOKEN_FAMILY_KEY = "auth:refresh_family:";
    private static final String TOKEN_FINGERPRINT_KEY = "auth:token_fingerprint:";

    /**
     * Create a new refresh token with family tracking.
     *
     * @param userId User ID
     * @return New refresh token
     */
    public String createRefreshTokenWithFamily(Long userId) {
        String token = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();

        // Store token with family reference
        redisTemplate
                .opsForValue()
                .set(
                        REFRESH_TOKEN_KEY + token,
                        userId + ":" + familyId,
                        refreshTokenExpireSeconds,
                        TimeUnit.SECONDS);

        // Track active token in family
        redisTemplate
                .opsForValue()
                .set(TOKEN_FAMILY_KEY + familyId, token, refreshTokenExpireSeconds, TimeUnit.SECONDS);

        log.debug("Created refresh token for userId={} familyId={}", userId, familyId);
        return token;
    }

    /**
     * Rotate refresh token with reuse detection.
     *
     * @param oldToken Current refresh token
     * @return Rotation result with new token or reuse detection flag
     */
    public RefreshResult rotateRefreshToken(String oldToken) {
        if (StrUtil.isEmpty(oldToken)) {
            return null;
        }

        String value = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY + oldToken);
        if (value == null) {
            log.debug("Refresh token not found: {}", oldToken);
            return null;
        }

        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid refresh token format: {}", oldToken);
            return null;
        }

        Long userId;
        try {
            userId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId in refresh token: {}", parts[0]);
            return null;
        }
        String familyId = parts[1];

        // Check if this is the active token in the family
        String activeToken = redisTemplate.opsForValue().get(TOKEN_FAMILY_KEY + familyId);
        if (activeToken == null) {
            log.debug("Token family not found: {}", familyId);
            return null;
        }

        if (!oldToken.equals(activeToken)) {
            // REUSE DETECTED - old token is being used after rotation
            log.warn("Refresh token reuse detected! userId={} familyId={}", userId, familyId);
            return RefreshResult.reuseDetected(userId, familyId);
        }

        // Rotate: delete old token and create new one
        redisTemplate.delete(REFRESH_TOKEN_KEY + oldToken);
        String newToken = UUID.randomUUID().toString();

        redisTemplate
                .opsForValue()
                .set(
                        REFRESH_TOKEN_KEY + newToken,
                        userId + ":" + familyId,
                        refreshTokenExpireSeconds,
                        TimeUnit.SECONDS);

        redisTemplate
                .opsForValue()
                .set(TOKEN_FAMILY_KEY + familyId, newToken, refreshTokenExpireSeconds, TimeUnit.SECONDS);

        log.debug("Rotated refresh token for userId={} familyId={}", userId, familyId);
        return RefreshResult.success(userId, familyId, newToken);
    }

    /**
     * Invalidate entire token family (used after reuse detection).
     *
     * @param familyId Family ID to invalidate
     */
    public void invalidateTokenFamily(String familyId) {
        if (StrUtil.isEmpty(familyId)) {
            return;
        }

        String activeToken = redisTemplate.opsForValue().get(TOKEN_FAMILY_KEY + familyId);
        if (activeToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_KEY + activeToken);
        }
        redisTemplate.delete(TOKEN_FAMILY_KEY + familyId);
        log.info("Invalidated token family: {}", familyId);
    }

    /**
     * Remove a specific token and its family.
     *
     * @param token Token to remove
     */
    public void removeTokenAndFamily(String token) {
        if (StrUtil.isEmpty(token)) {
            return;
        }

        String value = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY + token);
        if (value != null) {
            String[] parts = value.split(":", 2);
            if (parts.length == 2) {
                redisTemplate.delete(TOKEN_FAMILY_KEY + parts[1]);
            }
        }
        redisTemplate.delete(REFRESH_TOKEN_KEY + token);
    }

    /**
     * Store fingerprint for access token binding.
     *
     * @param accessToken Access token
     * @param fingerprint SHA-256 fingerprint of User-Agent + IP
     */
    public void storeFingerprint(String accessToken, String fingerprint) {
        if (accessToken == null || fingerprint == null) {
            return;
        }
        redisTemplate
                .opsForValue()
                .set(TOKEN_FINGERPRINT_KEY + accessToken, fingerprint, 7200, TimeUnit.SECONDS);
    }

    /**
     * Get stored fingerprint for access token.
     *
     * @param accessToken Access token
     * @return Stored fingerprint or null
     */
    public String getFingerprint(String accessToken) {
        if (accessToken == null) {
            return null;
        }
        return redisTemplate.opsForValue().get(TOKEN_FINGERPRINT_KEY + accessToken);
    }

    /**
     * Remove fingerprint when token is invalidated.
     *
     * @param accessToken Access token
     */
    public void removeFingerprint(String accessToken) {
        if (accessToken == null) {
            return;
        }
        redisTemplate.delete(TOKEN_FINGERPRINT_KEY + accessToken);
    }

    /** Result of refresh token rotation. */
    @Data
    @AllArgsConstructor
    public static class RefreshResult {
        private Long userId;
        private String familyId;
        private String newRefreshToken;
        private boolean reuseDetected;

        public static RefreshResult success(Long userId, String familyId, String newToken) {
            return new RefreshResult(userId, familyId, newToken, false);
        }

        public static RefreshResult reuseDetected(Long userId, String familyId) {
            return new RefreshResult(userId, familyId, null, true);
        }
    }
}
