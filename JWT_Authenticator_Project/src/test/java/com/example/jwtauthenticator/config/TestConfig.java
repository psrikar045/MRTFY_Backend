package com.example.jwtauthenticator.config;

import com.example.jwtauthenticator.service.IdGeneratorService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestConfiguration
@Profile("test")
public class TestConfig {

    
    @Bean
    @Primary
    public IdGeneratorService idGeneratorService() {
        IdGeneratorService mockService = Mockito.mock(IdGeneratorService.class);
        // Configure the mock to return a predictable ID
        when(mockService.generateDombrUserId()).thenReturn("DOMBR000001");
        when(mockService.generateSimpleDombrUserId()).thenReturn("DOMBR000001");
        when(mockService.generateNextId()).thenReturn("MRTFY0001");
        return mockService;
    }
    
    @Bean
    @Primary
    public AppConfig appConfig() {
        AppConfig mockConfig = Mockito.mock(AppConfig.class);
        when(mockConfig.getApiUrl(anyString())).thenReturn("http://localhost:8080/api/auth/verify-email?token=test-token");
        return mockConfig;
    }
}