package nus.edu.u.user.service.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import nus.edu.u.common.enums.ErrorCodeConstants;
import nus.edu.u.common.exception.ServiceException;
import nus.edu.u.framework.security.audit.SecurityAuditLogger;
import nus.edu.u.framework.security.audit.SecurityAuditLogger.SecurityEvent;
import nus.edu.u.user.service.role.RoleService;
import nus.edu.u.user.service.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Targeted coverage for the F-4 / C-03 fingerprint-mismatch enforcement path in {@link
 * FirebaseAuthServiceImpl#refreshWithRotation(String, String, String)}.
 *
 * <p>The happy-path / Firebase token verification paths require a live Firebase SDK and aren't
 * unit-testable, so this class covers only the branches we can drive without static mocking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirebaseAuthServiceImplTest {

    @Mock private UserService userService;
    @Mock private RoleService roleService;
    @Mock private EnhancedTokenService tokenService;
    @Mock private FirebaseUserMappingService firebaseMappingService;
    @Mock private SecurityAuditLogger auditLogger;

    private FirebaseAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new FirebaseAuthServiceImpl(
                        userService,
                        roleService,
                        tokenService,
                        firebaseMappingService,
                        auditLogger);
        SaTokenContextMockUtil.setMockContext();
    }

    @AfterEach
    void tearDown() {
        try {
            if (StpUtil.isLogin()) {
                StpUtil.logout();
            }
        } catch (Exception ignored) {
        }
        SaTokenContextMockUtil.clearContext();
    }

    // F-4 / C-03: stored fingerprint != expected → revoke family, drop fingerprint,
    // logout, audit, throw EXPIRED_LOGIN_CREDENTIALS.
    @Test
    void refreshWithRotation_fingerprintMismatch_revokesFamilyAndThrows() {
        StpUtil.login(77L);
        String tokenValue = StpUtil.getTokenValue();
        // Stored fingerprint that intentionally differs from what generateFingerprint(...) returns.
        when(tokenService.getFingerprint(tokenValue))
                .thenReturn("definitely-not-the-expected-hash");

        assertThatThrownBy(
                        () -> service.refreshWithRotation("old-refresh", "Mozilla/5.0", "10.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ErrorCodeConstants.EXPIRED_LOGIN_CREDENTIALS.getCode());

        verify(auditLogger)
                .log(
                        eq(SecurityEvent.TOKEN_FINGERPRINT_MISMATCH),
                        eq("77"),
                        eq("10.0.0.1"),
                        contains("fingerprint mismatch"));
        verify(tokenService).removeTokenAndFamily("old-refresh");
        verify(tokenService).removeFingerprint(tokenValue);
        // Rotation must NOT happen on mismatch.
        verify(tokenService, never()).rotateRefreshToken(any());
    }

    // Stored fingerprint is null → branch is skipped (older sessions without a stored fingerprint
    // are allowed through to the normal flow). We exercise this branch by asserting we DON'T
    // revoke the family in that case.
    @Test
    void refreshWithRotation_storedFingerprintNull_doesNotRevokeOnRefresh() {
        StpUtil.login(78L);
        String tokenValue = StpUtil.getTokenValue();
        when(tokenService.getFingerprint(tokenValue)).thenReturn(null);
        // Other dependencies in the buildLoginResponse path — stub the minimum needed.
        when(userService.selectUserWithRole(78L))
                .thenReturn(nus.edu.u.user.domain.dto.UserRoleDTO.builder().userId(78L).build());

        // Should not throw on the fingerprint branch; whatever happens downstream is fine —
        // we only care that the F-4 revoke path was NOT triggered.
        try {
            service.refreshWithRotation("old-refresh", "Mozilla/5.0", "10.0.0.1");
        } catch (Exception ignored) {
            // downstream may fail without full session setup; that's outside this test's scope.
        }
        verify(tokenService, never()).removeTokenAndFamily(any());
        verify(tokenService, never()).removeFingerprint(any());
    }
}
