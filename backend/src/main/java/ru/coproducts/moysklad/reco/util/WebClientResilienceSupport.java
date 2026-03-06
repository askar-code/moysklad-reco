package ru.coproducts.moysklad.reco.util;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

public final class WebClientResilienceSupport {

    private WebClientResilienceSupport() {
    }

    public static ReactorClientHttpConnector buildConnector(int connectTimeoutMs, Duration responseTimeout) {
        HttpClient httpClient = HttpClient.create()
                .compress(true)
                .followRedirect(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(responseTimeout);
        return new ReactorClientHttpConnector(httpClient);
    }

    public static Retry buildRetrySpec(String operation,
                                       int maxAttempts,
                                       Duration backoff,
                                       Logger log) {
        if (maxAttempts <= 0) {
            return Retry.max(0).filter(throwable -> false);
        }

        return Retry.backoff(maxAttempts, backoff)
                .filter(WebClientResilienceSupport::isRetriable)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying {} after failure {} of {}: {}",
                        operation,
                        signal.totalRetries() + 1,
                        maxAttempts,
                        signal.failure().getMessage()
                ));
    }

    static boolean isRetriable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().value() == 429
                    || responseException.getStatusCode().is5xxServerError();
        }
        if (throwable instanceof WebClientRequestException) {
            return true;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
