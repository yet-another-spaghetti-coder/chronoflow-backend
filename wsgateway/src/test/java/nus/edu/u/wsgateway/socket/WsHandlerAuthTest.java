package nus.edu.u.wsgateway.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import nus.edu.u.wsgateway.runtime.LocalConnectionRegistry;
import nus.edu.u.wsgateway.security.WsHandshakeProperties;
import nus.edu.u.wsgateway.security.WsJwtProperties;
import nus.edu.u.wsgateway.security.WsJwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;

/** Verifies the auth-first gate in WsHandler.tryAuth rejects every spoof path. */
class WsHandlerAuthTest {

    private static final String SECRET = "dev-secret-must-be-at-least-32-chars-long-xxxx";
    private static final DefaultDataBufferFactory BUF = new DefaultDataBufferFactory();

    private WsHandler handler;
    private WsJwtService jwtService;
    private WsHandshakeProperties handshakeProps;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        WsJwtProperties props = new WsJwtProperties();
        props.setSecret(SECRET);
        jwtService = new WsJwtService(props, mapper);
        jwtService.init();
        handshakeProps = new WsHandshakeProperties();
        handshakeProps.setAllowedOrigins(
                List.of("https://chronoflow.example.com", "http://localhost:5173"));
        handler =
                new WsHandler(
                        mock(LocalConnectionRegistry.class), jwtService, handshakeProps, mapper);
    }

    @Test
    void tryAuth_validJwt_returnsUserId() {
        String token = jwtService.mint("u-42").token();
        WebSocketMessage msg = textMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}");

        assertThat(handler.tryAuth(msg)).isEqualTo("u-42");
    }

    @Test
    void tryAuth_missingToken_returnsNull() {
        WebSocketMessage msg = textMessage("{\"type\":\"AUTH\"}");

        assertThat(handler.tryAuth(msg)).isNull();
    }

    @Test
    void tryAuth_wrongType_returnsNull() {
        String token = jwtService.mint("u-42").token();
        WebSocketMessage msg = textMessage("{\"type\":\"PING\",\"token\":\"" + token + "\"}");

        assertThat(handler.tryAuth(msg)).isNull();
    }

    @Test
    void tryAuth_clientSuppliedUserIdInPayload_isIgnored() {
        // The classic impersonation attempt: pretend to be a different user by including a userId
        // field. The handler must not honor it; userId must come from JWT.sub only.
        String token = jwtService.mint("u-42").token();
        WebSocketMessage msg =
                textMessage(
                        "{\"type\":\"AUTH\",\"token\":\""
                                + token
                                + "\",\"userId\":\"u-attacker\"}");

        assertThat(handler.tryAuth(msg)).isEqualTo("u-42");
    }

    @Test
    void tryAuth_malformedJson_returnsNull() {
        assertThat(handler.tryAuth(textMessage("not-json"))).isNull();
        assertThat(handler.tryAuth(textMessage("{"))).isNull();
        assertThat(handler.tryAuth(textMessage(""))).isNull();
    }

    @Test
    void tryAuth_oversizedFrame_returnsNullWithoutParsing() {
        // 5000 chars > 4096 limit.
        String huge = "{\"type\":\"AUTH\",\"token\":\"" + "x".repeat(5000) + "\"}";
        assertThat(handler.tryAuth(textMessage(huge))).isNull();
    }

    @Test
    void tryAuth_nonTextFrame_returnsNull() {
        WebSocketMessage binary = new WebSocketMessage(Type.BINARY, BUF.wrap(new byte[] {1, 2, 3}));
        assertThat(handler.tryAuth(binary)).isNull();
    }

    @Test
    void tryAuth_invalidSignature_returnsNull() {
        // Mint with a different secret, attempt to present as authenticated.
        WsJwtProperties otherProps = new WsJwtProperties();
        otherProps.setSecret("another-secret-also-at-least-32-chars-yyy");
        WsJwtService other = new WsJwtService(otherProps, mapper);
        other.init();
        String foreign = other.mint("u-1").token();
        WebSocketMessage msg = textMessage("{\"type\":\"AUTH\",\"token\":\"" + foreign + "\"}");

        assertThat(handler.tryAuth(msg)).isNull();
    }

    @Test
    void tryAuth_garbageToken_returnsNull() {
        WebSocketMessage msg = textMessage("{\"type\":\"AUTH\",\"token\":\"a.b.c\"}");
        assertThat(handler.tryAuth(msg)).isNull();
    }

    @Test
    void isOriginAllowed_acceptsConfiguredOrigin() {
        assertThat(handler.isOriginAllowed("https://chronoflow.example.com")).isTrue();
        assertThat(handler.isOriginAllowed("http://localhost:5173")).isTrue();
    }

    @Test
    void isOriginAllowed_rejectsUnknownOrigin() {
        assertThat(handler.isOriginAllowed("https://evil.com")).isFalse();
        assertThat(handler.isOriginAllowed("http://localhost:5174")).isFalse(); // wrong port
        assertThat(handler.isOriginAllowed("https://chronoflow.example.com.evil.com")).isFalse();
    }

    @Test
    void isOriginAllowed_rejectsMissingOrigin() {
        assertThat(handler.isOriginAllowed(null)).isFalse();
        assertThat(handler.isOriginAllowed("")).isFalse();
        assertThat(handler.isOriginAllowed("   ")).isFalse();
    }

    @Test
    void isOriginAllowed_failsClosedWhenAllowlistEmpty() {
        WsHandshakeProperties empty = new WsHandshakeProperties();
        WsHandler emptyHandler =
                new WsHandler(mock(LocalConnectionRegistry.class), jwtService, empty, mapper);

        assertThat(emptyHandler.isOriginAllowed("https://chronoflow.example.com")).isFalse();
        assertThat(emptyHandler.isOriginAllowed("http://localhost:5173")).isFalse();
        assertThat(emptyHandler.isOriginAllowed(null)).isFalse();
    }

    private static WebSocketMessage textMessage(String text) {
        return new WebSocketMessage(Type.TEXT, BUF.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }
}
