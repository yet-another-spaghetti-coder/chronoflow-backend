package nus.edu.u.common.constant;

/**
 * Constants for security
 *
 * @author Lu Shuwen
 * @date 2025-08-28
 */
public class SecurityConstants {
    /** Jwt token payload: user id */
    public static final String JWT_PAYLOAD_USER_ID = "id";

    /** Jwt token payload: role id */
    public static final String JWT_PAYLOAD_ROLE_ID = "roleId";

    /** Jwt token payload: tenant id */
    public static final String JWT_PAYLOAD_TENANT_ID = "tenantId";

    /** Jwt token payload: expire time */
    public static final String JWT_PAYLOAD_EXPIRE_TIME = "expire";

    /** Cookie name of refresh token */
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    /** Refresh token cookie path */
    public static final String REFRESH_TOKEN_COOKIE_PATH = "/";

    /** Refresh token cookie max age for remember choice */
    public static final int REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;

    public static final String MOBILE_SSO_JWT_ISSUER = "https://accounts.google.com";
}
