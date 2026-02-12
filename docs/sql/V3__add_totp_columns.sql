-- Add TOTP (Two-Factor Authentication) columns to sys_user table
ALTER TABLE sys_user
    ADD COLUMN totp_secret VARCHAR(64) NULL COMMENT 'TOTP secret key for 2FA',
    ADD COLUMN totp_enabled TINYINT(1) DEFAULT 0 NOT NULL COMMENT '0 - Disabled; 1 - Enabled';
