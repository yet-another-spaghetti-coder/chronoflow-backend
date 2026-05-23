package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.core.domain.CommonResult.success;

import cn.dev33.satoken.annotation.SaIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.user.domain.vo.auth.ForgotPasswordReqVO;
import nus.edu.u.user.domain.vo.auth.ResetPasswordReqVO;
import nus.edu.u.user.service.auth.PasswordResetService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password reset controller.
 *
 * <p>Implements SR-A-06: self-service password reset via single-use, time-limited tokens.
 */
@Tag(name = "Password Reset Controller")
@RestController
@RequestMapping("/users/auth")
@Validated
@Slf4j
public class PasswordResetController {

    private static final String GENERIC_FORGOT_RESPONSE =
            "If an account exists for this email, a reset link has been sent.";

    @Resource private PasswordResetService passwordResetService;

    @SaIgnore
    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link")
    public CommonResult<String> forgotPassword(
            @RequestBody @Valid ForgotPasswordReqVO reqVO, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        passwordResetService.requestReset(reqVO.getEmail(), clientIp);
        // The response is identical whether or not the email exists, to prevent enumeration.
        return success(GENERIC_FORGOT_RESPONSE);
    }

    @SaIgnore
    @PostMapping("/reset-password")
    @Operation(summary = "Consume reset token and set a new password")
    public CommonResult<Boolean> resetPassword(
            @RequestBody @Valid ResetPasswordReqVO reqVO, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        passwordResetService.resetPassword(reqVO.getToken(), reqVO.getNewPassword(), clientIp);
        return success(true);
    }

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
