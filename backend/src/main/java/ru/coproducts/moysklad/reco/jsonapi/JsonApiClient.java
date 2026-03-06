package ru.coproducts.moysklad.reco.jsonapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.coproducts.moysklad.reco.util.WebClientResilienceSupport;

import java.time.Duration;

/**
 * Клиент для JSON API 1.2 МоегоСклада.
 * Использует токен, полученный при активации решения по Vendor API.
 */
@Component
public class JsonApiClient {

    private static final Logger log = LoggerFactory.getLogger(JsonApiClient.class);

    private final WebClient webClient;
    private final Retry retrySpec;

    public JsonApiClient(
            @Value("${moysklad.json-api.base-url:https://api.moysklad.ru/api/remap/1.2}") String baseUrl,
            @Value("${moysklad.json-api.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${moysklad.json-api.response-timeout-seconds:30}") long responseTimeoutSeconds,
            @Value("${moysklad.json-api.retry-max-attempts:2}") int retryMaxAttempts,
            @Value("${moysklad.json-api.retry-backoff-millis:500}") long retryBackoffMillis,
            WebClient.Builder builder
    ) {
        this.webClient = builder
                .clientConnector(WebClientResilienceSupport.buildConnector(
                        connectTimeoutMs,
                        Duration.ofSeconds(responseTimeoutSeconds)
                ))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json;charset=utf-8")
                .build();
        this.retrySpec = WebClientResilienceSupport.buildRetrySpec(
                "MoySklad JSON API request",
                retryMaxAttempts,
                Duration.ofMillis(retryBackoffMillis),
                log
        );
    }

    /**
     * Базовый GET-запрос к JSON API c авторизацией по токену.
     */
    public JsonNode get(String token, String path) {
        return webClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(retrySpec)
                .doOnError(e -> logWebClientError("GET " + path, e))
                .onErrorResume(e -> Mono.empty())
                .block();
    }

    /**
     * Получить товар по ID.
     * Фактический формат пути и параметров следует уточнить по документации JSON API 1.2.
     */
    public JsonNode getProductById(String token, String productId) {
        return get(token, "/entity/product/" + productId);
    }

    public JsonNode fetchDemandsPage(String token, String filter, int limit, int offset) {
        return fetchDocumentPage(token, "/entity/demand", filter, limit, offset, "demand");
    }

    public JsonNode fetchRetailDemandsPage(String token, String filter, int limit, int offset) {
        return fetchDocumentPage(token, "/entity/retaildemand", filter, limit, offset, "retaildemand");
    }

    private JsonNode fetchDocumentPage(String token, String path, String filter, int limit, int offset, String logName) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .queryParam("expand", "positions.assortment")
                        .queryParam("filter", filter)
                .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .retryWhen(retrySpec)
                .doOnError(e -> logWebClientError(logName + " page filter=" + filter + " limit=" + limit + " offset=" + offset, e))
                .block();
    }

    private void logWebClientError(String operation, Throwable error) {
        if (error instanceof WebClientResponseException responseException) {
            log.error("Error calling JSON API {}: status={} body={}",
                    operation,
                    responseException.getStatusCode(),
                    responseException.getResponseBodyAsString());
            return;
        }
        log.error("Error calling JSON API {}: {}", operation, error.getMessage());
    }
}
