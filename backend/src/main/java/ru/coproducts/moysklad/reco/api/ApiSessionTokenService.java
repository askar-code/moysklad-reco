package ru.coproducts.moysklad.reco.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.coproducts.moysklad.reco.util.HmacBase64Support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class ApiSessionTokenService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final byte[] signingKey;
    private final long ttlSeconds;

    public ApiSessionTokenService(@Value("${moysklad.vendor.secret-key}") String vendorSecretKey,
                                  @Value("${moysklad.api.session-ttl-seconds:28800}") long ttlSeconds) {
        this.signingKey = (vendorSecretKey + ":api-session").getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = Math.max(60L, ttlSeconds);
    }

    public IssuedSession issue(String accountId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        String header = HmacBase64Support.base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload;
        try {
            payload = HmacBase64Support.base64UrlEncode(OBJECT_MAPPER.writeValueAsString(Map.of(
                    "accountId", accountId,
                    "iat", now.getEpochSecond(),
                    "exp", expiresAt.getEpochSecond()
            )).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build API session token payload", e);
        }

        String data = header + "." + payload;
        String signature = HmacBase64Support.base64UrlEncode(HmacBase64Support.hmacSha256(data.getBytes(StandardCharsets.UTF_8), signingKey));
        return new IssuedSession(data + "." + signature, expiresAt);
    }

    public Optional<String> verifyAndExtractAccountId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            String data = parts[0] + "." + parts[1];
            byte[] expectedSig = HmacBase64Support.hmacSha256(data.getBytes(StandardCharsets.UTF_8), signingKey);
            byte[] actualSig = HmacBase64Support.base64UrlDecode(parts[2]);
            if (!HmacBase64Support.constantTimeEquals(expectedSig, actualSig)) {
                return Optional.empty();
            }

            JsonNode payload = OBJECT_MAPPER.readTree(HmacBase64Support.base64UrlDecode(parts[1]));
            long now = Instant.now().getEpochSecond();
            if (now > payload.path("exp").asLong(0L)) {
                return Optional.empty();
            }

            String accountId = payload.path("accountId").asText(null);
            return (accountId != null && !accountId.isBlank()) ? Optional.of(accountId) : Optional.empty();
        } catch (IllegalArgumentException | java.io.IOException e) {
            return Optional.empty();
        }
    }

    public record IssuedSession(String token, Instant expiresAt) {
    }
}
