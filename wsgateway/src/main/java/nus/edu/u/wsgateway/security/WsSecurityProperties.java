package nus.edu.u.wsgateway.security;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime security controls for the WebSocket HTTP surface. */
@Component
@ConfigurationProperties(prefix = "chronoflow.websocket.security")
@Data
public class WsSecurityProperties {

    /**
     * Shared service-to-service secret required on /ws/internal/**.
     *
     * <p>Empty means fail-closed: the internal endpoint rejects all calls.
     */
    private String internalServiceToken = "";

    /** Per-user limit for POST /ws/token minting. */
    private int tokenRateLimit = 20;

    /** Rate limit window for POST /ws/token minting. */
    private Duration tokenRateWindow = Duration.ofMinutes(1);

    /** Redis-backed single-use enforcement for WS JWT jti values. */
    private boolean replayProtectionEnabled = true;
}
