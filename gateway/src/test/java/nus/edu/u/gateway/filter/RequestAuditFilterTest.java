package nus.edu.u.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestAuditFilterTest {

    @Mock private ReactiveStringRedisTemplate reactiveRedisTemplate;
    @Mock private ReactiveListOperations<String, String> listOperations;
    @Mock private GatewayFilterChain chain;

    private RequestAuditFilter filter;

    @BeforeEach
    void setUp() {
        when(reactiveRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(reactiveRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        filter = new RequestAuditFilter(reactiveRedisTemplate);
    }

    @Test
    void getOrder_returnsNegative100() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    @Test
    void filter_postToAuditedPath_writesToRedis() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/users/roles").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String expectedKeyPrefix = "gateway:audit:log:" + LocalDate.now();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(keyCaptor.capture(), valueCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(expectedKeyPrefix);

        String json = valueCaptor.getValue();
        assertThat(json).contains("\"method\":\"POST\"");
        assertThat(json).contains("\"path\":\"/users/roles\"");
        assertThat(json).contains("\"statusCode\":");
        assertThat(json).contains("\"duration\":");

        verify(reactiveRedisTemplate).expire(eq(expectedKeyPrefix), eq(Duration.ofDays(7)));
    }

    @Test
    void filter_putToAuditedPath_writesToRedis() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/events/123").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(listOperations).rightPush(anyString(), anyString());
    }

    @Test
    void filter_patchToAuditedPath_writesToRedis() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.patch("/tasks/42").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(listOperations).rightPush(anyString(), anyString());
    }

    @Test
    void filter_deleteToAuditedPath_writesToRedis() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/attendees/99").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(listOperations).rightPush(anyString(), anyString());
    }

    @Test
    void filter_getRequest_skipsAudit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/users/roles").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(listOperations, never()).rightPush(anyString(), anyString());
    }

    @Test
    void filter_postToNonAuditedPath_skipsAudit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/health").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(listOperations, never()).rightPush(anyString(), anyString());
    }

    @Test
    void filter_allAuditedPrefixes_areRecognized() {
        String[] prefixes = {
                "/users/roles", "/users/permissions", "/users/organizer",
                "/users/auth", "/events", "/tasks", "/attendees", "/api/files"
        };

        for (String prefix : prefixes) {
            // Reset mocks for each iteration
            when(listOperations.rightPush(anyString(), anyString())).thenReturn(Mono.just(1L));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post(prefix + "/test").build());
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        // 8 prefixes tested, each should write
        verify(listOperations, org.mockito.Mockito.atLeast(8)).rightPush(anyString(), anyString());
    }

    @Test
    void filter_capturesClientIpFromXForwardedFor() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/events")
                        .header("X-Forwarded-For", "203.0.113.50, 70.41.3.18")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(anyString(), valueCaptor.capture());
        assertThat(valueCaptor.getValue()).contains("\"clientIp\":\"203.0.113.50\"");
    }

    @Test
    void filter_capturesUserAgent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/events")
                        .header("User-Agent", "TestAgent/1.0")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(anyString(), valueCaptor.capture());
        assertThat(valueCaptor.getValue()).contains("\"userAgent\":\"TestAgent/1.0\"");
    }

    @Test
    void filter_capturesTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/tasks")
                        .header("X-Trace-Id", "abc-123-trace")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(anyString(), valueCaptor.capture());
        assertThat(valueCaptor.getValue()).contains("\"traceId\":\"abc-123-trace\"");
    }

    @Test
    void filter_redisError_doesNotPropagate() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/events").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        when(listOperations.rightPush(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis down")));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_capturesQueryString() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/events/1?force=true").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(anyString(), valueCaptor.capture());
        assertThat(valueCaptor.getValue()).contains("\"query\":\"force=true\"");
    }
}
