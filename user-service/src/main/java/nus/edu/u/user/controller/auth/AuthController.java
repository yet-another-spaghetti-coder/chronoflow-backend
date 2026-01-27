package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_COOKIE_NAME;
import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE;
import static nus.edu.u.common.core.domain.CommonResult.success;

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.framework.security.factory.AbstractCookieFactory;
import nus.edu.u.framework.security.factory.LongLifeRefreshTokenCookie;
import nus.edu.u.framework.security.factory.ZeroLifeRefreshTokenCookie;
import nus.edu.u.user.config.CookieConfig;
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
