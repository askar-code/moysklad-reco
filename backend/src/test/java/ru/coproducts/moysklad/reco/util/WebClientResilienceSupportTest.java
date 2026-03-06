package ru.coproducts.moysklad.reco.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientResilienceSupportTest {

    @Test
    void retriesOnServerError() {
        WebClientResponseException exception = WebClientResponseException.create(
                HttpStatus.BAD_GATEWAY.value(),
                "Bad Gateway",
                HttpHeaders.EMPTY,
                new byte[0],
                null
        );

        assertTrue(WebClientResilienceSupport.isRetriable(exception));
    }

    @Test
    void retriesOnTooManyRequests() {
        WebClientResponseException exception = WebClientResponseException.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too Many Requests",
                HttpHeaders.EMPTY,
                new byte[0],
                null
        );

        assertTrue(WebClientResilienceSupport.isRetriable(exception));
    }

    @Test
    void doesNotRetryOnGenericClientError() {
        WebClientResponseException exception = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                HttpHeaders.EMPTY,
                new byte[0],
                null
        );

        assertFalse(WebClientResilienceSupport.isRetriable(exception));
    }

    @Test
    void retriesOnRequestException() {
        WebClientRequestException exception = new WebClientRequestException(
                new IOException("connection reset"),
                null,
                URI.create("https://example.com"),
                HttpHeaders.EMPTY
        );

        assertTrue(WebClientResilienceSupport.isRetriable(exception));
    }

    @Test
    void retriesOnNestedTimeout() {
        RuntimeException exception = new RuntimeException(new TimeoutException("timed out"));

        assertTrue(WebClientResilienceSupport.isRetriable(exception));
    }
}
