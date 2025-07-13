package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.ForwardConfig;
import com.example.jwtauthenticator.entity.Brand;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final BrandExtractionService brandExtractionService;
    
    @Value("${app.brand-extraction.enabled:true}")
    private boolean brandExtractionEnabled;

    public CompletableFuture<ResponseEntity<String>> forward(String url) {
        String cached = forwardCache.getIfPresent(url);
        if (cached != null) {
            // Even for cached responses, trigger brand extraction if enabled
            if (brandExtractionEnabled) {
                triggerBrandExtraction(url, cached);
            }
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
                        
                        // Trigger brand data extraction for successful responses
                        if (brandExtractionEnabled) {
                            triggerBrandExtraction(url, response.getBody());
                        }
                    }
                    return response;
                });
    }
    
    /**
     * Trigger brand data extraction asynchronously
     */
    private void triggerBrandExtraction(String url, String apiResponse) {
        try {
            log.info("Triggering brand extraction for URL: {}", url);
            Brand brand = brandExtractionService.extractAndStoreBrandData(url, apiResponse);
            log.info("Brand extraction completed for: {} (Brand ID: {})", url, brand.getId());
        } catch (Exception e) {
            log.error("Brand extraction failed for URL: {}", url, e);
            // Don't throw the exception as this shouldn't affect the main forward operation
        }
    }

    public ForwardConfig getForwardConfig() {
        return forwardConfig;
    }
}
