package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.jwtauthenticator.entity.ApiKey; // Import the entity

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private String prefix;
    private boolean isActive;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private List<String> allowedIps;
    private List<String> allowedDomains;
    private String rateLimitTier;

    // Static factory method for converting an ApiKey entity to this DTO
    public static ApiKeyResponseDTO fromEntity(ApiKey apiKey) {
        return ApiKeyResponseDTO.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .description(apiKey.getDescription())
                .prefix(apiKey.getPrefix())
                .isActive(apiKey.isActive())
                .expiresAt(apiKey.getExpiresAt())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .revokedAt(apiKey.getRevokedAt())
                // Use helper methods from entity to convert comma-separated string to List
                .allowedIps(apiKey.getAllowedIpsAsList())
                .allowedDomains(apiKey.getAllowedDomainsAsList())
                .rateLimitTier(apiKey.getRateLimitTier())
                .build();
    }
}