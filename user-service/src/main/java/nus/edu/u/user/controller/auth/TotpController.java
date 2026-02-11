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
import nus.edu.u.user.config.CookieConfig;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;
import nus.edu.u.user.domain.vo.auth.TotpSetupRespVO;
import nus.edu.u.user.domain.vo.auth.TotpVerifyReqVO;
import nus.edu.u.user.service.auth.AuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * TOTP (Two-Factor Authentication) Controller
 */
@Tag(name = "TOTP Controller")
@RestController
@RequestMapping("/users/auth/totp")
@Validated
@Slf4j
public class TotpController {

    @Resource
    private AuthService authService;

    @Resource
    private CookieConfig cookieConfig;

    @GetMapping("/status")
    @Operation(summary = "Check if TOTP is enabled for current user")
    public CommonResult<Boolean> getTotpStatus() {
        return success(authService.isTotpEnabled());
    }

    @PostMapping("/setup")
    @Operation(summary = "Generate TOTP setup (QR code and secret)")
    public CommonResult<TotpSetupRespVO> setupTotp() {
        return success(authService.setupTotp());
    }

    @PostMapping("/enable")
    @Operation(summary = "Verify code and enable TOTP")
    public CommonResult<Boolean> enableTotp(@RequestBody @Valid TotpVerifyReqVO reqVO) {
        return success(authService.enableTotp(reqVO.getSecret(), reqVO.getCode()));
    }

    @DeleteMapping
    @Operation(summary = "Disable TOTP")
    public CommonResult<Boolean> disableTotp(@RequestBody @Valid TotpVerifyReqVO reqVO) {
        return success(authService.disableTotp(reqVO.getCode()));
    }

    @SaIgnore
    @PostMapping("/verify")
    @Operation(summary = "Verify TOTP code during login (MFA)")
    public CommonResult<LoginRespVO> verifyTotp(
            @RequestBody @Valid TotpVerifyReqVO reqVO,
            HttpServletResponse response) {
        LoginRespVO loginRespVO = authService.verifyTotpAndLogin(reqVO.getMfaToken(), reqVO.getCode());

        // Set refresh token cookie
        if (loginRespVO.getRefreshToken() != null) {
            AbstractCookieFactory cookieFactory = new LongLifeRefreshTokenCookie(
                    cookieConfig.isHttpOnly(),
                    cookieConfig.isSecurity(),
                    REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE);
            response.addCookie(cookieFactory.createCookie(loginRespVO.getRefreshToken()));
        }

        return success(loginRespVO);
    }
}
