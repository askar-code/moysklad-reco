package ru.coproducts.moysklad.reco.vendor.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.coproducts.moysklad.reco.util.HmacBase64Support;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendorJwtAuthFilterTest {

    @Test
    void allowsRequestWithValidToken() throws Exception {
        String secret = "test-secret";
        Instant now = Instant.parse("2026-03-06T07:00:00Z");
        VendorJwtAuthFilter filter = new VendorJwtAuthFilter(secret, Clock.fixed(now, ZoneOffset.UTC));

        String token = buildToken(secret, now, now.plusSeconds(600), "jti-1");

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/moysklad/vendor/1.0/apps/app-1/account-1");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Filter chain should be called for valid token");
        assertEquals(200, response.getStatus() == 0 ? 200 : response.getStatus());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        String secret = "test-secret";
        VendorJwtAuthFilter filter = new VendorJwtAuthFilter(secret, Clock.systemUTC());

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/moysklad/vendor/1.0/apps/app-1/account-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Filter chain should not be called");
        assertEquals(401, response.getStatus());
    }

    @Test
    void skipsNonVendorApiRoutes() throws Exception {
        String secret = "test-secret";
        VendorJwtAuthFilter filter = new VendorJwtAuthFilter(secret, Clock.systemUTC());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Non-vendor routes should not be filtered here");
    }

    @Test
    void rejectsReplayWhileTokenIsStillValid() throws Exception {
        String secret = "test-secret";
        Instant now = Instant.parse("2026-03-06T07:00:00Z");
        VendorJwtAuthFilter filter = new VendorJwtAuthFilter(secret, Clock.fixed(now, ZoneOffset.UTC));
        String token = buildToken(secret, now, now.plusSeconds(600), "replay-1");

        MockHttpServletRequest firstRequest = new MockHttpServletRequest("PUT", "/api/moysklad/vendor/1.0/apps/app-1/account-1");
        firstRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        AtomicBoolean firstProceeded = new AtomicBoolean(false);

        filter.doFilter(firstRequest, firstResponse, (req, res) -> firstProceeded.set(true));

        MockHttpServletRequest secondRequest = new MockHttpServletRequest("PUT", "/api/moysklad/vendor/1.0/apps/app-1/account-1");
        secondRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        AtomicBoolean secondProceeded = new AtomicBoolean(false);

        filter.doFilter(secondRequest, secondResponse, (req, res) -> secondProceeded.set(true));

        assertTrue(firstProceeded.get(), "First request should be accepted");
        assertFalse(secondProceeded.get(), "Replayed token should be rejected");
        assertEquals(401, secondResponse.getStatus());
    }

    @Test
    void allowsSameJtiAfterPreviousTokenExpired() throws Exception {
        String secret = "test-secret";
        Instant initialNow = Instant.parse("2026-03-06T07:00:00Z");
        MutableClock clock = new MutableClock(initialNow);
        VendorJwtAuthFilter filter = new VendorJwtAuthFilter(secret, clock);

        String expiredToken = buildToken(secret, initialNow, initialNow.plusSeconds(10), "replay-2");
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("PUT", "/api/moysklad/vendor/1.0/apps/app-1/account-1");
        firstRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        AtomicBoolean firstProceeded = new AtomicBoolean(false);

        filter.doFilter(firstRequest, firstResponse, (req, res) -> firstProceeded.set(true));

        clock.setInstant(initialNow.plusSeconds(20));

        String refreshedToken = buildToken(secret, initialNow.plusSeconds(20), initialNow.plusSeconds(620), "replay-2");
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("PUT", "/api/moysklad/vendor/1.0/apps/app-1/account-1");
        secondRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedToken);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        AtomicBoolean secondProceeded = new AtomicBoolean(false);

        filter.doFilter(secondRequest, secondResponse, (req, res) -> secondProceeded.set(true));

        assertTrue(firstProceeded.get(), "Initial request should be accepted");
        assertTrue(secondProceeded.get(), "Expired replay marker should not block a new token");
        assertEquals(200, secondResponse.getStatus() == 0 ? 200 : secondResponse.getStatus());
    }

    private String buildToken(String secret, Instant issuedAt, Instant expiresAt, String jti) throws Exception {
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String resolvedJti = jti != null ? jti : UUID.randomUUID().toString();
        String payloadJson = String.format(
                "{\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}",
                issuedAt.getEpochSecond(),
                expiresAt.getEpochSecond(),
                resolvedJti
        );

        String header = HmacBase64Support.base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = HmacBase64Support.base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String data = header + "." + payload;
        String signature = HmacBase64Support.base64UrlEncode(
                HmacBase64Support.hmacSha256(data.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))
        );

        return data + "." + signature;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }
    }
}
