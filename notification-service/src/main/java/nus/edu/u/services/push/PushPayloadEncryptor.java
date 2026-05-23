package nus.edu.u.services.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushPayloadEncryptor {

    public static final String ENVELOPE_ALG = "ECDH-P256+A256GCM";
    private static final byte[] HKDF_INFO =
            "chronoflow-web-push-payload-v1".getBytes(StandardCharsets.UTF_8);
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public Map<String, String> encrypt(
            String publicKeyJwk,
            String keyVersion,
            String title,
            String body,
            Map<String, Object> data)
            throws Exception {
        ECPublicKey recipientPublicKey = parsePublicKey(publicKeyJwk);

        var keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), secureRandom);
        var ephemeralKeyPair = keyPairGenerator.generateKeyPair();

        var keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(ephemeralKeyPair.getPrivate());
        keyAgreement.doPhase(recipientPublicKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();

        byte[] salt = randomBytes(16);
        byte[] aesKey = hkdfSha256(sharedSecret, salt, HKDF_INFO, AES_KEY_BYTES);
        byte[] iv = randomBytes(GCM_IV_BYTES);

        Map<String, Object> plaintext = new LinkedHashMap<>();
        plaintext.put("title", title == null || title.isBlank() ? "Notification" : title);
        plaintext.put("body", body == null ? "" : body);
        if (data != null && !data.isEmpty()) {
            plaintext.put("data", data);
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(plaintext));
        int tagOffset = encrypted.length - GCM_TAG_BYTES;

        ECPublicKey ephemeralPublicKey = (ECPublicKey) ephemeralKeyPair.getPublic();
        Map<String, String> envelope = new LinkedHashMap<>();
        envelope.put("encrypted", "1");
        envelope.put("v", "1");
        envelope.put("alg", ENVELOPE_ALG);
        envelope.put("kid", keyVersion == null || keyVersion.isBlank() ? "v1" : keyVersion);
        envelope.put(
                "epk", base64Url(objectMapper.writeValueAsBytes(toPublicJwk(ephemeralPublicKey))));
        envelope.put("salt", base64Url(salt));
        envelope.put("iv", base64Url(iv));
        envelope.put("ct", base64Url(Arrays.copyOfRange(encrypted, 0, tagOffset)));
        envelope.put("tag", base64Url(Arrays.copyOfRange(encrypted, tagOffset, encrypted.length)));
        return envelope;
    }

    private ECPublicKey parsePublicKey(String jwkJson) throws Exception {
        if (jwkJson == null || jwkJson.isBlank()) {
            throw new IllegalArgumentException("push encryption public key is required");
        }

        JsonNode node = objectMapper.readTree(jwkJson);
        if (!"EC".equals(node.path("kty").asText()) || !"P-256".equals(node.path("crv").asText())) {
            throw new IllegalArgumentException("push encryption public key must be EC P-256 JWK");
        }

        byte[] x = base64UrlDecode(node.path("x").asText());
        byte[] y = base64UrlDecode(node.path("y").asText());

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = parameters.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(point, ecSpec);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(publicKeySpec);
    }

    private Map<String, String> toPublicJwk(ECPublicKey publicKey) {
        Map<String, String> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", base64Url(unsignedCoordinate(publicKey.getW().getAffineX())));
        jwk.put("y", base64Url(unsignedCoordinate(publicKey.getW().getAffineY())));
        return jwk;
    }

    private byte[] unsignedCoordinate(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) return raw;
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private byte[] randomBytes(int len) {
        byte[] bytes = new byte[len];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);

        byte[] okm = new byte[length];
        byte[] previous = new byte[0];
        int offset = 0;
        int counter = 1;
        while (offset < length) {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(previous);
            mac.update(info);
            mac.update((byte) counter);
            previous = mac.doFinal();
            int toCopy = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, okm, offset, toCopy);
            offset += toCopy;
            counter++;
        }
        return okm;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
