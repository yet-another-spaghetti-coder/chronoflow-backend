-- ============================================================
-- V5: Backfill result_code for security audit log entries
-- SecurityAuditLogger previously did not set result_code,
-- leaving it NULL for all security events.
-- ============================================================

-- Set result_code = -1 (failure) for failed security events
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

-- Set result_code = 0 (success) for successful security events
UPDATE audit_log
SET    result_code = 0
WHERE  module = 'security'
  AND  result_code IS NULL;
