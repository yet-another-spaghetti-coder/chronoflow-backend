package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_COOKIE_NAME;
import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE;
import static nus.edu.u.common.core.domain.CommonResult.success;

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.framework.security.audit.SecurityAuditLogger;
import nus.edu.u.framework.security.audit.SecurityAuditLogger.SecurityEvent;
import nus.edu.u.framework.security.factory.AbstractCookieFactory;
import nus.edu.u.framework.security.factory.LongLifeRefreshTokenCookie;
import nus.edu.u.framework.security.factory.ZeroLifeRefreshTokenCookie;
import nus.edu.u.framework.security.ratelimit.RateLimiter;
import nus.edu.u.user.config.CookieConfig;
import nus.edu.u.user.domain.vo.auth.FirebaseLoginReqVO;
import nus.edu.u.user.domain.vo.auth.FirebaseRegisterReqVO;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;
import nus.edu.u.user.service.auth.FirebaseAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Firebase authentication controller.
 * Provides endpoints for Firebase-based login, registration, and token refresh.
 */
@Tag(name = "Firebase Authentication Controller")
@RestController
@RequestMapping("/users/auth")
@Validated
@Slf4j
public class FirebaseAuthController {

    @Resource private FirebaseAuthService firebaseAuthService;

    @Resource private CookieConfig cookieConfig;

    @Resource private RateLimiter rateLimiter;

    @Resource private SecurityAuditLogger auditLogger;

    /**
     * Login with Firebase ID token.
     * The Firebase ID token should be passed in the Authorization header as Bearer token.
     */
    @SaIgnore
    @PostMapping("/firebase-login")
    @Operation(summary = "Login with Firebase ID token")
    public CommonResult<LoginRespVO> firebaseLogin(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestBody(required = false) FirebaseLoginReqVO reqVO,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Rate limiting
        if (!rateLimiter.isAllowed("firebase-login", clientIp)) {
            auditLogger.log(SecurityEvent.RATE_LIMIT_EXCEEDED, null, clientIp, "firebase-login");
            throw new RuntimeException("Too many login attempts. Please try again later.");
        }

        String idToken = extractBearerToken(authHeader);
        boolean remember = reqVO != null && reqVO.isRemember();

        LoginRespVO loginRespVO =
                firebaseAuthService.firebaseLogin(idToken, remember, userAgent, clientIp);
        setCookies(response, loginRespVO, remember);
        return success(loginRespVO);
    }

    /**
     * Register with Firebase credentials.
     * The Firebase ID token should be passed in the Authorization header as Bearer token.
     */
    @SaIgnore
    @PostMapping("/firebase-register")
    @Operation(summary = "Register with Firebase credentials")
    public CommonResult<LoginRespVO> firebaseRegister(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
            @RequestBody @Valid FirebaseRegisterReqVO reqVO,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Rate limiting
        if (!rateLimiter.isAllowed("firebase-register", clientIp)) {
            auditLogger.log(SecurityEvent.RATE_LIMIT_EXCEEDED, null, clientIp, "firebase-register");
            throw new RuntimeException("Too many registration attempts.");
        }

        String idToken = extractBearerToken(authHeader);
        LoginRespVO loginRespVO =
                firebaseAuthService.firebaseRegister(idToken, reqVO, userAgent, clientIp);
        setCookies(response, loginRespVO, true);
        return success(loginRespVO);
    }

    /**
     * Refresh access token with rotation.
     * Uses enhanced token rotation with reuse detection.
     */
    @SaIgnore
    @PostMapping("/firebase-refresh")
    @Operation(summary = "Refresh access token with rotation")
    public CommonResult<LoginRespVO> refreshWithRotation(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        LoginRespVO loginRespVO =
                firebaseAuthService.refreshWithRotation(refreshToken, userAgent, clientIp);
        setCookies(response, loginRespVO, true);
        return success(loginRespVO);
    }

    /**
     * Enhanced logout that invalidates both Sa-Token and Firebase tokens.
     */
    @PostMapping("/firebase-logout")
    @Operation(summary = "Logout and invalidate all tokens")
    public CommonResult<Boolean> enhancedLogout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {

        firebaseAuthService.enhancedLogout(refreshToken);

        // Clear cookie
        AbstractCookieFactory cookieFactory =
                new ZeroLifeRefreshTokenCookie(cookieConfig.isHttpOnly(), cookieConfig.isSecurity());
        response.addCookie(cookieFactory.createCookie(null));

        return success(true);
    }

    /**
     * Extract Bearer token from Authorization header.
     */
    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    /**
     * Set refresh token cookie based on remember preference.
     */
    private void setCookies(HttpServletResponse response, LoginRespVO loginRespVO, boolean remember) {
        if (loginRespVO.getRefreshToken() != null) {
            AbstractCookieFactory cookieFactory;
            if (remember) {
                cookieFactory =
                        new LongLifeRefreshTokenCookie(
                                cookieConfig.isHttpOnly(),
                                cookieConfig.isSecurity(),
                                REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE);
            } else {
                cookieFactory =
                        new ZeroLifeRefreshTokenCookie(
                                cookieConfig.isHttpOnly(), cookieConfig.isSecurity());
            }
            response.addCookie(cookieFactory.createCookie(loginRespVO.getRefreshToken()));
        }
    }

    /**
     * Extract client IP from request headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
