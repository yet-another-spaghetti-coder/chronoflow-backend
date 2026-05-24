package nus.edu.u.wsgateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mint and verify the short-lived WebSocket-only JWT (HS256).
 *
 * <p>This token is scoped to WebSocket connection authentication and is minted only after the
 * normal Sa-Token HTTP session is validated. It is intentionally separate from the main Sa-Token
 * access token, so a leak of this token cannot escalate to general API access (different audience +
 * purpose + short TTL).
 *
 * <p>Signed with HMAC-SHA256 using {@link WsJwtProperties#getSecret()}. No external JWT library:
 * minimal attack surface and easy to audit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WsJwtService {

    private static final String ALG = "HS256";
    private static final String TYP = "JWT";
    private static final String MAC_ALG = "HmacSHA256";
    private static final int MIN_SECRET_LEN = 32;
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();
    private static final SecureRandom RNG = new SecureRandom();

    private final WsJwtProperties props;
    private final ObjectMapper mapper;

    private byte[] secretBytes;

    @PostConstruct
    public void init() {
        String secret = props.getSecret();
        if (secret == null || secret.length() < MIN_SECRET_LEN) {
            throw new IllegalStateException(
                    "chronoflow.websocket.jwt.secret must be set and at least "
                            + MIN_SECRET_LEN
                            + " characters");
        }
        secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Mint a fresh WS JWT bound to the given userId. */
    public Minted mint(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        long now = Instant.now().getEpochSecond();
        long exp = now + props.getTtlSeconds();
        String jti = newJti();

        ObjectNode header = mapper.createObjectNode();
        header.put("alg", ALG);
        header.put("typ", TYP);

        ObjectNode payload = mapper.createObjectNode();
        payload.put("sub", userId);
        payload.put("iss", props.getIssuer());
        payload.put("aud", props.getAudience());
        payload.put("purpose", props.getPurpose());
        payload.put("iat", now);
        payload.put("exp", exp);
        payload.put("jti", jti);

        String headerB64 = encodeJson(header);
        String payloadB64 = encodeJson(payload);
        String signingInput = headerB64 + "." + payloadB64;
        byte[] sig = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8));
        String sigB64 = B64URL.encodeToString(sig);
        return new Minted(signingInput + "." + sigB64, exp, jti);
    }

    /**
     * Verify a WS JWT. Throws {@link JwtVerificationException} on any failure with a generic reason
     * code suitable for logging; never echoes token content back to clients.
     */
    public VerifiedClaims verify(String token) throws JwtVerificationException {
        if (token == null || token.isBlank()) {
            throw new JwtVerificationException("empty_token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new JwtVerificationException("malformed");
        }
        String headerB64 = parts[0];
        String payloadB64 = parts[1];
        String sigB64 = parts[2];

        byte[] expectedSig =
                hmacSha256((headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8));
        byte[] providedSig;
        try {
            providedSig = B64URL_DEC.decode(sigB64);
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("bad_sig_encoding");
        }
        if (!MessageDigest.isEqual(expectedSig, providedSig)) {
            throw new JwtVerificationException("bad_signature");
        }

        JsonNode header;
        JsonNode payload;
        try {
            header = mapper.readTree(B64URL_DEC.decode(headerB64));
            payload = mapper.readTree(B64URL_DEC.decode(payloadB64));
        } catch (Exception e) {
            throw new JwtVerificationException("bad_json");
        }
        if (!ALG.equals(header.path("alg").asText())) {
            throw new JwtVerificationException("bad_alg");
        }
        if (!TYP.equals(header.path("typ").asText())) {
            throw new JwtVerificationException("bad_typ");
        }
        if (!props.getIssuer().equals(payload.path("iss").asText())) {
            throw new JwtVerificationException("bad_iss");
        }
        if (!props.getAudience().equals(payload.path("aud").asText())) {
            throw new JwtVerificationException("bad_aud");
        }
        if (!props.getPurpose().equals(payload.path("purpose").asText())) {
            throw new JwtVerificationException("bad_purpose");
        }

        long now = Instant.now().getEpochSecond();
        long exp = payload.path("exp").asLong(-1);
        if (exp <= 0 || now > exp + props.getClockSkewSeconds()) {
            throw new JwtVerificationException("expired");
        }

        String sub = payload.path("sub").asText("");
        if (sub.isBlank()) {
            throw new JwtVerificationException("missing_sub");
        }
        String jti = payload.path("jti").asText(null);

        return new VerifiedClaims(sub, jti, exp);
    }

    private String encodeJson(ObjectNode node) {
        try {
            return B64URL.encodeToString(mapper.writeValueAsBytes(node));
        } catch (Exception e) {
            throw new IllegalStateException("ws jwt json encode failed", e);
        }
    }

    private byte[] hmacSha256(byte[] data) {
        try {
            Mac mac = Mac.getInstance(MAC_ALG);
            mac.init(new SecretKeySpec(secretBytes, MAC_ALG));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("hmac failure", e);
        }
    }

    private static String newJti() {
        byte[] buf = new byte[16];
        RNG.nextBytes(buf);
        return B64URL.encodeToString(buf);
    }

    /** Result of a successful mint. */
    public record Minted(String token, long expEpochSeconds, String jti) {}

    /** Verified, authenticated identity carried by a WS JWT. */
    public record VerifiedClaims(String userId, String jti, long expEpochSeconds) {}

    /** Thrown on any verification failure. Message is a short reason code, not user-facing. */
    public static class JwtVerificationException extends Exception {
        public JwtVerificationException(String reason) {
            super(reason);
        }
    }
}
