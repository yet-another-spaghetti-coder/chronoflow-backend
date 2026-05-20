-- ============================================================
-- V4: Add audit_log table for STRIDE-based auditing
-- Run against each service database:
--   user, event, task, attendee, file
-- ============================================================

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

-- Indexes for common query patterns
CREATE INDEX idx_audit_log_user_id     ON audit_log (user_id);
CREATE INDEX idx_audit_log_module      ON audit_log (module);
CREATE INDEX idx_audit_log_type        ON audit_log (type);
CREATE INDEX idx_audit_log_target      ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_create_time ON audit_log (create_time);
CREATE INDEX idx_audit_log_tenant_id   ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_trace_id    ON audit_log (trace_id);
