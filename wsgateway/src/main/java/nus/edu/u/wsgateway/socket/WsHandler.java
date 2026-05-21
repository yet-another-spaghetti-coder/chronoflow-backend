package nus.edu.u.wsgateway.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nus.edu.u.wsgateway.runtime.LocalConnectionRegistry;
import nus.edu.u.wsgateway.security.WsHandshakeProperties;
import nus.edu.u.wsgateway.security.WsJwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive WebSocket handler with explicit per-connection authentication.
 *
 * <p>PLS 03 (CSWH mitigation): the connection is treated as unauthenticated at upgrade time.
 * Before subscribing the session to any user's outbound channel, this handler waits for an explicit
 * {@code {"type":"AUTH","token":"<ws-jwt>"}} message and verifies the WS-only JWT minted by {@code
 * /ws/token}. The {@code userId} is derived from the JWT {@code sub} claim — never from the URL
 * query string, request body, or any client-supplied input.
 *
 * <p>Failure cases close the session with {@link CloseStatus#POLICY_VIOLATION} so a misbehaving
 * client cannot keep a half-open socket alive indefinitely. A separate scheduler watchdog enforces
 * an auth timeout for clients that connect but never send AUTH at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsHandler implements WebSocketHandler {

    /** How long the client has to send the AUTH frame before the connection is force-closed. */
    static final Duration AUTH_TIMEOUT = Duration.ofSeconds(5);

    /** Reject AUTH frames larger than this to bound parse cost on unauthenticated input. */
    private static final int MAX_AUTH_FRAME_CHARS = 4096;

    private static final CloseStatus AUTH_REQUIRED =
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "AUTH_REQUIRED");
    private static final CloseStatus AUTH_FAILED =
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "AUTH_FAILED");
    private static final CloseStatus BAD_ORIGIN =
            new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "BAD_ORIGIN");

    private final LocalConnectionRegistry registry;
    private final WsJwtService jwtService;
    private final WsHandshakeProperties handshakeProps;
    private final ObjectMapper mapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();

        // PLS 03 Origin check: reject before any inbound stream is wired up. Fail-closed if the
        // allow-list is empty so a misconfigured deploy cannot silently accept arbitrary origins.
        String origin = session.getHandshakeInfo().getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (!isOriginAllowed(origin)) {
            log.warn(
                    "[WS] rejecting handshake bad_origin='{}' session={}", origin, sessionId);
            return session.close(BAD_ORIGIN);
        }

        AtomicBoolean authed = new AtomicBoolean(false);

        // Watchdog: close the session if AUTH never arrives. Cancelled on auth success/termination.
        Disposable watchdog =
                Schedulers.parallel()
                        .schedule(
                                () -> {
                                    if (!authed.get()) {
                                        log.warn(
                                                "[WS] auth timeout, closing session={}",
                                                sessionId);
                                        session.close(AUTH_REQUIRED).subscribe();
                                    }
                                },
                                AUTH_TIMEOUT.toMillis(),
                                TimeUnit.MILLISECONDS);

        return session.receive()
                .switchOnFirst(
                        (signal, source) -> {
                            if (!signal.hasValue()) {
                                // Client opened and immediately closed/errored before any frame.
                                return Mono.<Void>empty().flux();
                            }
                            String userId = tryAuth(signal.get());
                            if (userId == null) {
                                log.warn("[WS] auth rejected session={}", sessionId);
                                return session.close(AUTH_FAILED).flux();
                            }
                            authed.set(true);
                            watchdog.dispose();
                            log.info("[WS] authed userId={} session={}", userId, sessionId);

                            Flux<WebSocketMessage> outbound =
                                    registry.stream(userId).map(session::textMessage);
                            Mono<Void> sendMono = session.send(outbound);
                            Mono<Void> recvMono =
                                    source.skip(1)
                                            .doOnNext(
                                                    msg ->
                                                            handlePostAuth(
                                                                    session, userId, msg))
                                            .then();
                            return Mono.when(sendMono, recvMono).flux();
                        })
                .then()
                .doFinally(
                        sig -> {
                            watchdog.dispose();
                            log.info(
                                    "[WS] disconnect session={} signal={}", sessionId, sig);
                        })
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "[WS] session error {}: {}", sessionId, ex.toString());
                            return session.close(AUTH_FAILED);
                        });
    }

    /**
     * Exact-match check against {@link WsHandshakeProperties#getAllowedOrigins()}. Browser clients
     * are required to send an {@code Origin} header on the upgrade request; missing or unknown
     * origins are rejected (fail-closed).
     */
    boolean isOriginAllowed(String origin) {
        List<String> allowed = handshakeProps.getAllowedOrigins();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return allowed.contains(origin);
    }

    /**
     * Verify the first inbound frame as a valid AUTH message. Returns the authenticated {@code
     * userId} from the JWT {@code sub} claim, or {@code null} on any failure. Never throws — auth
     * failures are intentionally indistinguishable from each other to the client.
     */
    String tryAuth(WebSocketMessage msg) {
        if (msg.getType() != WebSocketMessage.Type.TEXT) {
            return null;
        }
        String text = msg.getPayloadAsText();
        if (text == null || text.isBlank() || text.length() > MAX_AUTH_FRAME_CHARS) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(text);
            if (!"AUTH".equals(node.path("type").asText(""))) {
                return null;
            }
            String token = node.path("token").asText("");
            WsJwtService.VerifiedClaims claims = jwtService.verify(token);
            return claims.userId();
        } catch (WsJwtService.JwtVerificationException e) {
            log.debug("[WS] AUTH verify failed reason={}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("[WS] AUTH parse failed: {}", e.toString());
            return null;
        }
    }

    /**
     * Post-auth inbound handling. Phase 5 will tighten this into a strict allow-list; for now keep
     * legacy literal "ping" working and add the JSON {"type":"PING"} -> {"type":"PONG",ts}
     * heartbeat to fix the FE/BE format mismatch the gap analysis flagged.
     */
    private void handlePostAuth(
            WebSocketSession session, String userId, WebSocketMessage msg) {
        if (msg.getType() != WebSocketMessage.Type.TEXT) {
            return;
        }
        String text = msg.getPayloadAsText();
        if (text == null || text.isEmpty()) {
            return;
        }
        if ("ping".equalsIgnoreCase(text)) {
            session.send(Mono.just(session.textMessage("pong")))
                    .subscribe(
                            null,
                            ex ->
                                    log.debug(
                                            "[WS] legacy pong send failed userId={}: {}",
                                            userId,
                                            ex.toString()));
            return;
        }
        try {
            JsonNode node = mapper.readTree(text);
            String type = node.path("type").asText("");
            if ("PING".equals(type)) {
                String pong =
                        mapper.writeValueAsString(
                                Map.of("type", "PONG", "ts", System.currentTimeMillis()));
                session.send(Mono.just(session.textMessage(pong)))
                        .subscribe(
                                null,
                                ex ->
                                        log.debug(
                                                "[WS] PONG send failed userId={}: {}",
                                                userId,
                                                ex.toString()));
                return;
            }
            log.debug("[WS] ignored inbound type='{}' userId={}", type, userId);
        } catch (Exception e) {
            // Do not log the full body — could be hostile content from an authenticated session
            // that later got compromised.
            log.debug("[WS] non-JSON inbound userId={} bytes={}", userId, text.length());
        }
    }
}
