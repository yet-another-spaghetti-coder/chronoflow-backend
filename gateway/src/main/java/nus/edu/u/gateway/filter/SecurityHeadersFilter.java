package nus.edu.u.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** WebFlux filter to add OWASP recommended security headers to all responses. */
@Component
public class SecurityHeadersFilter implements WebFilter, Ordered {

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
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            addSecurityHeaders(exchange.getResponse().getHeaders());
                            return Mono.empty();
                        });
        return chain.filter(exchange);
    }

    private void addSecurityHeaders(HttpHeaders headers) {
        headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("Content-Security-Policy", buildCsp());
        headers.set("X-XSS-Protection", "1; mode=block");
        headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
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
