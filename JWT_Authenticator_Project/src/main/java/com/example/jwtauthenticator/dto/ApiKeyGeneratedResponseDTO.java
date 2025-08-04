package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyGeneratedResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private String prefix;
    private String keyValue; // The actual key string, only for initial display
    private String registeredDomain;
    private String mainDomain;
    private String subdomainPattern;
    private String environment;
    private String allowedDomains;
    private String allowedIps;
    private String scopes;
    private String rateLimitTier;
    private Boolean isActive;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}