package nus.edu.u.framework.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogWriterServiceTest {

    @Mock private AuditLogMapper auditLogMapper;

    @Test
    void writeSync_insertsDirectly() {
        AuditLogWriterService service = new AuditLogWriterService(auditLogMapper, Runnable::run);
        AuditLogDO log = AuditLogDO.builder()
                .module("security")
                .operation("LOGIN_SUCCESS")
                .type(1)
                .build();
        when(auditLogMapper.insert(any())).thenReturn(1);

        service.writeSync(log);

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperation()).isEqualTo("LOGIN_SUCCESS");
    }

    @Test
    void writeSync_mapperThrows_doesNotPropagate() {
        AuditLogWriterService service = new AuditLogWriterService(auditLogMapper, Runnable::run);
        AuditLogDO log = AuditLogDO.builder()
                .module("security")
                .operation("CRITICAL")
                .type(1)
                .build();
        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any());

        // Should not throw
        service.writeSync(log);
    }

    @Test
    void writeAsync_submitsToExecutor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AuditLogDO> captured = new AtomicReference<>();

        Executor testExecutor = command -> {
            new Thread(() -> {
                command.run();
                latch.countDown();
            }).start();
        };

        when(auditLogMapper.insert(any())).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return 1;
        });

        AuditLogWriterService service = new AuditLogWriterService(auditLogMapper, testExecutor);
        AuditLogDO log = AuditLogDO.builder()
                .module("user")
                .operation("Create Role")
                .type(2)
                .build();

        service.writeAsync(log);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getOperation()).isEqualTo("Create Role");
    }

    @Test
    void writeAsync_mapperThrows_doesNotPropagate() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Executor testExecutor = command -> {
            new Thread(() -> {
                command.run();
                latch.countDown();
            }).start();
        };

        doThrow(new RuntimeException("db down")).when(auditLogMapper).insert(any());

        AuditLogWriterService service = new AuditLogWriterService(auditLogMapper, testExecutor);
        AuditLogDO log = AuditLogDO.builder()
                .module("user")
                .operation("Fail Op")
                .type(2)
                .build();

        service.writeAsync(log);

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        // No exception propagated to caller
    }

    @Test
    void writeAsync_callerRunsPolicy_executesOnCallerThread() {
        // Simulates CallerRunsPolicy by using a synchronous executor
        Executor syncExecutor = Runnable::run;
        when(auditLogMapper.insert(any())).thenReturn(1);

        AuditLogWriterService service = new AuditLogWriterService(auditLogMapper, syncExecutor);
        AuditLogDO log = AuditLogDO.builder()
                .module("event")
                .operation("Create Event")
                .type(3)
                .build();

        service.writeAsync(log);

        verify(auditLogMapper).insert(log);
    }
}
