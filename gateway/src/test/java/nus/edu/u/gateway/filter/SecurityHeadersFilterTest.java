package nus.edu.u.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

/** PLS 03: WebSocket endpoint is added to CSP connect-src (and only when configured). */
class SecurityHeadersFilterTest {

    @Test
    void csp_omitsConnectSrcWs_whenUnset() {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        // @Value not bound in unit test; default is null. Equivalent to env var unset.
        ReflectionTestUtils.setField(filter, "connectSrcWs", "");

        String csp = applyAndReadCsp(filter);

        assertThat(csp).contains("connect-src 'self'");
        assertThat(csp).doesNotContain("wss://");
        assertThat(csp).doesNotContain("ws://");
    }

    @Test
    void csp_includesConfiguredWss_inConnectSrc() {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        ReflectionTestUtils.setField(filter, "connectSrcWs", "wss://api.example.com");

        String csp = applyAndReadCsp(filter);

        assertThat(csp).contains("connect-src 'self'");
        assertThat(csp).contains("wss://api.example.com");
    }

    @Test
    void csp_trimsWhitespace_inConfiguredEndpoint() {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        ReflectionTestUtils.setField(filter, "connectSrcWs", "  wss://api.example.com  ");

        String csp = applyAndReadCsp(filter);

        assertThat(csp).contains("wss://api.example.com");
        // No double spaces around the trimmed value.
        assertThat(csp).doesNotContain("  wss://");
    }

    @Test
    void otherSecurityHeaders_areAlwaysPresent() {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        ReflectionTestUtils.setField(filter, "connectSrcWs", "");

        MockServerWebExchange exchange = newExchange();
        runFilter(filter, exchange);
        HttpHeaders headers = exchange.getResponse().getHeaders();

        assertThat(headers.getFirst("Strict-Transport-Security")).contains("max-age=31536000");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy"))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("X-XSS-Protection")).isEqualTo("1; mode=block");
        assertThat(headers.getFirst("Permissions-Policy")).contains("camera=()");
    }

    private static String applyAndReadCsp(SecurityHeadersFilter filter) {
        MockServerWebExchange exchange = newExchange();
        runFilter(filter, exchange);
        String csp = exchange.getResponse().getHeaders().getFirst("Content-Security-Policy");
        assertThat(csp).isNotNull();
        return csp;
    }

    private static MockServerWebExchange newExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/health"));
    }

    private static void runFilter(SecurityHeadersFilter filter, MockServerWebExchange exchange) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, chain).block();
    }
}
