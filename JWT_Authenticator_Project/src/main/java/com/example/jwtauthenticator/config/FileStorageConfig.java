package com.example.jwtauthenticator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class FileStorageConfig {

    @Value("${app.file-storage.download.max-file-size:10485760}") // 10MB default
    private int maxFileSize;

    /**
     * Configure WebClient with increased memory buffer for file downloads
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Configure exchange strategies with increased memory buffer
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxFileSize * 2)) // Double the max file size
                .build();
        
        return WebClient.builder()
                .exchangeStrategies(strategies);
    }
}