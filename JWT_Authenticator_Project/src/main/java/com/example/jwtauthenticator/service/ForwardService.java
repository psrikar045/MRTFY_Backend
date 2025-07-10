package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.ForwardConfig;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForwardService {

    private static final String EXTERNAL_API = "https://sumnode-main.onrender.com/api/extract-company-details";
    private final WebClient forwardWebClient;
    private final Cache<String, String> forwardCache;
    private final ForwardConfig forwardConfig;

    public CompletableFuture<ResponseEntity<String>> forward(String url) {
        String cached = forwardCache.getIfPresent(url);
        if (cached != null) {
            return CompletableFuture.completedFuture(ResponseEntity.ok(cached));
        }

        return forwardWebClient.post()
                .uri(EXTERNAL_API)
                .bodyValue(Collections.singletonMap("url", url))
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .map(body -> ResponseEntity.status(resp.statusCode()).body(body)))
                .timeout(Duration.ofSeconds(forwardConfig.getTimeoutSeconds()))
                .doOnError(e -> log.error("Forwarding error", e))
                .toFuture()
                .thenApply(response -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        forwardCache.put(url, response.getBody());
                    }
                    return response;
                });
    }

    public ForwardConfig getForwardConfig() {
        return forwardConfig;
    }
}
