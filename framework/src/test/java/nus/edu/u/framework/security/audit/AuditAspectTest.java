package nus.edu.u.framework.security.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditAspectTest {

    @Mock private AuditLogWriterService writerService;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    private AuditAspect aspect;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        aspect = new AuditAspect(writerService, objectMapper);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new FakeService());
    }

    @Test
    void around_successfulExecution_writesAuditLogAsync() throws Throwable {
        Auditable auditable = createAuditable("Create Role", AuditType.ADMIN_ACTION, "Role", "");
        when(joinPoint.proceed()).thenReturn("ok");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doSomething"));

        Object result = aspect.around(joinPoint, auditable);

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        AuditLogDO log = captor.getValue();
        assertThat(log.getOperation()).isEqualTo("Create Role");
        assertThat(log.getType()).isEqualTo(AuditType.ADMIN_ACTION.getValue());
        assertThat(log.getResultCode()).isEqualTo(0);
        assertThat(log.getDuration()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void around_exceptionThrown_recordsErrorAndRethrows() throws Throwable {
        Auditable auditable = createAuditable("Delete Role", AuditType.ADMIN_ACTION, "Role", "");
        RuntimeException error = new RuntimeException("db error");
        when(joinPoint.proceed()).thenThrow(error);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doSomething"));

        assertThatThrownBy(() -> aspect.around(joinPoint, auditable))
                .isSameAs(error);

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        AuditLogDO log = captor.getValue();
        assertThat(log.getResultCode()).isEqualTo(-1);
        assertThat(log.getResultMsg()).isEqualTo("db error");
    }

    @Test
    void around_spelTargetId_evaluatesCorrectly() throws Throwable {
        Auditable auditable = createAuditable("Update Task", AuditType.DATA_CHANGE, "Task", "#taskId");
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doWithId", Long.class));
        when(signature.getParameterNames()).thenReturn(new String[]{"taskId"});

        aspect.around(joinPoint, auditable);

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        assertThat(captor.getValue().getTargetId()).isEqualTo("42");
    }

    @Test
    void around_sensitiveFieldsStripped_fromRequestBody() throws Throwable {
        Auditable auditable = createAuditable("Create User", AuditType.ADMIN_ACTION, "User", "");
        FakeRequest req = new FakeRequest("alice@test.com", "secret123");
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{req});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doWithRequest", FakeRequest.class));
        when(signature.getParameterNames()).thenReturn(new String[]{"req"});

        aspect.around(joinPoint, auditable);

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        String body = captor.getValue().getRequestBody();
        assertThat(body).contains("alice@test.com");
        assertThat(body).doesNotContain("secret123");
        assertThat(body).contains("\"password\":\"***\"");
    }

    @Test
    void around_recordRequestBodyFalse_doesNotRecordBody() throws Throwable {
        Auditable auditable = createAuditable("Upload File", AuditType.DATA_CHANGE, "File", "",
                new String[]{}, false);
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"data"});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doSomething"));

        aspect.around(joinPoint, auditable);

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        assertThat(captor.getValue().getRequestBody()).isNull();
    }

    @Test
    void around_moduleAutoDetected_fromPackage() throws Throwable {
        Auditable auditable = createAuditable("Create Event", AuditType.DATA_CHANGE, "Event", "");
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doSomething"));

        // FakeService is in the framework.security.audit package — won't match any service module
        aspect.around(joinPoint, auditable);

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        assertThat(captor.getValue().getModule()).isEqualTo("unknown");
    }

    @Test
    void around_moduleExplicitlySet_usesProvidedModule() throws Throwable {
        Auditable auditable = createAuditableWithModule("Create Event", AuditType.DATA_CHANGE,
                "Event", "", "event");
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doSomething"));

        aspect.around(joinPoint, auditable);

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(writerService).writeAsync(captor.capture());
        assertThat(captor.getValue().getModule()).isEqualTo("event");
    }

    @Test
    void around_writerServiceFails_doesNotPropagateException() throws Throwable {
        Auditable auditable = createAuditable("Fail Op", AuditType.ADMIN_ACTION, "", "");
        when(joinPoint.proceed()).thenReturn("result");
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(FakeService.class.getMethod("doSomething"));
        doAnswer(inv -> { throw new RuntimeException("writer failed"); })
                .when(writerService).writeAsync(any());

        // Should not throw
        Object result = aspect.around(joinPoint, auditable);
        assertThat(result).isEqualTo("result");
    }

    // ---- Helpers ----

    private Auditable createAuditable(String operation, AuditType type,
                                       String targetType, String targetId) {
        return createAuditable(operation, type, targetType, targetId, new String[]{}, true);
    }

    private Auditable createAuditable(String operation, AuditType type,
                                       String targetType, String targetId,
                                       String[] excludeFields, boolean recordRequestBody) {
        return new Auditable() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Auditable.class; }
            @Override public String operation() { return operation; }
            @Override public String module() { return ""; }
            @Override public AuditType type() { return type; }
            @Override public String targetType() { return targetType; }
            @Override public String targetId() { return targetId; }
            @Override public String[] excludeFields() { return excludeFields; }
            @Override public boolean recordRequestBody() { return recordRequestBody; }
        };
    }

    private Auditable createAuditableWithModule(String operation, AuditType type,
                                                 String targetType, String targetId, String module) {
        return new Auditable() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Auditable.class; }
            @Override public String operation() { return operation; }
            @Override public String module() { return module; }
            @Override public AuditType type() { return type; }
            @Override public String targetType() { return targetType; }
            @Override public String targetId() { return targetId; }
            @Override public String[] excludeFields() { return new String[]{}; }
            @Override public boolean recordRequestBody() { return true; }
        };
    }

    /** Fake service class for method reflection. */
    public static class FakeService {
        public void doSomething() {}
        public void doWithId(Long taskId) {}
        public void doWithRequest(FakeRequest req) {}
    }

    /** Fake request VO with a sensitive password field. */
    public static class FakeRequest {
        private String email;
        private String password;

        public FakeRequest() {}
        public FakeRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
