package nus.edu.u.user.service.auth;

import nus.edu.u.user.domain.vo.auth.FirebaseRegisterReqVO;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;

/**
 * Firebase authentication service interface.
 * Handles Firebase ID token verification and Sa-Token session creation.
 */
public interface FirebaseAuthService {

    /**
     * Authenticate user with Firebase ID token and create Sa-Token session.
     *
     * @param firebaseIdToken Firebase ID token from client
     * @param remember Whether to create persistent refresh token
     * @param userAgent User-Agent header for fingerprinting
     * @param clientIp Client IP address for fingerprinting
     * @return Login response with user info and tokens
     */
    LoginRespVO firebaseLogin(String firebaseIdToken, boolean remember, String userAgent, String clientIp);

    /**
     * Register new user with Firebase credentials.
     *
     * @param firebaseIdToken Firebase ID token from client
     * @param reqVO Registration request with additional user info
     * @param userAgent User-Agent header for fingerprinting
     * @param clientIp Client IP address for fingerprinting
     * @return Login response with user info and tokens
     */
    LoginRespVO firebaseRegister(String firebaseIdToken, FirebaseRegisterReqVO reqVO, String userAgent, String clientIp);

    /**
     * Refresh access token with rotation and reuse detection.
     *
     * @param oldRefreshToken Current refresh token
     * @param userAgent User-Agent header for fingerprinting
     * @param clientIp Client IP address for fingerprinting
     * @return Login response with new tokens
     */
    LoginRespVO refreshWithRotation(String oldRefreshToken, String userAgent, String clientIp);

    /**
     * Enhanced logout that invalidates both Sa-Token and Firebase tokens.
     *
     * @param refreshToken Refresh token to invalidate
     */
    void enhancedLogout(String refreshToken);
}
