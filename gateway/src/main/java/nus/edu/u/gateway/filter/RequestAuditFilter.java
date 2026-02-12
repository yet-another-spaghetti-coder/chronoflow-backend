package nus.edu.u.gateway.filter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway-level audit filter that logs mutating requests (POST/PATCH/PUT/DELETE)
 * to sensitive endpoints into Redis for request-level audit trailing (STRIDE: Repudiation).
 *
 * <p>Entries are stored in Redis lists with a 7-day TTL:
 * {@code gateway:audit:log:{date}}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestAuditFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate reactiveRedisTemplate;

    private static final String AUDIT_KEY_PREFIX = "gateway:audit:log:";
    private static final Duration AUDIT_RETENTION = Duration.ofDays(7);

    /** HTTP methods that represent mutating operations. */
    private static final Set<HttpMethod> MUTATING_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE
    );

    /** Path prefixes for sensitive endpoints to audit. */
    private static final String[] AUDITED_PREFIXES = {
            "/users/roles",
            "/users/permissions",
            "/users/organizer",
            "/users/auth",
            "/events",
            "/tasks",
            "/attendees",
            "/api/files"
    };

    @Override
    public int getOrder() {
        // Run after auth filters but before routing
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();

        // Only audit mutating requests to sensitive endpoints
        if (method == null || !MUTATING_METHODS.contains(method)) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();
        if (!isAuditedPath(path)) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .then(Mono.defer(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;

                    String auditEntry = buildAuditEntry(request, statusCode, duration);
                    String dateKey = AUDIT_KEY_PREFIX + LocalDate.now();

                    return reactiveRedisTemplate.opsForList()
                            .rightPush(dateKey, auditEntry)
                            .then(reactiveRedisTemplate.expire(dateKey, AUDIT_RETENTION))
                            .doOnError(e -> log.warn("[GATEWAY_AUDIT] Failed to write: {}", e.getMessage()))
                            .onErrorResume(e -> Mono.empty())
                            .then();
                }));
    }

    private boolean isAuditedPath(String path) {
        for (String prefix : AUDITED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String buildAuditEntry(ServerHttpRequest request, int statusCode, long duration) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String traceId = request.getHeaders().getFirst("X-Trace-Id");

        // Build JSON manually to avoid ObjectMapper dependency in gateway
        StringBuilder sb = new StringBuilder(512);
        sb.append("{");
        appendField(sb, "timestamp", Instant.now().toString());
        sb.append(","); appendField(sb, "method", request.getMethod() != null ? request.getMethod().name() : "");
        sb.append(","); appendField(sb, "path", request.getURI().getPath());
        sb.append(","); appendField(sb, "query", request.getURI().getQuery());
        sb.append(","); appendField(sb, "clientIp", clientIp);
        sb.append(","); appendField(sb, "userAgent", truncate(userAgent, 256));
        sb.append(","); appendField(sb, "traceId", traceId);
        sb.append(",\"statusCode\":").append(statusCode);
        sb.append(",\"duration\":").append(duration);
        sb.append("}");

        return sb.toString();
    }

    private void appendField(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(escapeJson(value)).append("\"");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
