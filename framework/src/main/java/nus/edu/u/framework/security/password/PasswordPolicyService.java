package nus.edu.u.framework.security.password;

import static nus.edu.u.common.enums.ErrorCodeConstants.PASSWORD_POLICY_VIOLATION;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception0;

/**
 * Enforces password-policy rules that the {@code @StrongPassword} field-level annotation cannot
 * express because they require other fields (the user's email / username).
 *
 * <p>This is the second half of SR-A-07. Call from the service layer immediately before
 * persisting the new password. Throws a {@link nus.edu.u.common.exception.ServiceException}
 * carrying {@code PASSWORD_POLICY_VIOLATION} so the global handler returns a clean 400 to the
 * client without leaking a stack trace.
 *
 * <p>Registered as a Spring bean via {@code SecurityAutoConfiguration}.
 */
public class PasswordPolicyService {

    /**
     * Substring match only fires above this length. Shorter identity strings produce too many
     * false positives — e.g. an email local-part of "test", "info", "admin", or "bob" would
     * otherwise reject any password containing those four letters. Equality still applies at
     * any length.
     */
    private static final int SUBSTRING_CHECK_MIN_LENGTH = 5;

    /**
     * Reject a password that is identical to, or trivially derived from, the user's identity
     * (username, full email, or the local-part of the email).
     *
     * <ul>
     *   <li>Password equals the username (case-insensitive) → reject.
     *   <li>Password contains the username AND the username is ≥ {@value
     *       #SUBSTRING_CHECK_MIN_LENGTH} characters → reject.
     *   <li>Password equals the full email or just the local-part → reject.
     *   <li>Password contains the local-part AND the local-part is ≥ {@value
     *       #SUBSTRING_CHECK_MIN_LENGTH} characters → reject.
     * </ul>
     */
    public void assertNotIdentity(String rawPassword, String username, String email) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return;
        }
        String pwd = rawPassword.toLowerCase();

        if (username != null && !username.isBlank()) {
            String u = username.toLowerCase();
            boolean matchesUsername =
                    pwd.equals(u)
                            || (u.length() >= SUBSTRING_CHECK_MIN_LENGTH && pwd.contains(u));
            if (matchesUsername) {
                throw exception0(
                        PASSWORD_POLICY_VIOLATION.getCode(),
                        "Password must not contain the username");
            }
        }
        if (email != null && !email.isBlank()) {
            String fullEmail = email.toLowerCase();
            String localPart = fullEmail;
            int at = fullEmail.indexOf('@');
            if (at > 0) {
                localPart = fullEmail.substring(0, at);
            }
            boolean equalsEmail = pwd.equals(fullEmail) || pwd.equals(localPart);
            boolean containsLocalPart =
                    !localPart.isEmpty()
                            && localPart.length() >= SUBSTRING_CHECK_MIN_LENGTH
                            && pwd.contains(localPart);
            if (equalsEmail || containsLocalPart) {
                throw exception0(
                        PASSWORD_POLICY_VIOLATION.getCode(),
                        "Password must not contain the email address");
            }
        }
    }
}
