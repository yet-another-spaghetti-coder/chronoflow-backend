package nus.edu.u.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter to add security headers to all responses. Implements OWASP recommended security
 * headers.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    /**
     * Production WebSocket endpoint (wss://...) to add to CSP {@code connect-src}. Empty default =
     * no wss source added (no WS connections permitted by CSP). Set via env / Nacos: {@code
     * CHRONOFLOW_WSS_URL=wss://api.example.com}. Dev profile may set {@code ws://localhost:8087};
     * do not allow {@code ws:} in prod.
     */
    @Value("${chronoflow.security.csp.connect-src-ws:}")
    private String connectSrcWs;

    @Override
    public int getOrder() {
        // Run after other filters but before response is sent
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Add security headers BEFORE the chain executes
        HttpHeaders headers = exchange.getResponse().getHeaders();

        // HTTP Strict Transport Security
        headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");

        // Prevent MIME type sniffing
        headers.add("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        headers.add("X-Frame-Options", "DENY");

        // Control referrer information
        headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

        // Content Security Policy
        headers.add("Content-Security-Policy", buildCsp());

        // XSS Protection (legacy but still useful)
        headers.add("X-XSS-Protection", "1; mode=block");

        // Permissions Policy (formerly Feature-Policy)
        headers.add("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

        return chain.filter(exchange);
    }

    /** Build Content Security Policy header. Allows Firebase and Google auth domains. */
    private String buildCsp() {
        StringBuilder connectSrc =
                new StringBuilder(
                        "connect-src 'self' https://*.googleapis.com https://*.firebaseapp.com"
                                + " https://identitytoolkit.googleapis.com"
                                + " https://securetoken.googleapis.com");
        if (connectSrcWs != null && !connectSrcWs.isBlank()) {
            connectSrc.append(' ').append(connectSrcWs.trim());
        }
        return String.join(
                "; ",
                "default-src 'self'",
                "script-src 'self'",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data: https:",
                "font-src 'self' https://fonts.gstatic.com",
                connectSrc.toString(),
                "frame-src https://*.firebaseapp.com https://accounts.google.com",
                "base-uri 'self'",
                "form-action 'self'");
    }
}
