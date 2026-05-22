package nus.edu.u.provider.ws;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.configuration.ws.WsGatewayLimitPropertiesConfig;
import nus.edu.u.domain.dto.ws.WsRequestDTO;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Slf4j
@Component
public class WebSocketGatewayClientImpl implements WebSocketGatewayClient {

    private final WsGatewayLimitPropertiesConfig props;
    private final WebClient client;

    public WebSocketGatewayClientImpl(
            WsGatewayLimitPropertiesConfig props,
            @Value("${spring.application.name:notificationservice}") String appName) {
        this.props = props;

        HttpClient http =
                HttpClient.create()
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                props.getTimeouts().getConnectMs())
                        .responseTimeout(Duration.ofMillis(props.getTimeouts().getReadMs()))
                        .doOnConnected(
                                c ->
                                        c.addHandlerLast(
                                                new WriteTimeoutHandler(
                                                        props.getTimeouts().getWriteMs(),
                                                        TimeUnit.MILLISECONDS)));

        this.client =
                WebClient.builder()
                        .baseUrl(props.getBaseUrl())
                        .clientConnector(new ReactorClientHttpConnector(http))
                        .defaultHeader("X-Source-Service", appName)
                        .defaultHeader("X-Internal-Service-Token", props.getInternalServiceToken())
                        .filter(errorFilter())
                        .build();
    }

    @Override
    public Mono<Void> sendToUser(WsRequestDTO req) {
        if (!props.isEnabled()) {
            log.debug("[WS] disabled; skipping {}", req);
            return Mono.empty();
        }

        String reqId = Optional.ofNullable(MDC.get("requestId")).orElse("");

        return client.post()
                .uri("/ws/internal/push")
                .header("X-Request-Id", reqId)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(Void.class)
                .retryWhen(
                        Retry.backoff(
                                        props.getRetry().getMaxRetries(),
                                        Duration.ofMillis(props.getRetry().getInitialBackoffMs()))
                                .jitter(0.2)
                                .filter(this::isRetryable))
                .doOnSuccess(
                        v ->
                                log.debug(
                                        "[WS] OK user={} type={} eventId={}",
                                        req.getUserId(),
                                        req.getType(),
                                        req.getEventId()))
                .doOnError(
                        e ->
                                log.warn(
                                        "[WS] FAIL user={} type={} eventId={} err={}",
                                        req.getUserId(),
                                        req.getType(),
                                        req.getEventId(),
                                        e.toString()));
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof ConnectException) return true;
        if (t instanceof WebClientResponseException w) {
            int s = w.getStatusCode().value();
            return s >= 500 && s < 600;
        }
        return false;
    }

    private ExchangeFilterFunction errorFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(
                resp -> {
                    if (resp.statusCode().is2xxSuccessful()) return Mono.just(resp);

                    return resp.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(
                                    body ->
                                            Mono.error(
                                                    WebClientResponseException.create(
                                                            resp.statusCode().value(),
                                                            (resp.statusCode()
                                                                            instanceof
                                                                            HttpStatus hs)
                                                                    ? hs.getReasonPhrase()
                                                                    : "",
                                                            resp.headers().asHttpHeaders(),
                                                            body.getBytes(StandardCharsets.UTF_8),
                                                            StandardCharsets.UTF_8)));
                });
    }
}
