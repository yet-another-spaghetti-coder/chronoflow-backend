package nus.edu.u.user.controller.auth;

import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_COOKIE_NAME;
import static nus.edu.u.common.constant.SecurityConstants.REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nus.edu.u.common.core.domain.CommonResult;
import nus.edu.u.common.enums.ErrorCodeConstants;
import nus.edu.u.common.exception.ServiceException;
import nus.edu.u.framework.security.audit.SecurityAuditLogger;
import nus.edu.u.framework.security.audit.SecurityAuditLogger.SecurityEvent;
import nus.edu.u.framework.security.ratelimit.RateLimiter;
import nus.edu.u.user.config.CookieConfig;
import nus.edu.u.user.domain.vo.auth.LoginReqVO;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;
import nus.edu.u.user.service.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private RateLimiter rateLimiter;
    @Mock private SecurityAuditLogger auditLogger;

    @Spy private CookieConfig cookieConfig = new CookieConfig();

    @InjectMocks private AuthController controller;

    @BeforeEach
    void setUp() {
        cookieConfig.setHttpOnly(true);
        cookieConfig.setSecurity(false);
        ReflectionTestUtils.setField(controller, "cookieConfig", cookieConfig);
        // F-2: tests pre-date the rate limiter; default to "allow" so the existing assertions
        // about cookie behaviour still hold. Lenient because logout / refresh don't touch
        // the rate limiter and Mockito strict mode would flag the unused stubbing.
        lenient().when(rateLimiter.isAllowed(any(), any())).thenReturn(true);
    }

    @Test
    void login_whenRememberTrue_setsLongLivedCookie() {
        LoginReqVO req =
                LoginReqVO.builder().username("user@example.com").password("password").build();
        LoginRespVO resp = new LoginRespVO();
        resp.setRefreshToken("new-refresh");
        when(authService.login(any(LoginReqVO.class))).thenReturn(resp);

        CommonResult<LoginRespVO> result = controller.login(req, "old-refresh", request, response);

        assertThat(result.getData()).isSameAs(resp);
        assertThat(req.getRefreshToken()).isEqualTo("old-refresh");

        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        Cookie cookie = cookieCaptor.getValue();
        assertThat(cookie.getName()).isEqualTo(REFRESH_TOKEN_COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo("new-refresh");
        assertThat(cookie.getMaxAge()).isEqualTo(REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        verify(authService).login(req);
    }

    @Test
    void login_whenRememberFalse_setsZeroLifeCookie() {
        LoginReqVO req =
                LoginReqVO.builder()
                        .username("user@example.com")
                        .password("password")
                        .remember(false)
                        .build();
        LoginRespVO resp = new LoginRespVO();
        resp.setRefreshToken("token");
        when(authService.login(any(LoginReqVO.class))).thenReturn(resp);

        controller.login(req, null, request, response);

        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        assertThat(cookieCaptor.getValue().getMaxAge()).isZero();
    }

    @Test
    void logout_removesRefreshTokenCookie() {
        CommonResult<Boolean> result = controller.logout("to-remove", response);

        assertThat(result.getData()).isTrue();
        verify(authService).logout("to-remove");
        ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookieCaptor.capture());
        Cookie cookie = cookieCaptor.getValue();
        assertThat(cookie.getName()).isEqualTo(REFRESH_TOKEN_COOKIE_NAME);
        assertThat(cookie.getValue()).isNull();
        assertThat(cookie.getMaxAge()).isZero();
    }

    @Test
    void refresh_returnsTokenFromService() {
        LoginRespVO resp = new LoginRespVO();
        when(authService.refresh("refresh-token")).thenReturn(resp);

        CommonResult<LoginRespVO> result = controller.refresh("refresh-token");

        assertThat(result.getData()).isSameAs(resp);
        verify(authService).refresh("refresh-token");
    }

    // F-2 / C-01: when the rate limiter rejects, throw TOO_MANY_REQUESTS and never touch
    // authService.
    @Test
    void login_whenRateLimited_throwsAndDoesNotCallAuthService() {
        LoginReqVO req =
                LoginReqVO.builder().username("user@example.com").password("password").build();
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        lenient().when(rateLimiter.isAllowed(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> controller.login(req, "old", request, response))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.TOO_MANY_REQUESTS.getCode());

        verify(authService, never()).login(any());
        verify(auditLogger)
                .log(eq(SecurityEvent.RATE_LIMIT_EXCEEDED), eq(null), eq("1.2.3.4"), eq("login"));
    }

    // extractClientIp branches — exercises the static helper through the login() entry point.
    @Test
    void login_extractClientIp_prefersFirstXForwardedFor() {
        LoginReqVO req =
                LoginReqVO.builder().username("user@example.com").password("password").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2, 10.0.0.3");
        LoginRespVO resp = new LoginRespVO();
        resp.setRefreshToken("token");
        when(authService.login(any(LoginReqVO.class))).thenReturn(resp);

        controller.login(req, null, request, response);

        verify(rateLimiter).isAllowed("login", "10.0.0.1");
    }

    @Test
    void login_extractClientIp_xForwardedForSingleValue() {
        LoginReqVO req =
                LoginReqVO.builder().username("user@example.com").password("password").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn("  192.168.1.50  ");
        LoginRespVO resp = new LoginRespVO();
        resp.setRefreshToken("token");
        when(authService.login(any(LoginReqVO.class))).thenReturn(resp);

        controller.login(req, null, request, response);

        verify(rateLimiter).isAllowed("login", "192.168.1.50");
    }

    @Test
    void login_extractClientIp_fallsBackToXRealIp() {
        LoginReqVO req =
                LoginReqVO.builder().username("user@example.com").password("password").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn("");
        when(request.getHeader("X-Real-IP")).thenReturn(" 172.16.0.5 ");
        LoginRespVO resp = new LoginRespVO();
        resp.setRefreshToken("token");
        when(authService.login(any(LoginReqVO.class))).thenReturn(resp);

        controller.login(req, null, request, response);

        verify(rateLimiter).isAllowed("login", "172.16.0.5");
    }

    @Test
    void login_extractClientIp_fallsBackToRemoteAddrWhenNoHeaders() {
        LoginReqVO req =
                LoginReqVO.builder().username("user@example.com").password("password").build();
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        LoginRespVO resp = new LoginRespVO();
        resp.setRefreshToken("token");
        when(authService.login(any(LoginReqVO.class))).thenReturn(resp);

        controller.login(req, null, request, response);

        verify(rateLimiter).isAllowed("login", "127.0.0.1");
    }
}
