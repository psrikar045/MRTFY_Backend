package com.example.jwtauthenticator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.customsearch")
@Data
public class GoogleSearchConfig {
    private String apiKey;
    private String cx;
}