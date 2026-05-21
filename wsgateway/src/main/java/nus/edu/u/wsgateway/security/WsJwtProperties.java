package nus.edu.u.wsgateway.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for the short-lived WebSocket-only JWT (PLS 03 CSWH/auth control). */
@Component
@ConfigurationProperties(prefix = "chronoflow.websocket.jwt")
@Data
public class WsJwtProperties {

    /** Token issuer claim (iss). */
    private String issuer = "chronoflow";

    /** Token audience claim (aud). Scope the token to WebSocket use only. */
    private String audience = "chronoflow-ws";

    /** Custom claim asserting this token's only legitimate use. */
    private String purpose = "websocket-auth";

    /** Token lifetime in seconds. Keep short; fresh token is fetched per WS connection. */
    private long ttlSeconds = 60;

    /** HMAC-SHA256 signing secret. Must be >= 32 chars. Sourced from env / Secret Manager. */
    private String secret;

    /** Permitted clock skew when verifying exp, in seconds. */
    private long clockSkewSeconds = 5;
}
