-- ============================================================================
-- Combined migrations script (docs/sql)
-- ============================================================================
-- Run this after combined-init.sql. Migrations are applied in version order.
-- Generation Date: 2026-03-14
-- ============================================================================


-- ============================================================================
-- File: V2__add_firebase_uid.sql
-- ============================================================================
USE user;

-- Add Firebase UID column to sys_user table
-- This column stores the Firebase Authentication User UID for users who authenticate via Firebase

ALTER TABLE sys_user ADD COLUMN firebase_uid VARCHAR(128) NULL COMMENT 'Firebase Authentication User UID';

-- Create unique index for Firebase UID lookups
CREATE UNIQUE INDEX idx_sys_user_firebase_uid ON sys_user (firebase_uid);

-- Note: Run this migration on your database before enabling Firebase authentication


-- ============================================================================
-- File: V3__add_totp_columns.sql
-- ============================================================================
USE user;

-- Add TOTP (Two-Factor Authentication) columns to sys_user table
ALTER TABLE sys_user
    ADD COLUMN totp_secret VARCHAR(64) NULL COMMENT 'TOTP secret key for 2FA',
    ADD COLUMN totp_enabled TINYINT(1) DEFAULT 0 NOT NULL COMMENT '0 - Disabled; 1 - Enabled';


-- ============================================================================
-- File: V4__add_audit_log.sql
-- Run against each service database: user, event, task, attendee, file
-- ============================================================================

-- user
USE user;

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)     NULL                    COMMENT 'Distributed trace ID',
    user_id         BIGINT          NULL                    COMMENT 'User who performed the action',
    user_ip         VARCHAR(64)     NULL                    COMMENT 'Client IP address',
    user_agent      VARCHAR(512)    NULL                    COMMENT 'Client User-Agent header',
    module          VARCHAR(50)     NOT NULL                COMMENT 'Logical module: user, event, task, attendee, file, security',
    operation       VARCHAR(100)    NOT NULL                COMMENT 'Operation name, e.g. Create Role, LOGIN_SUCCESS',
    type            TINYINT         NOT NULL                COMMENT '1=Security 2=AdminAction 3=DataChange 4=APIAccess',
    method          VARCHAR(10)     NULL                    COMMENT 'HTTP method',
    request_url     VARCHAR(512)    NULL                    COMMENT 'Request URL',
    request_body    TEXT            NULL                    COMMENT 'Sanitized request body (passwords stripped)',
    target_type     VARCHAR(100)    NULL                    COMMENT 'Logical entity type: Role, Permission, Event, Task, etc.',
    target_id       VARCHAR(64)     NULL                    COMMENT 'Target entity ID',
    before_data     JSON            NULL                    COMMENT 'Entity state before modification',
    after_data      JSON            NULL                    COMMENT 'Entity state after modification',
    result_code     INT             NULL                    COMMENT 'HTTP/business result code',
    result_msg      VARCHAR(512)    NULL                    COMMENT 'Result message (error message on failure)',
    duration        INT             NULL                    COMMENT 'Operation duration in milliseconds',
    extra           JSON            NULL                    COMMENT 'Arbitrary extra data',
    tenant_id       BIGINT          NULL                    COMMENT 'Tenant ID for multi-tenant filtering',
    creator         VARCHAR(100)    NULL                    COMMENT 'Creator user ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    updater         VARCHAR(100)    NULL                    COMMENT 'Last updater user ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update time',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'Logical delete flag'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit log table';

CREATE INDEX idx_audit_log_user_id     ON audit_log (user_id);
CREATE INDEX idx_audit_log_module      ON audit_log (module);
CREATE INDEX idx_audit_log_type        ON audit_log (type);
CREATE INDEX idx_audit_log_target      ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_create_time ON audit_log (create_time);
CREATE INDEX idx_audit_log_tenant_id   ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_trace_id    ON audit_log (trace_id);

-- event
USE event;

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)     NULL                    COMMENT 'Distributed trace ID',
    user_id         BIGINT          NULL                    COMMENT 'User who performed the action',
    user_ip         VARCHAR(64)     NULL                    COMMENT 'Client IP address',
    user_agent      VARCHAR(512)    NULL                    COMMENT 'Client User-Agent header',
    module          VARCHAR(50)     NOT NULL                COMMENT 'Logical module: user, event, task, attendee, file, security',
    operation       VARCHAR(100)    NOT NULL                COMMENT 'Operation name, e.g. Create Role, LOGIN_SUCCESS',
    type            TINYINT         NOT NULL                COMMENT '1=Security 2=AdminAction 3=DataChange 4=APIAccess',
    method          VARCHAR(10)     NULL                    COMMENT 'HTTP method',
    request_url     VARCHAR(512)    NULL                    COMMENT 'Request URL',
    request_body    TEXT            NULL                    COMMENT 'Sanitized request body (passwords stripped)',
    target_type     VARCHAR(100)    NULL                    COMMENT 'Logical entity type: Role, Permission, Event, Task, etc.',
    target_id       VARCHAR(64)     NULL                    COMMENT 'Target entity ID',
    before_data     JSON            NULL                    COMMENT 'Entity state before modification',
    after_data      JSON            NULL                    COMMENT 'Entity state after modification',
    result_code     INT             NULL                    COMMENT 'HTTP/business result code',
    result_msg      VARCHAR(512)    NULL                    COMMENT 'Result message (error message on failure)',
    duration        INT             NULL                    COMMENT 'Operation duration in milliseconds',
    extra           JSON            NULL                    COMMENT 'Arbitrary extra data',
    tenant_id       BIGINT          NULL                    COMMENT 'Tenant ID for multi-tenant filtering',
    creator         VARCHAR(100)    NULL                    COMMENT 'Creator user ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    updater         VARCHAR(100)    NULL                    COMMENT 'Last updater user ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update time',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'Logical delete flag'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit log table';

CREATE INDEX idx_audit_log_user_id     ON audit_log (user_id);
CREATE INDEX idx_audit_log_module      ON audit_log (module);
CREATE INDEX idx_audit_log_type        ON audit_log (type);
CREATE INDEX idx_audit_log_target      ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_create_time ON audit_log (create_time);
CREATE INDEX idx_audit_log_tenant_id   ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_trace_id    ON audit_log (trace_id);

-- task
USE task;

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)     NULL                    COMMENT 'Distributed trace ID',
    user_id         BIGINT          NULL                    COMMENT 'User who performed the action',
    user_ip         VARCHAR(64)     NULL                    COMMENT 'Client IP address',
    user_agent      VARCHAR(512)    NULL                    COMMENT 'Client User-Agent header',
    module          VARCHAR(50)     NOT NULL                COMMENT 'Logical module: user, event, task, attendee, file, security',
    operation       VARCHAR(100)    NOT NULL                COMMENT 'Operation name, e.g. Create Role, LOGIN_SUCCESS',
    type            TINYINT         NOT NULL                COMMENT '1=Security 2=AdminAction 3=DataChange 4=APIAccess',
    method          VARCHAR(10)     NULL                    COMMENT 'HTTP method',
    request_url     VARCHAR(512)    NULL                    COMMENT 'Request URL',
    request_body    TEXT            NULL                    COMMENT 'Sanitized request body (passwords stripped)',
    target_type     VARCHAR(100)    NULL                    COMMENT 'Logical entity type: Role, Permission, Event, Task, etc.',
    target_id       VARCHAR(64)     NULL                    COMMENT 'Target entity ID',
    before_data     JSON            NULL                    COMMENT 'Entity state before modification',
    after_data      JSON            NULL                    COMMENT 'Entity state after modification',
    result_code     INT             NULL                    COMMENT 'HTTP/business result code',
    result_msg      VARCHAR(512)    NULL                    COMMENT 'Result message (error message on failure)',
    duration        INT             NULL                    COMMENT 'Operation duration in milliseconds',
    extra           JSON            NULL                    COMMENT 'Arbitrary extra data',
    tenant_id       BIGINT          NULL                    COMMENT 'Tenant ID for multi-tenant filtering',
    creator         VARCHAR(100)    NULL                    COMMENT 'Creator user ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    updater         VARCHAR(100)    NULL                    COMMENT 'Last updater user ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update time',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'Logical delete flag'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit log table';

CREATE INDEX idx_audit_log_user_id     ON audit_log (user_id);
CREATE INDEX idx_audit_log_module      ON audit_log (module);
CREATE INDEX idx_audit_log_type        ON audit_log (type);
CREATE INDEX idx_audit_log_target      ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_create_time ON audit_log (create_time);
CREATE INDEX idx_audit_log_tenant_id   ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_trace_id    ON audit_log (trace_id);

-- attendee
USE attendee;

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)     NULL                    COMMENT 'Distributed trace ID',
    user_id         BIGINT          NULL                    COMMENT 'User who performed the action',
    user_ip         VARCHAR(64)     NULL                    COMMENT 'Client IP address',
    user_agent      VARCHAR(512)    NULL                    COMMENT 'Client User-Agent header',
    module          VARCHAR(50)     NOT NULL                COMMENT 'Logical module: user, event, task, attendee, file, security',
    operation       VARCHAR(100)    NOT NULL                COMMENT 'Operation name, e.g. Create Role, LOGIN_SUCCESS',
    type            TINYINT         NOT NULL                COMMENT '1=Security 2=AdminAction 3=DataChange 4=APIAccess',
    method          VARCHAR(10)     NULL                    COMMENT 'HTTP method',
    request_url     VARCHAR(512)    NULL                    COMMENT 'Request URL',
    request_body    TEXT            NULL                    COMMENT 'Sanitized request body (passwords stripped)',
    target_type     VARCHAR(100)    NULL                    COMMENT 'Logical entity type: Role, Permission, Event, Task, etc.',
    target_id       VARCHAR(64)     NULL                    COMMENT 'Target entity ID',
    before_data     JSON            NULL                    COMMENT 'Entity state before modification',
    after_data      JSON            NULL                    COMMENT 'Entity state after modification',
    result_code     INT             NULL                    COMMENT 'HTTP/business result code',
    result_msg      VARCHAR(512)    NULL                    COMMENT 'Result message (error message on failure)',
    duration        INT             NULL                    COMMENT 'Operation duration in milliseconds',
    extra           JSON            NULL                    COMMENT 'Arbitrary extra data',
    tenant_id       BIGINT          NULL                    COMMENT 'Tenant ID for multi-tenant filtering',
    creator         VARCHAR(100)    NULL                    COMMENT 'Creator user ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    updater         VARCHAR(100)    NULL                    COMMENT 'Last updater user ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update time',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'Logical delete flag'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit log table';

CREATE INDEX idx_audit_log_user_id     ON audit_log (user_id);
CREATE INDEX idx_audit_log_module      ON audit_log (module);
CREATE INDEX idx_audit_log_type        ON audit_log (type);
CREATE INDEX idx_audit_log_target      ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_create_time ON audit_log (create_time);
CREATE INDEX idx_audit_log_tenant_id   ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_trace_id    ON audit_log (trace_id);

-- file
USE file;

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trace_id        VARCHAR(64)     NULL                    COMMENT 'Distributed trace ID',
    user_id         BIGINT          NULL                    COMMENT 'User who performed the action',
    user_ip         VARCHAR(64)     NULL                    COMMENT 'Client IP address',
    user_agent      VARCHAR(512)    NULL                    COMMENT 'Client User-Agent header',
    module          VARCHAR(50)     NOT NULL                COMMENT 'Logical module: user, event, task, attendee, file, security',
    operation       VARCHAR(100)    NOT NULL                COMMENT 'Operation name, e.g. Create Role, LOGIN_SUCCESS',
    type            TINYINT         NOT NULL                COMMENT '1=Security 2=AdminAction 3=DataChange 4=APIAccess',
    method          VARCHAR(10)     NULL                    COMMENT 'HTTP method',
    request_url     VARCHAR(512)    NULL                    COMMENT 'Request URL',
    request_body    TEXT            NULL                    COMMENT 'Sanitized request body (passwords stripped)',
    target_type     VARCHAR(100)    NULL                    COMMENT 'Logical entity type: Role, Permission, Event, Task, etc.',
    target_id       VARCHAR(64)     NULL                    COMMENT 'Target entity ID',
    before_data     JSON            NULL                    COMMENT 'Entity state before modification',
    after_data      JSON            NULL                    COMMENT 'Entity state after modification',
    result_code     INT             NULL                    COMMENT 'HTTP/business result code',
    result_msg      VARCHAR(512)    NULL                    COMMENT 'Result message (error message on failure)',
    duration        INT             NULL                    COMMENT 'Operation duration in milliseconds',
    extra           JSON            NULL                    COMMENT 'Arbitrary extra data',
    tenant_id       BIGINT          NULL                    COMMENT 'Tenant ID for multi-tenant filtering',
    creator         VARCHAR(100)    NULL                    COMMENT 'Creator user ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record creation time',
    updater         VARCHAR(100)    NULL                    COMMENT 'Last updater user ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record update time',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'Logical delete flag'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Audit log table';

CREATE INDEX idx_audit_log_user_id     ON audit_log (user_id);
CREATE INDEX idx_audit_log_module      ON audit_log (module);
CREATE INDEX idx_audit_log_type        ON audit_log (type);
CREATE INDEX idx_audit_log_target      ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_create_time ON audit_log (create_time);
CREATE INDEX idx_audit_log_tenant_id   ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_trace_id    ON audit_log (trace_id);


-- ============================================================================
-- File: V5__backfill_audit_log_result_code.sql
-- Run against each service database that has audit_log
-- ============================================================================

USE user;

UPDATE audit_log
SET    result_code = -1
WHERE  module = 'security'
  AND  result_code IS NULL
  AND  (   operation LIKE 'LOGIN_FAILED%'
        OR operation = 'TOKEN_FINGERPRINT_MISMATCH'
        OR operation = 'REFRESH_TOKEN_REUSE_DETECTED'
        OR operation = 'PERMISSION_DENIED'
        OR operation = 'RATE_LIMIT_EXCEEDED'
      );

UPDATE audit_log
SET    result_code = 0
WHERE  module = 'security'
  AND  result_code IS NULL;

USE event;

UPDATE audit_log
SET    result_code = -1
WHERE  module = 'security'
  AND  result_code IS NULL
  AND  (   operation LIKE 'LOGIN_FAILED%'
        OR operation = 'TOKEN_FINGERPRINT_MISMATCH'
        OR operation = 'REFRESH_TOKEN_REUSE_DETECTED'
        OR operation = 'PERMISSION_DENIED'
        OR operation = 'RATE_LIMIT_EXCEEDED'
      );

UPDATE audit_log
SET    result_code = 0
WHERE  module = 'security'
  AND  result_code IS NULL;

USE task;

UPDATE audit_log
SET    result_code = -1
WHERE  module = 'security'
  AND  result_code IS NULL
  AND  (   operation LIKE 'LOGIN_FAILED%'
        OR operation = 'TOKEN_FINGERPRINT_MISMATCH'
        OR operation = 'REFRESH_TOKEN_REUSE_DETECTED'
        OR operation = 'PERMISSION_DENIED'
        OR operation = 'RATE_LIMIT_EXCEEDED'
      );

UPDATE audit_log
SET    result_code = 0
WHERE  module = 'security'
  AND  result_code IS NULL;

USE attendee;

UPDATE audit_log
SET    result_code = -1
WHERE  module = 'security'
  AND  result_code IS NULL
  AND  (   operation LIKE 'LOGIN_FAILED%'
        OR operation = 'TOKEN_FINGERPRINT_MISMATCH'
        OR operation = 'REFRESH_TOKEN_REUSE_DETECTED'
        OR operation = 'PERMISSION_DENIED'
        OR operation = 'RATE_LIMIT_EXCEEDED'
      );

UPDATE audit_log
SET    result_code = 0
WHERE  module = 'security'
  AND  result_code IS NULL;

USE file;

UPDATE audit_log
SET    result_code = -1
WHERE  module = 'security'
  AND  result_code IS NULL
  AND  (   operation LIKE 'LOGIN_FAILED%'
        OR operation = 'TOKEN_FINGERPRINT_MISMATCH'
        OR operation = 'REFRESH_TOKEN_REUSE_DETECTED'
        OR operation = 'PERMISSION_DENIED'
        OR operation = 'RATE_LIMIT_EXCEEDED'
      );

UPDATE audit_log
SET    result_code = 0
WHERE  module = 'security'
  AND  result_code IS NULL;


-- ============================================================================
-- File: V6__add_webview_ott_table.sql
-- ============================================================================
USE user;

CREATE TABLE IF NOT EXISTS sys_user_ott (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ott_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

ALTER TABLE sys_user ADD COLUMN salt VARCHAR(64) NULL COMMENT 'Password salt' AFTER password;
