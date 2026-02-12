# Chronoflow Authentication & Authorization Flow

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Authentication Flow](#authentication-flow)
   - [Login](#1-login-flow)
   - [Token Refresh](#2-token-refresh-flow)
   - [Logout](#3-logout-flow)
4. [Authorization Flow](#authorization-flow)
5. [Token Management](#token-management)
6. [Security Features](#security-features)
7. [API Reference](#api-reference)
8. [Error Codes](#error-codes)
9. [Configuration](#configuration)

---

## Overview

Chronoflow uses a **dual-token authentication system** built on:

| Component | Technology | Purpose |
|-----------|------------|---------|
| Access Token | Sa-Token | Short-lived API authentication |
| Refresh Token | Redis + Cookie | Long-lived session renewal |
| Password Hashing | BCrypt | Secure password storage |
| Session Storage | Redis | Distributed session management |

### Token Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│                      TOKEN LIFECYCLE                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Access Token (Sa-Token)          Refresh Token (Redis)        │
│   ┌─────────────────────┐          ┌─────────────────────┐      │
│   │ TTL: 24 hours       │          │ TTL: 7 days         │      │
│   │ Storage: Redis      │          │ Storage: Redis      │      │
│   │ Format: UUID        │          │ Format: UUID        │      │
│   │ Header: Authorization│         │ Cookie: refreshToken│      │
│   └─────────────────────┘          └─────────────────────┘      │
│                                                                 │
│   [Login] ──► Both tokens created                               │
│   [API Call] ──► Access token validated                         │
│   [Token Expired] ──► Refresh token renews access token         │
│   [Logout] ──► Both tokens invalidated                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Architecture

### Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND                                     │
│                         (React @ localhost:5173)                          │
└─────────────────────────────────┬────────────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                              GATEWAY                                      │
│                           (Port 8080)                                     │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                     Sa-Token Auth Filter                            │  │
│  │  • Validates Authorization header                                   │  │
│  │  • Checks whitelist paths                                           │  │
│  │  • Injects user context                                             │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────┬────────────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           USER SERVICE                                    │
│                           (Port 8081)                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐           │
│  │ AuthController  │  │  AuthService    │  │  TokenService   │           │
│  │ /users/auth/*   │  │  authenticate() │  │  createToken()  │           │
│  └─────────────────┘  │  login()        │  │  validateToken()│           │
│                       │  refresh()      │  │  removeToken()  │           │
│                       │  logout()       │  └─────────────────┘           │
│                       └─────────────────┘                                 │
└─────────────────────────────────┬────────────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                              REDIS                                        │
│                           (Port 6379)                                     │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  Sa-Token Sessions:  satoken:login:session:{userId}                 │  │
│  │  Refresh Tokens:     Authorization:login:refresh_token:{token}      │  │
│  │  Access Tokens:      satoken:login:token:{token}                    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `user-service/.../controller/auth/AuthController.java` | REST endpoints for auth |
| `user-service/.../service/auth/AuthServiceImpl.java` | Authentication logic |
| `user-service/.../service/auth/TokenServiceImpl.java` | Token management |
| `user-service/.../config/PasswordEncoderConfig.java` | BCrypt configuration |
| `framework/.../security/satoken/StpPermissionHandler.java` | Permission checking |
| `gateway/.../filter/AuthFilter.java` | Gateway auth filter |

---

## Authentication Flow

### 1. Login Flow

```
┌─────────┐          ┌─────────┐          ┌─────────────┐          ┌───────┐
│ Client  │          │ Gateway │          │ User Service│          │ Redis │
└────┬────┘          └────┬────┘          └──────┬──────┘          └───┬───┘
     │                    │                      │                     │
     │ POST /users/auth/login                    │                     │
     │ {username, password, remember}            │                     │
     │───────────────────►│                      │                     │
     │                    │                      │                     │
     │                    │ Forward (whitelist)  │                     │
     │                    │─────────────────────►│                     │
     │                    │                      │                     │
     │                    │                      │ Query user by username
     │                    │                      │────────────────────►│
     │                    │                      │◄────────────────────│
     │                    │                      │                     │
     │                    │                      │ BCrypt.matches(password)
     │                    │                      │──────────┐          │
     │                    │                      │◄─────────┘          │
     │                    │                      │                     │
     │                    │                      │ StpUtil.login(userId)
     │                    │                      │────────────────────►│
     │                    │                      │ Store access token  │
     │                    │                      │◄────────────────────│
     │                    │                      │                     │
     │                    │                      │ Create refresh token│
     │                    │                      │────────────────────►│
     │                    │                      │ Store refresh token │
     │                    │                      │◄────────────────────│
     │                    │                      │                     │
     │                    │◄─────────────────────│                     │
     │                    │ LoginRespVO          │                     │
     │◄───────────────────│                      │                     │
     │ Set-Cookie: refreshToken                  │                     │
     │ Set-Cookie: Authorization                 │                     │
     │                    │                      │                     │
```

#### Request

```http
POST /users/auth/login HTTP/1.1
Content-Type: application/json

{
  "username": "testuser",
  "password": "Test@1234",
  "remember": true
}
```

#### Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
Set-Cookie: Authorization=abc123-uuid; Path=/; HttpOnly; Max-Age=86400
Set-Cookie: refreshToken=xyz789-uuid; Path=/; HttpOnly; Max-Age=604800

{
  "code": 0,
  "msg": "success",
  "data": {
    "user": {
      "id": "2017289231954173953",
      "name": "testuser",
      "email": "test@example.com",
      "role": "ORGANIZER"
    },
    "roles": [
      {
        "id": "2017289232084197378",
        "name": "Organizer",
        "key": "ORGANIZER",
        "isDefault": false,
        "permissions": [
          {
            "id": "1971465366969307147",
            "name": "All system permission",
            "key": "system:*"
          }
        ]
      }
    ]
  }
}
```

#### Code Flow

```java
// 1. AuthController.java - Receive request
@PostMapping("/login")
public CommonResult<LoginRespVO> login(@RequestBody LoginReqVO reqVO, ...) {
    LoginRespVO loginRespVO = authService.login(reqVO);
    response.addCookie(cookieFactory.createCookie(loginRespVO.getRefreshToken()));
    return success(loginRespVO);
}

// 2. AuthServiceImpl.java - Authenticate
public LoginRespVO login(LoginReqVO reqVO) {
    UserDO userDO = authenticate(reqVO.getUsername(), reqVO.getPassword());
    return handleLogin(userDO, reqVO.isRemember(), reqVO.getRefreshToken());
}

// 3. AuthServiceImpl.java - Verify credentials
public UserDO authenticate(String username, String password) {
    UserDO userDO = userService.getUserByUsername(username);
    if (userDO == null) throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
    if (!userService.isPasswordMatch(password, userDO.getPassword())) {
        throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
    }
    if (CommonStatusEnum.isDisable(userDO.getStatus())) {
        throw exception(AUTH_LOGIN_USER_DISABLED);
    }
    return userDO;
}

// 4. AuthServiceImpl.java - Create tokens
private LoginRespVO handleLogin(UserDO userDO, boolean rememberMe, String refreshToken) {
    StpUtil.login(userDO.getId());  // Creates access token
    StpUtil.getSession().set(SESSION_TENANT_ID, userDO.getTenantId());
    if (StrUtil.isEmpty(refreshToken)) {
        refreshToken = tokenService.createRefreshToken(userTokenDTO);
    }
    return getInfo(refreshToken);
}

// 5. TokenServiceImpl.java - Store refresh token in Redis
public String createRefreshToken(UserTokenDTO userTokenDTO) {
    String token = UUID.randomUUID().toString();
    if (userTokenDTO.isRemember()) {
        stringRedisTemplate.opsForValue().set(
            LOGIN_REFRESH_TOKEN_KEY + token,
            userTokenDTO.getId().toString(),
            securityProperties.getRefreshTokenExpire(),
            TimeUnit.SECONDS
        );
    }
    return token;
}
```

---

### 2. Token Refresh Flow

```
┌─────────┐          ┌─────────┐          ┌─────────────┐          ┌───────┐
│ Client  │          │ Gateway │          │ User Service│          │ Redis │
└────┬────┘          └────┬────┘          └──────┬──────┘          └───┬───┘
     │                    │                      │                     │
     │ POST /users/auth/refresh                  │                     │
     │ Cookie: refreshToken=xyz789               │                     │
     │───────────────────►│                      │                     │
     │                    │                      │                     │
     │                    │ Forward (whitelist)  │                     │
     │                    │─────────────────────►│                     │
     │                    │                      │                     │
     │                    │                      │ StpUtil.isLogin()?  │
     │                    │                      │────────────────────►│
     │                    │                      │◄────────────────────│
     │                    │                      │ false (expired)     │
     │                    │                      │                     │
     │                    │                      │ GET refresh_token:xyz789
     │                    │                      │────────────────────►│
     │                    │                      │◄────────────────────│
     │                    │                      │ userId=2017289...   │
     │                    │                      │                     │
     │                    │                      │ StpUtil.login(userId)
     │                    │                      │────────────────────►│
     │                    │                      │ New access token    │
     │                    │                      │◄────────────────────│
     │                    │                      │                     │
     │                    │◄─────────────────────│                     │
     │◄───────────────────│ LoginRespVO          │                     │
     │ New Authorization cookie                  │                     │
     │                    │                      │                     │
```

#### Request

```http
POST /users/auth/refresh HTTP/1.1
Cookie: refreshToken=xyz789-uuid
```

#### Response

```http
HTTP/1.1 200 OK
Set-Cookie: Authorization=new-abc123-uuid; Path=/; HttpOnly; Max-Age=86400

{
  "code": 0,
  "data": {
    "user": {...},
    "roles": [...]
  }
}
```

#### Code Flow

```java
// AuthServiceImpl.java
public LoginRespVO refresh(String refreshToken) {
    // Case 1: Access token still valid
    if (StpUtil.isLogin()) {
        return getInfo(refreshToken);
    }

    // Case 2: Access token expired - use refresh token
    Long userId = tokenService.getUserIdFromRefreshToken(refreshToken);
    if (ObjUtil.isNull(userId)) {
        throw exception(REFRESH_TOKEN_WRONG);
    }

    // Re-login (creates new access token)
    StpUtil.login(userId);
    return getInfo(refreshToken);
}

// TokenServiceImpl.java
public Long getUserIdFromRefreshToken(String refreshToken) {
    String userIdStr = stringRedisTemplate.opsForValue()
        .get(LOGIN_REFRESH_TOKEN_KEY + refreshToken);
    return StrUtil.isNotEmpty(userIdStr) ? Long.parseLong(userIdStr) : null;
}
```

---

### 3. Logout Flow

```
┌─────────┐          ┌─────────┐          ┌─────────────┐          ┌───────┐
│ Client  │          │ Gateway │          │ User Service│          │ Redis │
└────┬────┘          └────┬────┘          └──────┬──────┘          └───┬───┘
     │                    │                      │                     │
     │ POST /users/auth/logout                   │                     │
     │ Cookie: refreshToken=xyz789               │                     │
     │ Header: Authorization=abc123              │                     │
     │───────────────────►│                      │                     │
     │                    │                      │                     │
     │                    │ Validate token       │                     │
     │                    │─────────────────────►│                     │
     │                    │                      │                     │
     │                    │                      │ DELETE refresh_token:xyz789
     │                    │                      │────────────────────►│
     │                    │                      │◄────────────────────│
     │                    │                      │                     │
     │                    │                      │ StpUtil.logout()    │
     │                    │                      │────────────────────►│
     │                    │                      │ Delete access token │
     │                    │                      │◄────────────────────│
     │                    │                      │                     │
     │                    │◄─────────────────────│                     │
     │◄───────────────────│ success: true        │                     │
     │ Set-Cookie: refreshToken=; Max-Age=0      │                     │
     │                    │                      │                     │
```

#### Request

```http
POST /users/auth/logout HTTP/1.1
Authorization: abc123-uuid
Cookie: refreshToken=xyz789-uuid
```

#### Response

```http
HTTP/1.1 200 OK
Set-Cookie: refreshToken=; Path=/; HttpOnly; Max-Age=0

{
  "code": 0,
  "data": true
}
```

#### Code Flow

```java
// AuthController.java
@PostMapping("/logout")
public CommonResult<Boolean> logout(
    @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
    HttpServletResponse response) {
    authService.logout(refreshToken);
    // Clear cookie
    response.addCookie(cookieFactory.createCookie(null));
    return success(true);
}

// AuthServiceImpl.java
public void logout(String token) {
    tokenService.removeToken(token);
    StpUtil.logout();
}

// TokenServiceImpl.java
public void removeToken(String token) {
    stringRedisTemplate.delete(LOGIN_REFRESH_TOKEN_KEY + token);
    StpUtil.logout();
}
```

---

## Authorization Flow

### Role-Based Access Control (RBAC)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           RBAC MODEL                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   User ──────► UserRole ──────► Role ──────► RolePermission ──────► Permission
│                                                                         │
│   ┌─────────┐      ┌──────────┐      ┌─────────────────┐               │
│   │ User    │      │ Role     │      │ Permission      │               │
│   ├─────────┤      ├──────────┤      ├─────────────────┤               │
│   │ id      │      │ id       │      │ id              │               │
│   │ username│      │ name     │      │ name            │               │
│   │ tenantId│      │ key      │      │ key             │               │
│   └─────────┘      │ isDefault│      │ (e.g. user:read)│               │
│                    └──────────┘      └─────────────────┘               │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Permission Check Flow

```
┌─────────┐          ┌─────────┐          ┌─────────────────────┐
│ Client  │          │ Gateway │          │ StpPermissionHandler│
└────┬────┘          └────┬────┘          └──────────┬──────────┘
     │                    │                          │
     │ GET /users/admin/list                         │
     │ Authorization: abc123                         │
     │───────────────────►│                          │
     │                    │                          │
     │                    │ @SaCheckPermission("user:list")
     │                    │─────────────────────────►│
     │                    │                          │
     │                    │                          │ Get permissions from session
     │                    │                          │ session.get(USER_PERMISSION_KEY)
     │                    │                          │
     │                    │                          │ Check if "user:list" in permissions
     │                    │                          │ or "user:*" matches
     │                    │                          │
     │                    │◄─────────────────────────│
     │                    │ Allowed / Denied         │
     │                    │                          │
```

### Permission Annotations

```java
// Require specific permission
@SaCheckPermission("user:create")
public CommonResult<Long> createUser(...) { }

// Require any of multiple permissions
@SaCheckPermission(value = {"user:read", "user:list"}, mode = SaMode.OR)
public CommonResult<List<UserVO>> listUsers(...) { }

// Require specific role
@SaCheckRole("admin")
public CommonResult<?> adminOnly(...) { }

// No authentication required
@SaIgnore
public CommonResult<LoginRespVO> login(...) { }
```

### Permission Handler Implementation

```java
// StpPermissionHandler.java
@Component
public class StpPermissionHandler implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        return session.get(USER_PERMISSION_KEY, new ArrayList<>());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        return session.get(USER_ROLE_KEY, new ArrayList<>());
    }
}
```

### Session Data Structure

```
Redis Key: satoken:login:session:{userId}

Session Data:
{
  "tenantId": 1234567890,
  "roles": ["ORGANIZER", "ADMIN"],
  "permissions": [
    "system:*",
    "user:create",
    "user:read",
    "user:update",
    "user:delete",
    "event:*"
  ]
}
```

---

## Token Management

### Redis Key Structure

| Key Pattern | Value | TTL | Description |
|-------------|-------|-----|-------------|
| `satoken:login:token:{accessToken}` | userId | 24h | Access token → User mapping |
| `satoken:login:session:{userId}` | Session JSON | 24h | User session data |
| `Authorization:login:refresh_token:{refreshToken}` | userId | 7d | Refresh token → User mapping |

### Token Validation

```java
// Gateway AuthFilter
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    // Skip whitelist paths
    if (isWhitelist(path)) {
        return chain.filter(exchange);
    }

    // Get token from header or cookie
    String token = getToken(exchange);
    if (token == null) {
        return unauthorized(exchange);
    }

    // Validate with Sa-Token
    try {
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return unauthorized(exchange);
        }
        // Inject user info into request headers
        exchange = injectUserContext(exchange, loginId);
    } catch (Exception e) {
        return unauthorized(exchange);
    }

    return chain.filter(exchange);
}
```

### Whitelist Paths (No Auth Required)

```yaml
# gateway/application.yaml
gateway:
  white-list:
    - /users/auth/login
    - /users/auth/refresh
    - /users/reg/**
    - /ws
    - /ws/internal/**
    - /doc.html
    - /webjars/**
    - /favicon.ico
    - /v3/**
    - /*/v3/**
```

---

## Security Features

### 1. Password Security

```java
// PasswordEncoderConfig.java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// Usage
String encoded = passwordEncoder.encode("plainPassword");
boolean matches = passwordEncoder.matches("plainPassword", encoded);
```

### 2. Cookie Security

| Flag | Value | Purpose |
|------|-------|---------|
| `HttpOnly` | true | Prevents JavaScript access (XSS protection) |
| `Secure` | false (dev) / true (prod) | HTTPS only in production |
| `SameSite` | Lax | CSRF protection |
| `Path` | / | Cookie scope |

### 3. Generic Error Messages

```java
// Don't reveal if username exists
if (userDO == null) {
    throw exception(AUTH_LOGIN_BAD_CREDENTIALS);  // Same error
}
if (!passwordMatch) {
    throw exception(AUTH_LOGIN_BAD_CREDENTIALS);  // Same error
}
```

### 4. Account Status Check

```java
if (CommonStatusEnum.isDisable(userDO.getStatus())) {
    throw exception(AUTH_LOGIN_USER_DISABLED);
}
```

### 5. Multi-Tenancy

```java
// Tenant isolation via session
StpUtil.getSession().set(SESSION_TENANT_ID, userDO.getTenantId());

// MyBatis interceptor adds tenant filter to all queries
// MybatisPlusConfig.java
public class TenantLineHandler implements TenantLineInnerInterceptor {
    public Expression getTenantId() {
        return new LongValue(getCurrentTenantId());
    }
}
```

---

## API Reference

### Authentication Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/users/auth/login` | No | User login |
| POST | `/users/auth/refresh` | No | Refresh access token |
| POST | `/users/auth/logout` | Yes | User logout |

### Registration Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/users/reg/organizer` | No | Register as organizer |

### Request/Response Schemas

#### LoginReqVO

```json
{
  "username": "string (6-100 chars, required)",
  "password": "string (8-100 chars, required)",
  "remember": "boolean (default: true)"
}
```

#### LoginRespVO

```json
{
  "user": {
    "id": "string",
    "name": "string",
    "email": "string",
    "role": "string (pipe-delimited)"
  },
  "roles": [
    {
      "id": "string",
      "name": "string",
      "key": "string",
      "isDefault": "boolean",
      "permissions": [
        {
          "id": "string",
          "name": "string",
          "key": "string"
        }
      ]
    }
  ]
}
```

---

## Error Codes

| Code | Name | Description |
|------|------|-------------|
| 1001001 | AUTH_LOGIN_BAD_CREDENTIALS | Invalid username or password |
| 1001002 | AUTH_LOGIN_USER_DISABLED | User account is disabled |
| 1001003 | REFRESH_TOKEN_WRONG | Invalid or expired refresh token |
| 1001004 | ACCOUNT_ERROR | User account data error |
| 1001005 | EXPIRED_LOGIN_CREDENTIALS | Login credentials expired |

---

## Configuration

### Sa-Token Configuration

```yaml
# application.yaml
sa-token:
  token-name: Authorization        # Header/cookie name
  timeout: 86400                   # Access token TTL (24 hours)
  active-timeout: -1               # No activity timeout
  is-concurrent: true              # Allow multiple logins
  is-share: true                   # Share token across logins
  is-log: true                     # Enable logging
  token-style: uuid                # Token format
  cookie:
    http-only: true                # XSS protection
```

### Security Properties

```yaml
# application.yaml
chronoflow:
  security:
    refresh-token-expire: 604800   # 7 days in seconds
  cookie:
    http-only: true
    security: false                # Set true in production
```

### Cookie Max Ages

```java
// SecurityConstants.java
public static final int REFRESH_TOKEN_REMEMBER_COOKIE_MAX_AGE = 604800;  // 7 days
public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
```

---

## Sequence Summary

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    COMPLETE AUTH LIFECYCLE                                  │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  1. LOGIN                                                                  │
│     Client ──► POST /users/auth/login {username, password}                 │
│     Server ──► Validate credentials                                        │
│     Server ──► Create access token (Sa-Token, 24h)                         │
│     Server ──► Create refresh token (Redis, 7d)                            │
│     Server ──► Set cookies + return user info                              │
│                                                                            │
│  2. API CALLS                                                              │
│     Client ──► GET /api/resource (Authorization: {token})                  │
│     Gateway ──► Validate access token                                      │
│     Gateway ──► Check permissions (@SaCheckPermission)                     │
│     Service ──► Process request with tenant context                        │
│                                                                            │
│  3. TOKEN REFRESH (when access token expires)                              │
│     Client ──► POST /users/auth/refresh (Cookie: refreshToken)             │
│     Server ──► Lookup userId from refresh token                            │
│     Server ──► Create new access token                                     │
│     Server ──► Return new cookies                                          │
│                                                                            │
│  4. LOGOUT                                                                 │
│     Client ──► POST /users/auth/logout                                     │
│     Server ──► Delete refresh token from Redis                             │
│     Server ──► Invalidate access token (StpUtil.logout)                    │
│     Server ──► Clear cookies                                               │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Frontend Integration Example

```typescript
// authApi.ts
import axios from 'axios';

const api = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_URL,
  withCredentials: true,  // Important: Send cookies
});

// Login
export const login = async (username: string, password: string, remember = true) => {
  const response = await api.post('/users/auth/login', {
    username,
    password,
    remember,
  });
  return response.data;
};

// Refresh token
export const refresh = async () => {
  const response = await api.post('/users/auth/refresh');
  return response.data;
};

// Logout
export const logout = async () => {
  await api.post('/users/auth/logout');
};

// Axios interceptor for auto-refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      try {
        await refresh();
        return api.request(error.config);  // Retry original request
      } catch (refreshError) {
        // Redirect to login
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
```

---

*Document generated for Chronoflow Security Project*
*Last updated: 2026-01-31*
