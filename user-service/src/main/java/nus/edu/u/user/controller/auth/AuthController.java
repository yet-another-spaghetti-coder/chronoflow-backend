package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_COOKIE_NAME;
import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE;
import static nus.edu.u.common.core.domain.CommonResult.error;
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
import nus.edu.u.framework.security.factory.AbstractCookieFactory;
import nus.edu.u.framework.security.factory.LongLifeRefreshTokenCookie;
import nus.edu.u.framework.security.factory.ZeroLifeRefreshTokenCookie;
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

    @SaIgnore
    @PostMapping("/login")
    @Operation(summary = "Login")
    public CommonResult<LoginRespVO> login(
            @RequestBody @Valid LoginReqVO reqVO,
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        log.info(
                "HttpServletRequest request, Login request received with remember me: {}",
                reqVO.isRemember());
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
}
