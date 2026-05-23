package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_COOKIE_NAME;
import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE;
import static nus.edu.u.common.core.domain.CommonResult.error;
import static nus.edu.u.common.core.domain.CommonResult.success;
import static nus.edu.u.common.enums.ErrorCodeConstants.TOO_MANY_REQUESTS;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

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
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.vo.auth.LoginReqVO;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;
import nus.edu.u.user.service.auth.AuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller
 *
 * @author Lu Shuwen
 * @date 2025-08-30
 */
@Tag(name = "Authentication Controller")
@RestController
@RequestMapping("/users/auth")
@Validated
@Slf4j
public class AuthController {

    @Resource private AuthService authService;

    @Resource private CookieConfig cookieConfig;

    @Resource private RateLimiter rateLimiter;

    @Resource private SecurityAuditLogger auditLogger;

    @SaIgnore
    @PostMapping("/login")
    @Operation(summary = "Login")
    public CommonResult<LoginRespVO> login(
            @RequestBody @Valid LoginReqVO reqVO,
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info(
                "HttpServletRequest request, Login request received with remember me: {}",
                reqVO.isRemember());

        // F-2 / C-01: per-IP sliding-window rate limit. Identical defaults to Firebase login.
        // Default policy is 10 attempts per 15 minutes per client IP (configurable via
        // chronoflow.security.rate-limit.*). Audit + reject 429-equivalent on overflow.
        String clientIp = extractClientIp(request);
        if (!rateLimiter.isAllowed("login", clientIp)) {
            auditLogger.log(SecurityEvent.RATE_LIMIT_EXCEEDED, null, clientIp, "login");
            // Use the existing TOO_MANY_REQUESTS error code so the user sees a clean message
            // (the upstream pattern of `throw new RuntimeException(...)` gets swallowed by
            // the global handler's defaultExceptionHandler into "System error").
            throw exception(TOO_MANY_REQUESTS);
        }

        reqVO.setRefreshToken(refreshToken);
        LoginRespVO loginRespVO = authService.login(reqVO);
        AbstractCookieFactory cookieFactory;
        if (reqVO.isRemember()) {
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
        return success(loginRespVO);
    }

    @SaIgnore
    @PostMapping("/exchangeToken")
    @Operation(summary = "Exchange Mobile SSO token with backend OTT")
    public CommonResult<String> ssoLogin(HttpServletRequest request, HttpServletResponse response) {
        try {
            String token = request.getHeader("Authorization").replace("Bearer", "").strip();
            UserDO userDo = authService.mobileSsoLogin(token);
            String oneTimeToken = authService.generateOTT(userDo.getId());
            return success(oneTimeToken);
        } catch (Exception e) {
            return error(e.hashCode(), e.getMessage());
        }
    }

    @SaIgnore
    @PostMapping("/validateOTT")
    @Operation(summary = "Validate OTT for showing protected Webview")
    public CommonResult<LoginRespVO> validateOTT(
            @RequestBody String ott, HttpServletResponse response) {
        try {
            LoginRespVO loginRespVO = authService.validateOTT(ott.replace("\"", ""));
            AbstractCookieFactory cookieFactory;
            cookieFactory =
                    new LongLifeRefreshTokenCookie(
                            cookieConfig.isHttpOnly(),
                            cookieConfig.isSecurity(),
                            REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE);
            response.addCookie(cookieFactory.createCookie(loginRespVO.getRefreshToken()));
            return success(loginRespVO);
        } catch (Exception e) {
            return error(e.hashCode(), e.getMessage());
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout")
    public CommonResult<Boolean> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        // Delete refresh token from cookie
        AbstractCookieFactory cookieFactory =
                new ZeroLifeRefreshTokenCookie(
                        cookieConfig.isHttpOnly(), cookieConfig.isSecurity());
        response.addCookie(cookieFactory.createCookie(null));
        return success(true);
    }

    @SaIgnore
    @PostMapping("/refresh")
    @Operation(summary = "Refresh")
    public CommonResult<LoginRespVO> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        return success(authService.refresh(refreshToken));
    }

    /** Extract client IP from common proxy headers, falling back to the socket remote address. */
    private static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
