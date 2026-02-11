package nus.edu.u.user.service.auth;

import static nus.edu.u.common.constant.CacheConstants.USER_PERMISSION_KEY;
import static nus.edu.u.common.constant.CacheConstants.USER_ROLE_KEY;
import static nus.edu.u.common.constant.Constants.DEFAULT_DELIMITER;
import static nus.edu.u.common.constant.Constants.SESSION_TENANT_ID;
import static nus.edu.u.common.enums.ErrorCodeConstants.*;
import static nus.edu.u.common.utils.exception.ServiceExceptionUtil.exception;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.common.enums.CommonStatusEnum;
import nus.edu.u.framework.mybatis.MybatisPlusConfig;
import nus.edu.u.framework.security.audit.SecurityAuditLogger;
import nus.edu.u.framework.security.audit.SecurityAuditLogger.SecurityEvent;
import nus.edu.u.user.domain.dataobject.user.UserDO;
import nus.edu.u.user.domain.dto.RoleDTO;
import nus.edu.u.user.domain.dto.UserPermissionDTO;
import nus.edu.u.user.domain.dto.UserRoleDTO;
import nus.edu.u.user.domain.vo.auth.FirebaseRegisterReqVO;
import nus.edu.u.user.domain.vo.auth.LoginRespVO;
import nus.edu.u.user.domain.vo.auth.UserVO;
import nus.edu.u.user.domain.vo.role.RoleRespVO;
import nus.edu.u.user.service.role.RoleService;
import nus.edu.u.user.service.user.UserService;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Firebase authentication service implementation.
 * Verifies Firebase ID tokens and creates Sa-Token sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthServiceImpl implements FirebaseAuthService {

    private final UserService userService;
    private final RoleService roleService;
    private final EnhancedTokenService tokenService;
    private final FirebaseUserMappingService firebaseMappingService;
    private final SecurityAuditLogger auditLogger;

    @Override
    public LoginRespVO firebaseLogin(
            String firebaseIdToken, boolean remember, String userAgent, String clientIp) {
        try {
            // Verify Firebase ID Token (RS256 signature, expiry, audience, revocation)
            FirebaseToken decodedToken =
                    FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken, true);
            String firebaseUid = decodedToken.getUid();
            String email = decodedToken.getEmail();
            boolean emailVerified = decodedToken.isEmailVerified();
            String signInProvider = getSignInProvider(decodedToken);

            // Enforce email verification (skip for SSO providers - they verify identity themselves)
            if (!isSsoProvider(signInProvider) && !emailVerified) {
                auditLogger.log(
                        SecurityEvent.LOGIN_FAILED_EMAIL_NOT_VERIFIED, firebaseUid, clientIp, email);
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }

            // Map Firebase UID to internal user
            Long internalUserId = firebaseMappingService.getInternalUserId(firebaseUid);
            if (internalUserId == null) {
                // Auto-register for SSO users (social + enterprise)
                if (isSsoProvider(signInProvider)) {
                    return autoRegisterSsoUser(decodedToken, userAgent, clientIp);
                }
                auditLogger.log(
                        SecurityEvent.LOGIN_FAILED_USER_NOT_FOUND, firebaseUid, clientIp, email);
                throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
            }

            // Bypass tenant filter since user is not authenticated yet
            UserDO userDO = MybatisPlusConfig.executeWithoutTenantFilter(
                    () -> userService.selectUserById(internalUserId));
            if (userDO == null) {
                throw exception(ACCOUNT_ERROR);
            }
            if (CommonStatusEnum.isDisable(userDO.getStatus())) {
                auditLogger.log(
                        SecurityEvent.LOGIN_FAILED_ACCOUNT_DISABLED, firebaseUid, clientIp, email);
                throw exception(AUTH_LOGIN_USER_DISABLED);
            }

            return createSession(userDO, remember, userAgent, clientIp, firebaseUid);

        } catch (FirebaseAuthException e) {
            log.warn("Firebase token verification failed: {}", e.getMessage());
            auditLogger.log(
                    SecurityEvent.LOGIN_FAILED_INVALID_TOKEN, null, clientIp, e.getMessage());
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRespVO firebaseRegister(
            String firebaseIdToken,
            FirebaseRegisterReqVO reqVO,
            String userAgent,
            String clientIp) {
        try {
            FirebaseToken decodedToken =
                    FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken, true);
            String firebaseUid = decodedToken.getUid();
            String email = decodedToken.getEmail();

            // Check if already registered
            Long existingUserId = firebaseMappingService.getInternalUserId(firebaseUid);
            if (existingUserId != null) {
                UserDO existingUser = MybatisPlusConfig.executeWithoutTenantFilter(
                        () -> userService.selectUserById(existingUserId));
                return createSession(existingUser, true, userAgent, clientIp, firebaseUid);
            }

            // Check if email already exists (link accounts) - bypass tenant filter
            UserDO existingByEmail = MybatisPlusConfig.executeWithoutTenantFilter(
                    () -> userService.getUserByEmail(email));
            if (existingByEmail != null) {
                firebaseMappingService.createMapping(firebaseUid, existingByEmail.getId());
                MybatisPlusConfig.executeWithoutTenantFilter(
                        () -> { userService.updateFirebaseUid(existingByEmail.getId(), firebaseUid); return null; });
                return createSession(existingByEmail, true, userAgent, clientIp, firebaseUid);
            }

            // Create new user - bypass tenant filter
            UserDO newUser = MybatisPlusConfig.executeWithoutTenantFilter(
                    () -> userService.createUserFromFirebase(
                            firebaseUid, email, reqVO.getName(), reqVO.getOrganizationName()));
            firebaseMappingService.createMapping(firebaseUid, newUser.getId());
            auditLogger.log(SecurityEvent.USER_REGISTERED, firebaseUid, clientIp, email);
            return createSession(newUser, true, userAgent, clientIp, firebaseUid);

        } catch (FirebaseAuthException e) {
            log.warn("Firebase token verification failed during registration: {}", e.getMessage());
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
    }

    @Override
    public LoginRespVO refreshWithRotation(
            String oldRefreshToken, String userAgent, String clientIp) {
        // If already logged in, validate fingerprint and return info
        if (StpUtil.isLogin()) {
            String currentToken = StpUtil.getTokenValue();
            String expectedFingerprint = generateFingerprint(userAgent, clientIp);
            String storedFingerprint = tokenService.getFingerprint(currentToken);
            if (storedFingerprint != null && !expectedFingerprint.equals(storedFingerprint)) {
                auditLogger.log(
                        SecurityEvent.TOKEN_FINGERPRINT_MISMATCH,
                        StpUtil.getLoginIdAsString(),
                        clientIp,
                        "Fingerprint mismatch");
            }
            return buildLoginResponse(StpUtil.getLoginIdAsLong(), oldRefreshToken);
        }

        if (StrUtil.isEmpty(oldRefreshToken)) {
            throw exception(REFRESH_TOKEN_WRONG);
        }

        // Rotate refresh token with reuse detection
        EnhancedTokenService.RefreshResult result =
                tokenService.rotateRefreshToken(oldRefreshToken);
        if (result == null) {
            throw exception(REFRESH_TOKEN_WRONG);
        }

        // Handle token reuse attack
        if (result.isReuseDetected()) {
            auditLogger.log(
                    SecurityEvent.REFRESH_TOKEN_REUSE_DETECTED,
                    result.getUserId().toString(),
                    clientIp,
                    "Family: " + result.getFamilyId());
            tokenService.invalidateTokenFamily(result.getFamilyId());
            StpUtil.logout(result.getUserId());
            throw exception(EXPIRED_LOGIN_CREDENTIALS);
        }

        // Create new session
        StpUtil.login(result.getUserId());
        loadPermissionsIntoSession(result.getUserId());
        String fingerprint = generateFingerprint(userAgent, clientIp);
        tokenService.storeFingerprint(StpUtil.getTokenValue(), fingerprint);
        auditLogger.log(
                SecurityEvent.TOKEN_REFRESHED, result.getUserId().toString(), clientIp, null);
        return buildLoginResponse(result.getUserId(), result.getNewRefreshToken());
    }

    @Override
    public void enhancedLogout(String refreshToken) {
        if (StpUtil.isLogin()) {
            Long userId = StpUtil.getLoginIdAsLong();
            String firebaseUid = firebaseMappingService.getFirebaseUid(userId);

            // Revoke Firebase tokens
            if (StrUtil.isNotEmpty(firebaseUid)) {
                try {
                    FirebaseAuth.getInstance().revokeRefreshTokens(firebaseUid);
                } catch (FirebaseAuthException e) {
                    log.warn("Failed to revoke Firebase tokens: {}", e.getMessage());
                }
            }

            tokenService.removeFingerprint(StpUtil.getTokenValue());
            auditLogger.log(SecurityEvent.LOGOUT, userId.toString(), null, null);
        }

        if (StrUtil.isNotEmpty(refreshToken)) {
            tokenService.removeTokenAndFamily(refreshToken);
        }

        StpUtil.logout();
    }

    /**
     * Create Sa-Token session for authenticated user.
     */
    private LoginRespVO createSession(
            UserDO userDO,
            boolean remember,
            String userAgent,
            String clientIp,
            String firebaseUid) {
        StpUtil.login(userDO.getId());
        StpUtil.getSession().set(SESSION_TENANT_ID, userDO.getTenantId());
        loadPermissionsIntoSession(userDO.getId());

        String fingerprint = generateFingerprint(userAgent, clientIp);
        tokenService.storeFingerprint(StpUtil.getTokenValue(), fingerprint);

        String refreshToken = remember ? tokenService.createRefreshTokenWithFamily(userDO.getId()) : null;

        auditLogger.log(SecurityEvent.LOGIN_SUCCESS, firebaseUid, clientIp, userDO.getEmail());
        return buildLoginResponse(userDO.getId(), refreshToken);
    }

    /**
     * Auto-register Google SSO users.
     */
    @Transactional(rollbackFor = Exception.class)
    protected LoginRespVO autoRegisterSsoUser(
            FirebaseToken decodedToken, String userAgent, String clientIp) {
        String firebaseUid = decodedToken.getUid();
        String email = decodedToken.getEmail();
        String displayName = decodedToken.getName();

        // Check if email already exists - bypass tenant filter since user is not authenticated
        UserDO existingUser = MybatisPlusConfig.executeWithoutTenantFilter(
                () -> userService.getUserByEmail(email));
        if (existingUser != null) {
            firebaseMappingService.createMapping(firebaseUid, existingUser.getId());
            MybatisPlusConfig.executeWithoutTenantFilter(
                    () -> { userService.updateFirebaseUid(existingUser.getId(), firebaseUid); return null; });
            return createSession(existingUser, true, userAgent, clientIp, firebaseUid);
        }

        // Create new user - bypass tenant filter for user creation
        UserDO newUser = MybatisPlusConfig.executeWithoutTenantFilter(
                () -> userService.createUserFromFirebase(
                        firebaseUid,
                        email,
                        displayName != null ? displayName : email.split("@")[0],
                        null));
        firebaseMappingService.createMapping(firebaseUid, newUser.getId());
        auditLogger.log(
                SecurityEvent.USER_REGISTERED, firebaseUid, clientIp, "SSO auto-register: " + email);
        return createSession(newUser, true, userAgent, clientIp, firebaseUid);
    }

    /**
     * Load user roles and permissions into Sa-Token session.
     */
    private void loadPermissionsIntoSession(Long userId) {
        UserRoleDTO userRoleDTO = userService.selectUserWithRole(userId);
        List<UserPermissionDTO> userPermissionList = userService.getUserPermissions(userId);

        if (userRoleDTO == null) {
            return;
        }

        SaSession session = StpUtil.getSession();
        session.set(
                USER_ROLE_KEY,
                userRoleDTO.getRoles().stream()
                        .map(RoleDTO::getRoleKey)
                        .collect(Collectors.toList()));
        session.set(
                USER_PERMISSION_KEY,
                userPermissionList.stream()
                        .map(UserPermissionDTO::getPermissionKey)
                        .collect(Collectors.toList()));
    }

    /**
     * Build login response with user info and roles.
     */
    private LoginRespVO buildLoginResponse(Long userId, String refreshToken) {
        UserRoleDTO userRoleDTO = userService.selectUserWithRole(userId);
        if (userRoleDTO == null) {
            throw exception(ACCOUNT_ERROR);
        }

        UserVO userVO =
                UserVO.builder()
                        .id(userRoleDTO.getUserId())
                        .email(userRoleDTO.getEmail())
                        .name(userRoleDTO.getUsername())
                        .role(
                                userRoleDTO.getRoles().stream()
                                        .map(RoleDTO::getRoleKey)
                                        .collect(Collectors.joining(DEFAULT_DELIMITER)))
                        .build();

        List<RoleRespVO> roleRespVOList =
                userRoleDTO.getRoles().stream()
                        .map(role -> roleService.getRole(role.getId()))
                        .filter(ObjUtil::isNotNull)
                        .toList();

        return LoginRespVO.builder()
                .refreshToken(refreshToken)
                .user(userVO)
                .roles(roleRespVOList)
                .build();
    }

    /**
     * Generate fingerprint from User-Agent and IP.
     */
    private String generateFingerprint(String userAgent, String clientIp) {
        String raw =
                (userAgent != null ? userAgent : "unknown")
                        + "|"
                        + (clientIp != null ? clientIp : "unknown");
        return DigestUtils.sha256Hex(raw);
    }

    /**
     * Extract sign-in provider from Firebase token claims.
     */
    @SuppressWarnings("unchecked")
    private String getSignInProvider(FirebaseToken token) {
        Object firebase = token.getClaims().get("firebase");
        if (firebase instanceof Map) {
            Map<String, Object> firebaseClaims = (Map<String, Object>) firebase;
            Object provider = firebaseClaims.get("sign_in_provider");
            return provider != null ? provider.toString() : "unknown";
        }
        return "unknown";
    }

    /**
     * Check if the sign-in provider is a trusted SSO provider that allows auto-registration.
     * Includes social providers and enterprise SSO (SAML/OIDC).
     */
    private boolean isSsoProvider(String signInProvider) {
        if (signInProvider == null) {
            return false;
        }
        // Social SSO providers
        if (signInProvider.equals("google.com")
                || signInProvider.equals("facebook.com")
                || signInProvider.equals("apple.com")
                || signInProvider.equals("microsoft.com")
                || signInProvider.equals("twitter.com")
                || signInProvider.equals("github.com")) {
            return true;
        }
        // Enterprise SSO providers (SAML and OIDC)
        // SAML providers have format: saml.{provider-id}
        // OIDC providers have format: oidc.{provider-id}
        if (signInProvider.startsWith("saml.") || signInProvider.startsWith("oidc.")) {
            return true;
        }
        return false;
    }
}
