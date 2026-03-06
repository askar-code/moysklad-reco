package ru.coproducts.moysklad.reco.vendor.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.coproducts.moysklad.reco.util.HmacBase64Support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Проверка подписи JWT-токена для входящих запросов по Vendor API.
 * Алгоритм: HS256 (HMAC SHA-256) с использованием secretKey.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VendorJwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VendorJwtAuthFilter.class);
    private static final int MAX_IAT_SKEW_SECONDS = 300;
    private static final int CLEANUP_INTERVAL = 256;

    private final String secretKey;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Long> usedJtiExpirations = new ConcurrentHashMap<>();
    private final AtomicInteger verificationCounter = new AtomicInteger();

    @Autowired
    public VendorJwtAuthFilter(
            @Value("${moysklad.vendor.secret-key}") String secretKey
    ) {
        this(secretKey, Clock.systemUTC());
    }

    VendorJwtAuthFilter(String secretKey, Clock clock) {
        this.secretKey = secretKey;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !isVendorPath(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            unauthorized(response, "Missing Authorization header");
            return;
        }

        String token = authHeader.substring("Bearer ".length());

        try {
            if (!verifyToken(token)) {
                unauthorized(response, "Invalid JWT");
                return;
            }
        } catch (Exception e) {
            log.error("Error verifying JWT: {}", e.getMessage());
            unauthorized(response, "JWT verification error");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean verifyToken(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        byte[] headerBytes = HmacBase64Support.base64UrlDecode(parts[0]);
        byte[] payloadBytes = HmacBase64Support.base64UrlDecode(parts[1]);
        String headerJson = new String(headerBytes, StandardCharsets.UTF_8);
        String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);

        JsonNode header = objectMapper.readTree(headerJson);
        if (!"HS256".equals(header.path("alg").asText())) {
            return false;
        }

        // Проверка подписи (constant-time)
        String data = parts[0] + "." + parts[1];
        byte[] expectedSig = HmacBase64Support.hmacSha256(data.getBytes(StandardCharsets.UTF_8), secretKey.getBytes(StandardCharsets.UTF_8));
        byte[] actualSig = HmacBase64Support.base64UrlDecode(parts[2]);
        if (!HmacBase64Support.constantTimeEquals(expectedSig, actualSig)) {
            return false;
        }

        JsonNode payload = objectMapper.readTree(payloadJson);
        long now = Instant.now(clock).getEpochSecond();

        long iat = payload.path("iat").asLong(0L);
        if (iat == 0L || iat > now + MAX_IAT_SKEW_SECONDS) {
            return false;
        }

        long exp = 0L;
        if (payload.has("exp")) {
            exp = payload.path("exp").asLong();
            if (now > exp) {
                return false;
            }
        }

        String jti = payload.path("jti").asText(null);
        if (jti != null) {
            cleanupExpiredJtiIfNeeded(now);
            long replayExpiry = exp > 0L ? exp : now + MAX_IAT_SKEW_SECONDS;
            final boolean[] replayDetected = {false};
            usedJtiExpirations.compute(jti, (key, currentExpiry) -> {
                if (currentExpiry != null && currentExpiry > now) {
                    replayDetected[0] = true;
                    return currentExpiry;
                }
                return replayExpiry;
            });
            if (replayDetected[0]) {
                return false;
            }
        }

        return true;
    }

    private void cleanupExpiredJtiIfNeeded(long now) {
        if (verificationCounter.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        usedJtiExpirations.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private boolean isVendorPath(String path) {
        return path.startsWith("/api/moysklad/vendor/1.0");
    }
}
