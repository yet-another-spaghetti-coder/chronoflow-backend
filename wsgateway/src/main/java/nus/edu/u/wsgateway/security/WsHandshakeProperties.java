package nus.edu.u.wsgateway.security;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Handshake-time security policy (PLS 03: Origin validation against CSWH).
 *
 * <p>Default {@code allowedOrigins} is empty, which means <b>reject all</b> — fail-closed. Dev and
 * prod environments must explicitly enumerate trusted browser origins (e.g., {@code
 * https://chronoflow.example.com}, {@code http://localhost:5173}).
 */
@Component
@ConfigurationProperties(prefix = "chronoflow.websocket.handshake")
@Data
public class WsHandshakeProperties {

    /** Exact-match list of Origin header values allowed during the WS handshake. */
    private List<String> allowedOrigins = new ArrayList<>();
}
