package nus.edu.u.gateway.filter;

import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CorsResponseFilter implements WebFilter, Ordered {

    private static final List<String> ALLOWED_HEADERS =
            List.of(
                    "Content-Type",
                    "Authorization",
                    "Accept",
                    "Origin",
                    "X-Requested-With",
                    "Cache-Control",
                    "Pragma");

    private static final List<HttpMethod> ALLOWED_METHODS =
            List.of(
                    HttpMethod.GET,
                    HttpMethod.POST,
                    HttpMethod.DELETE,
                    HttpMethod.PATCH,
                    HttpMethod.PUT,
                    HttpMethod.OPTIONS);

    private static final Set<String> ALLOWED_ORIGINS =
            Set.of(
                    "https://chronoflow.site",
                    "https://api.chronoflow.site",
                    "https://www.chronoflow.site",
                    "http://chronoflow.site",
                    "http://api.chronoflow.site",
                    "http://www.chronoflow.site",
                    "https://chronoflow-1.web.app",
                    "https://chronoflow-1.firebaseapp.com",
                    "http://localhost:5173",
                    "http://127.0.0.1:5173");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.setAccessControlAllowOrigin(origin);
            headers.setAccessControlAllowCredentials(true);
            headers.setAccessControlAllowHeaders(ALLOWED_HEADERS);
            headers.setAccessControlAllowMethods(ALLOWED_METHODS);
            headers.setAccessControlMaxAge(3600L);
            headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);

            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                return exchange.getResponse().setComplete();
            }
        }

        return chain.filter(exchange);
    }
}
