package ru.coproducts.moysklad.reco.vendor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.coproducts.moysklad.reco.util.HmacBase64Support;
import ru.coproducts.moysklad.reco.util.WebClientResilienceSupport;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class VendorContextClient {

    private static final Logger log = LoggerFactory.getLogger(VendorContextClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final String appUid;
    private final byte[] secretKey;
    private final Retry retrySpec;

    public VendorContextClient(
            @Value("${moysklad.vendor.base-url:https://apps-api.moysklad.ru/api/vendor/1.0}") String baseUrl,
            @Value("${moysklad.vendor.app-uid}") String appUid,
            @Value("${moysklad.vendor.secret-key}") String secretKey,
            @Value("${moysklad.vendor.context-connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${moysklad.vendor.context-response-timeout-seconds:15}") long responseTimeoutSeconds,
            @Value("${moysklad.vendor.context-retry-max-attempts:2}") int retryMaxAttempts,
            @Value("${moysklad.vendor.context-retry-backoff-millis:500}") long retryBackoffMillis,
            WebClient.Builder builder
    ) {
        this.webClient = builder
                .clientConnector(WebClientResilienceSupport.buildConnector(
                        connectTimeoutMs,
                        Duration.ofSeconds(responseTimeoutSeconds)
                ))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.appUid = appUid;
        this.secretKey = secretKey.getBytes(StandardCharsets.UTF_8);
        this.retrySpec = WebClientResilienceSupport.buildRetrySpec(
                "Vendor context request",
                retryMaxAttempts,
                Duration.ofMillis(retryBackoffMillis),
                log
        );
    }

    public Optional<JsonNode> fetchContextByKey(String contextKey) {
        if (contextKey == null || contextKey.isBlank()) {
            return Optional.empty();
        }

        String jwt = buildVendorJwt();
        JsonNode response = webClient.post()
                .uri("/context/{contextKey}", contextKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(retrySpec)
                .doOnError(e -> log.warn("Failed to resolve contextKey in Vendor API: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .block();

        return Optional.ofNullable(response);
    }

    String buildVendorJwt() {
        long now = Instant.now().getEpochSecond();
        long exp = now + 300L;
        String header = HmacBase64Support.base64UrlEncode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload;
        try {
            payload = HmacBase64Support.base64UrlEncode(OBJECT_MAPPER.writeValueAsString(Map.of(
                    "sub", appUid,
                    "iat", now,
                    "exp", exp,
                    "jti", UUID.randomUUID().toString()
            )).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build vendor JWT payload", e);
        }
        String data = header + "." + payload;
        String signature = HmacBase64Support.base64UrlEncode(HmacBase64Support.hmacSha256(data.getBytes(StandardCharsets.UTF_8), secretKey));
        return data + "." + signature;
    }
}
