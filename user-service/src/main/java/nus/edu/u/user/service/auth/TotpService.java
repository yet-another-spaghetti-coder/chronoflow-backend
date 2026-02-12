package nus.edu.u.user.service.auth;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * TOTP (Time-based One-Time Password) service for two-factor authentication.
 */
@Service
@Slf4j
public class TotpService {

    private static final String ISSUER = "Chronoflow";
    private static final int SECRET_LENGTH = 32;
    private static final String MFA_TOKEN_KEY = "auth:mfa_token:";
    private static final long MFA_TOKEN_EXPIRE_SECONDS = 300; // 5 minutes

    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;
    private final StringRedisTemplate redisTemplate;

    public TotpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
        this.qrGenerator = new ZxingPngQrGenerator();

        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    }

    /**
     * Generate a new TOTP secret.
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Generate QR code data URI for the given secret and user email.
     * Returns a base64-encoded PNG image as a data URI.
     */
    public String generateQrCodeDataUri(String secret, String email) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] imageData = qrGenerator.generate(data);
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            return "data:image/png;base64," + base64Image;
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Generate the TOTP URI for manual entry in authenticator apps.
     */
    public String generateTotpUri(String secret, String email) {
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                ISSUER, email, secret, ISSUER
        );
    }

    /**
     * Verify a TOTP code against the secret.
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        return codeVerifier.isValidCode(secret, code);
    }

    /**
     * Create a temporary MFA token for pending authentication.
     * This token is used between password verification and TOTP verification.
     *
     * @param userId User ID awaiting MFA
     * @param rememberMe Remember me flag
     * @return MFA token
     */
    public String createMfaToken(Long userId, boolean rememberMe) {
        String token = UUID.randomUUID().toString();
        String value = userId + ":" + rememberMe;
        redisTemplate.opsForValue().set(
                MFA_TOKEN_KEY + token,
                value,
                MFA_TOKEN_EXPIRE_SECONDS,
                TimeUnit.SECONDS
        );
        log.debug("Created MFA token for userId={}", userId);
        return token;
    }

    /**
     * Get user ID from MFA token and remove it.
     *
     * @param mfaToken MFA token
     * @return MfaTokenData containing userId and rememberMe flag, or null if invalid
     */
    public MfaTokenData consumeMfaToken(String mfaToken) {
        if (mfaToken == null) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(MFA_TOKEN_KEY + mfaToken);
        if (value == null) {
            return null;
        }
        // Delete token after use (one-time use)
        redisTemplate.delete(MFA_TOKEN_KEY + mfaToken);

        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            Long userId = Long.parseLong(parts[0]);
            boolean rememberMe = Boolean.parseBoolean(parts[1]);
            return new MfaTokenData(userId, rememberMe);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Data class for MFA token contents.
     */
    public record MfaTokenData(Long userId, boolean rememberMe) {}
}
