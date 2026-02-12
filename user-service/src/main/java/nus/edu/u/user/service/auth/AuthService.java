package nus.edu.u.user.service.auth;

import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.vo.auth.LoginReqVO;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;
import nus.edu.u.user.domain.vo.auth.TotpSetupRespVO;

/**
 * Authentication service interface
 *
 * @author Lu Shuwen
 * @date 2025-08-30
 */
public interface AuthService {

    /**
     * Select by username and match password If matched, return a UserDO object If not matched,
     * throw exception
     *
     * @param username name
     * @param password psd
     * @return UserDO
     */
    UserDO authenticate(String username, String password);

    /**
     * Login service
     *
     * @param reqVO LoginReqVO
     * @return LoginRespVO
     */
    LoginRespVO login(LoginReqVO reqVO);

    /**
     * Logout
     *
     * @param token access token
     */
    void logout(String token);

    LoginRespVO refresh(String refreshTokenVO);

    /**
     * Verify TOTP code and complete login after MFA challenge.
     *
     * @param mfaToken Temporary MFA token from login response
     * @param totpCode 6-digit TOTP code
     * @return LoginRespVO with full user info
     */
    LoginRespVO verifyTotpAndLogin(String mfaToken, String totpCode);

    /**
     * Generate TOTP setup data (QR code, secret) for current user.
     *
     * @return TOTP setup response
     */
    TotpSetupRespVO setupTotp();

    /**
     * Verify TOTP code and enable TOTP for current user.
     *
     * @param secret The secret from setup
     * @param code 6-digit TOTP code to verify
     * @return true if enabled successfully
     */
    boolean enableTotp(String secret, String code);

    /**
     * Disable TOTP for current user.
     *
     * @param code 6-digit TOTP code to verify ownership
     * @return true if disabled successfully
     */
    boolean disableTotp(String code);

    /**
     * Check if current user has TOTP enabled.
     *
     * @return true if TOTP is enabled
     */
    boolean isTotpEnabled();
}
