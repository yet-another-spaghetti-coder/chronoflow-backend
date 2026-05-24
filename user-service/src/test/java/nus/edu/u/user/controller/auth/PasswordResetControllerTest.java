package nus.edu.u.user.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.user.domain.vo.auth.ForgotPasswordReqVO;
import nus.edu.u.user.domain.vo.auth.ResetPasswordReqVO;
import nus.edu.u.user.service.auth.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock private PasswordResetService passwordResetService;
    @Mock private HttpServletRequest request;

    @InjectMocks private PasswordResetController controller;

    // --- forgotPassword --------------------------------------------------

    @Test
    void forgotPassword_returnsGenericMessageRegardlessOfAccountExistence() {
        ForgotPasswordReqVO req = ForgotPasswordReqVO.builder().email("alice@example.com").build();
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        CommonResult<String> result = controller.forgotPassword(req, request);

        assertThat(result.getData())
                .isEqualTo("If an account exists for this email, a reset link has been sent.");
        verify(passwordResetService).requestReset("alice@example.com", "10.0.0.1");
    }

    @Test
    void forgotPassword_passesClientIpFromXForwardedFor() {
        ForgotPasswordReqVO req = ForgotPasswordReqVO.builder().email("bob@example.com").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1");

        controller.forgotPassword(req, request);

        verify(passwordResetService).requestReset(eq("bob@example.com"), eq("203.0.113.5"));
    }

    // --- resetPassword ---------------------------------------------------

    @Test
    void resetPassword_delegatesToServiceAndReturnsTrue() {
        ResetPasswordReqVO req =
                ResetPasswordReqVO.builder()
                        .token("abc123")
                        .newPassword("Strong-Pass-2026!")
                        .build();
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");

        CommonResult<Boolean> result = controller.resetPassword(req, request);

        assertThat(result.getData()).isTrue();
        verify(passwordResetService).resetPassword("abc123", "Strong-Pass-2026!", "10.0.0.2");
    }

    // --- extractClientIp branches (exercised through the controller's public methods) --

    @Test
    void resetPassword_extractClientIp_xForwardedForSingleValue() {
        ResetPasswordReqVO req =
                ResetPasswordReqVO.builder().token("t1").newPassword("Strong-Pass-2026!").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn("  198.51.100.7  ");

        controller.resetPassword(req, request);

        verify(passwordResetService)
                .resetPassword(eq("t1"), eq("Strong-Pass-2026!"), eq("198.51.100.7"));
    }

    @Test
    void resetPassword_extractClientIp_fallsBackToXRealIp() {
        ResetPasswordReqVO req =
                ResetPasswordReqVO.builder().token("t2").newPassword("Strong-Pass-2026!").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn(" 172.16.0.7 ");

        controller.resetPassword(req, request);

        verify(passwordResetService)
                .resetPassword(eq("t2"), eq("Strong-Pass-2026!"), eq("172.16.0.7"));
    }

    @Test
    void resetPassword_extractClientIp_fallsBackToRemoteAddrWhenNoHeaders() {
        ResetPasswordReqVO req =
                ResetPasswordReqVO.builder().token("t3").newPassword("Strong-Pass-2026!").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        controller.resetPassword(req, request);

        verify(passwordResetService)
                .resetPassword(eq("t3"), eq("Strong-Pass-2026!"), eq("127.0.0.1"));
    }
}
