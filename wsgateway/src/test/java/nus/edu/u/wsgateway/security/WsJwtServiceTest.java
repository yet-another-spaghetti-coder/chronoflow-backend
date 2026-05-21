package nus.edu.u.wsgateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Security-critical unit tests for the WebSocket-only JWT mint+verify pipeline. */
class WsJwtServiceTest {

    private static final String SECRET = "dev-secret-must-be-at-least-32-chars-long-xxxx";
    private WsJwtService service;
    private WsJwtProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        props = new WsJwtProperties();
        props.setSecret(SECRET);
        service = new WsJwtService(props, mapper);
        service.init();
    }

    @Test
    void mintThenVerify_returnsSameUserId() throws Exception {
        WsJwtService.Minted minted = service.mint("u-42");
        WsJwtService.VerifiedClaims claims = service.verify(minted.token());

        assertThat(claims.userId()).isEqualTo("u-42");
        assertThat(claims.jti()).isEqualTo(minted.jti()).isNotBlank();
        assertThat(claims.expEpochSeconds()).isEqualTo(minted.expEpochSeconds());
    }

    @Test
    void mint_rejectsBlankUserId() {
        assertThatThrownBy(() -> service.mint(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.mint(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_rejectsEmptyOrMalformedToken() {
        assertThatThrownBy(() -> service.verify(null))
                .isInstanceOf(WsJwtService.JwtVerificationException.class);
        assertThatThrownBy(() -> service.verify(""))
                .isInstanceOf(WsJwtService.JwtVerificationException.class);
        assertThatThrownBy(() -> service.verify("a.b"))
                .isInstanceOf(WsJwtService.JwtVerificationException.class);
        assertThatThrownBy(() -> service.verify("a.b.c.d"))
                .isInstanceOf(WsJwtService.JwtVerificationException.class);
    }

    @Test
    void verify_rejectsTokenSignedWithDifferentSecret() throws Exception {
        WsJwtProperties otherProps = new WsJwtProperties();
        otherProps.setSecret("another-secret-also-at-least-32-chars-yyy");
        WsJwtService other = new WsJwtService(otherProps, mapper);
        other.init();

        String fromOtherSecret = other.mint("u-1").token();
        assertThatThrownBy(() -> service.verify(fromOtherSecret))
                .isInstanceOf(WsJwtService.JwtVerificationException.class)
                .hasMessageContaining("bad_signature");
    }

    @Test
    void verify_rejectsExpiredToken() throws Exception {
        WsJwtProperties shortProps = new WsJwtProperties();
        shortProps.setSecret(SECRET);
        shortProps.setTtlSeconds(-30); // already expired beyond skew
        shortProps.setClockSkewSeconds(1);
        WsJwtService expiring = new WsJwtService(shortProps, mapper);
        expiring.init();

        String token = expiring.mint("u-1").token();
        assertThatThrownBy(() -> expiring.verify(token))
                .isInstanceOf(WsJwtService.JwtVerificationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verify_rejectsWrongAudience() throws Exception {
        WsJwtProperties mismatchProps = new WsJwtProperties();
        mismatchProps.setSecret(SECRET);
        mismatchProps.setAudience("not-chronoflow-ws");
        WsJwtService minter = new WsJwtService(mismatchProps, mapper);
        minter.init();

        String token = minter.mint("u-1").token();
        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(WsJwtService.JwtVerificationException.class)
                .hasMessageContaining("bad_aud");
    }

    @Test
    void verify_rejectsWrongPurpose() throws Exception {
        WsJwtProperties mismatchProps = new WsJwtProperties();
        mismatchProps.setSecret(SECRET);
        mismatchProps.setPurpose("password-reset"); // hostile reuse from another scope
        WsJwtService minter = new WsJwtService(mismatchProps, mapper);
        minter.init();

        String token = minter.mint("u-1").token();
        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(WsJwtService.JwtVerificationException.class)
                .hasMessageContaining("bad_purpose");
    }

    @Test
    void verify_rejectsTamperedPayload() throws Exception {
        // Re-encode payload with sub swapped to a different user, keep original signature.
        // A correctly-implemented verify must catch this via HMAC mismatch.
        WsJwtService.Minted minted = service.mint("u-1");
        String[] parts = minted.token().split("\\.");
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        String json = new String(decoded, StandardCharsets.UTF_8).replace("\"sub\":\"u-1\"", "\"sub\":\"u-attacker\"");
        String tamperedPayload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> service.verify(tampered))
                .isInstanceOf(WsJwtService.JwtVerificationException.class)
                .hasMessageContaining("bad_signature");
    }

    @Test
    void verify_rejectsAlgNone() throws Exception {
        // Classic JWT downgrade attack: alg:none with empty signature.
        String headerNoneB64 =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "{\"alg\":\"none\",\"typ\":\"JWT\"}"
                                        .getBytes(StandardCharsets.UTF_8));
        long now = Instant.now().getEpochSecond();
        String payloadJson =
                "{\"sub\":\"u-1\",\"iss\":\"chronoflow\",\"aud\":\"chronoflow-ws\","
                        + "\"purpose\":\"websocket-auth\",\"iat\":"
                        + now
                        + ",\"exp\":"
                        + (now + 60)
                        + ",\"jti\":\"x\"}";
        String payloadB64 =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String forged = headerNoneB64 + "." + payloadB64 + ".";

        assertThatThrownBy(() -> service.verify(forged))
                .isInstanceOf(WsJwtService.JwtVerificationException.class);
    }

    @Test
    void init_failsFastWhenSecretTooShort() {
        WsJwtProperties weakProps = new WsJwtProperties();
        weakProps.setSecret("too-short");
        WsJwtService weakSvc = new WsJwtService(weakProps, mapper);

        assertThatThrownBy(weakSvc::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void signature_matchesHandComputedHmac() throws Exception {
        // Sanity check that we are actually HMAC-SHA256-signing header.payload, not something else.
        WsJwtService.Minted minted = service.mint("u-9");
        String[] parts = minted.token().split("\\.");
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        expected = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
        assertThat(MessageDigest.isEqual(expected, actual)).isTrue();
    }

    @Test
    void verifierFieldIsSetByInit() throws Exception {
        // Confirms PostConstruct binding actually wires the secret bytes.
        Field f = WsJwtService.class.getDeclaredField("secretBytes");
        f.setAccessible(true);
        byte[] bytes = (byte[]) f.get(service);
        assertThat(bytes).isEqualTo(SECRET.getBytes(StandardCharsets.UTF_8));
    }
}
