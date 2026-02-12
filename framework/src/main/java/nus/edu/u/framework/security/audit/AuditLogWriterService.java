package nus.edu.u.framework.security.audit;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.framework.mybatis.MybatisPlusConfig;

/**
 * Async audit log writer with a dedicated thread pool.
 * Critical security events are written synchronously to guarantee persistence.
 */
@Slf4j
public class AuditLogWriterService {

    private final AuditLogMapper auditLogMapper;
    private final Executor auditExecutor;

    public AuditLogWriterService(AuditLogMapper auditLogMapper, Executor auditExecutor) {
        this.auditLogMapper = auditLogMapper;
        this.auditExecutor = auditExecutor;
    }

    /**
     * Write an audit log entry asynchronously.
     */
    public void writeAsync(AuditLogDO auditLog) {
        auditExecutor.execute(() -> {
            try {
                MybatisPlusConfig.executeWithoutTenantFilter(() -> auditLogMapper.insert(auditLog));
            } catch (Exception e) {
                log.error("[AUDIT] Failed to persist audit log: operation={}, module={}, error={}",
                        auditLog.getOperation(), auditLog.getModule(), e.getMessage(), e);
            }
        });
    }

    /**
     * Write an audit log entry synchronously.
     * Use for critical security events that must be persisted before returning.
     */
    public void writeSync(AuditLogDO auditLog) {
        try {
            MybatisPlusConfig.executeWithoutTenantFilter(() -> auditLogMapper.insert(auditLog));
        } catch (Exception e) {
            log.error("[AUDIT] Failed to persist critical audit log: operation={}, module={}, error={}",
                    auditLog.getOperation(), auditLog.getModule(), e.getMessage(), e);
        }
    }
}
