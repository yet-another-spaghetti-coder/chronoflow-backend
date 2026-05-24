package nus.edu.u.user.service.auth;

/**
 * Password reset service interface.
 *
 * <p>Implements the self-service password-reset flow (SR-A-06 / C-16): a user submits their email,
 * the system issues a single-use, time-limited token bound to the account, the link is delivered to
 * the registered email, and the user supplies the token together with a new password to complete
 * the reset.
 */
public interface PasswordResetService {

    /**
     * Initiate a password reset. Always behaves the same way regardless of whether {@code email}
     * exists in the system, to avoid leaking account presence (T-17).
     *
     * @param email the email submitted by the user
     * @param clientIp client IP for audit
     */
    void requestReset(String email, String clientIp);

    /**
     * Consume a previously issued reset token and update the user's password. The token is
     * single-use: it is deleted atomically with the password update.
     *
     * @param token the single-use reset token from the email link
     * @param newPassword the new password (already validated for length at the controller layer)
     * @param clientIp client IP for audit
     */
    void resetPassword(String token, String newPassword, String clientIp);
}
