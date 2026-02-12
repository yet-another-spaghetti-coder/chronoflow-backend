package nus.edu.u.framework.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import nus.edu.u.framework.security.SecurityAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the audit logging subsystem.
 * Registers the AOP aspect, writer service, and dedicated thread pool.
 */
@AutoConfiguration(after = SecurityAutoConfiguration.class)
@MapperScan(basePackageClasses = AuditLogMapper.class)
public class AuditAutoConfiguration {

    @Bean
    public Executor auditExecutor() {
        return new ThreadPoolExecutor(
                2,    // core pool size
                4,    // max pool size
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                r -> {
                    Thread t = new Thread(r, "audit-writer");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // backpressure: caller writes synchronously
        );
    }

    @Bean
    public AuditLogWriterService auditLogWriterService(AuditLogMapper auditLogMapper, @Qualifier("auditExecutor") Executor auditExecutor) {
        return new AuditLogWriterService(auditLogMapper, auditExecutor);
    }

    @Bean
    public AuditAspect auditAspect(AuditLogWriterService writerService, ObjectMapper objectMapper) {
        return new AuditAspect(writerService, objectMapper);
    }

    /**
     * Wire the writer service into SecurityAuditLogger so that security events
     * are persisted to the audit_log table in addition to Redis.
     */
    @Bean
    public SecurityAuditLoggerConfigurer securityAuditLoggerConfigurer(
            ObjectProvider<SecurityAuditLogger> securityAuditLoggerProvider,
            AuditLogWriterService writerService) {
        securityAuditLoggerProvider.ifAvailable(
                logger -> logger.setAuditLogWriterService(writerService));
        return new SecurityAuditLoggerConfigurer();
    }

    /** Marker bean to indicate wiring is complete. */
    static class SecurityAuditLoggerConfigurer {}
}
