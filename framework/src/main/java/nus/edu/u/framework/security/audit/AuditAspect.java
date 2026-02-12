package nus.edu.u.framework.security.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * AOP aspect that intercepts methods annotated with {@link Auditable}
 * and writes audit log entries via {@link AuditLogWriterService}.
 */
@Aspect
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogWriterService writerService;
    private final ObjectMapper objectMapper;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(
            Arrays.asList("password", "rawPassword", "totpSecret", "secret", "token", "refreshToken"));

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception caught = null;

        try {
            result = joinPoint.proceed();
        } catch (Exception e) {
            caught = e;
            throw e;
        } finally {
            try {
                long duration = System.currentTimeMillis() - startTime;
                AuditLogDO auditLog = buildAuditLog(joinPoint, auditable, result, caught, duration);
                writerService.writeAsync(auditLog);
            } catch (Exception e) {
                log.warn("[AUDIT] Failed to build/submit audit log for {}: {}",
                        auditable.operation(), e.getMessage());
            }
        }

        return result;
    }

    private AuditLogDO buildAuditLog(
            ProceedingJoinPoint joinPoint,
            Auditable auditable,
            Object result,
            Exception exception,
            long duration) {

        AuditLogDO.AuditLogDOBuilder builder = AuditLogDO.builder();

        // Operation metadata
        builder.operation(auditable.operation());
        builder.type(auditable.type().getValue());
        builder.module(resolveModule(joinPoint, auditable));
        builder.targetType(auditable.targetType());
        builder.duration((int) duration);

        // User context
        try {
            if (StpUtil.isLogin()) {
                builder.userId(StpUtil.getLoginIdAsLong());
            }
        } catch (Exception ignored) {
            // Not authenticated — leave userId null
        }

        // Request context
        HttpServletRequest request = getRequest();
        if (request != null) {
            builder.userIp(getClientIp(request));
            builder.userAgent(truncate(request.getHeader("User-Agent"), 512));
            builder.method(request.getMethod());
            builder.requestUrl(truncate(request.getRequestURI(), 512));
            builder.traceId(request.getHeader("X-Trace-Id"));
        }

        // Request body
        if (auditable.recordRequestBody()) {
            builder.requestBody(sanitizeArgs(joinPoint.getArgs(), auditable.excludeFields()));
        }

        // Target ID via SpEL
        if (!auditable.targetId().isEmpty()) {
            builder.targetId(evaluateSpel(joinPoint, auditable.targetId()));
        }

        // Result
        if (exception != null) {
            builder.resultCode(-1);
            builder.resultMsg(truncate(exception.getMessage(), 512));
        } else {
            builder.resultCode(0);
        }

        // Tenant context
        try {
            Object tenantId = StpUtil.getSession().get("tenant_id");
            if (tenantId != null) {
                AuditLogDO auditLog = builder.build();
                auditLog.setTenantId(Long.parseLong(tenantId.toString()));
                return auditLog;
            }
        } catch (Exception ignored) {
            // No tenant context available
        }

        return builder.build();
    }

    private String resolveModule(ProceedingJoinPoint joinPoint, Auditable auditable) {
        if (!auditable.module().isEmpty()) {
            return auditable.module();
        }
        String packageName = joinPoint.getTarget().getClass().getPackageName();
        if (packageName.contains(".user.")) return "user";
        if (packageName.contains(".event.")) return "event";
        if (packageName.contains(".task.")) return "task";
        if (packageName.contains(".attendee.")) return "attendee";
        if (packageName.contains(".file.")) return "file";
        return "unknown";
    }

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private String evaluateSpel(ProceedingJoinPoint joinPoint, String expression) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            Object[] args = joinPoint.getArgs();

            // Try MethodSignature first, fall back to DefaultParameterNameDiscoverer
            String[] paramNames = sig.getParameterNames();
            if (paramNames == null || paramNames.length == 0) {
                paramNames = NAME_DISCOVERER.getParameterNames(sig.getMethod());
            }

            StandardEvaluationContext ctx = new StandardEvaluationContext();

            // Bind named parameters as variables
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }
            // Bind positional references (#p0, #p1, ...)
            for (int i = 0; i < args.length; i++) {
                ctx.setVariable("p" + i, args[i]);
            }
            // Set first arg as root object so property access (e.g. "key") also works
            if (args.length == 1 && args[0] != null) {
                ctx.setRootObject(args[0]);
            }

            Object value = PARSER.parseExpression(expression).getValue(ctx);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("[AUDIT] SpEL evaluation failed for '{}': {}", expression, e.getMessage());
            return null;
        }
    }

    private String sanitizeArgs(Object[] args, String[] excludeFields) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Set<String> excludes = new HashSet<>(Arrays.asList(excludeFields));
            excludes.addAll(SENSITIVE_FIELDS);

            // Serialize first arg (typically the request VO)
            Object target = args.length == 1 ? args[0] : args;
            String json = objectMapper.writeValueAsString(target);

            // Strip sensitive fields from JSON
            for (String field : excludes) {
                json = json.replaceAll(
                        "\"" + field + "\"\\s*:\\s*\"[^\"]*\"",
                        "\"" + field + "\":\"***\"");
            }

            return truncate(json, 4000);
        } catch (Exception e) {
            return "[serialization-error]";
        }
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
