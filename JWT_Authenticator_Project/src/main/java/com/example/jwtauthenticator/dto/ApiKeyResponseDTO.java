package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.jwtauthenticator.entity.ApiKey; // Import the entity
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API Key response with domain-based access control information")
public class ApiKeyResponseDTO {
    @Schema(description = "Unique identifier for the API key")
    private UUID id;
    
    @Schema(description = "Name of the API key")
    private String name;
    
    @Schema(description = "Description of the API key")
    private String description;
    
    @Schema(description = "Prefix for the API key")
    private String prefix;
    
    @Schema(description = "Masked preview of the API key (for identification only)", 
            example = "sk-1234...cdef")
    private String keyPreview;
    
    @Schema(description = "Whether the API key is active")
    private boolean isActive;
    
    @Schema(description = "Primary registered domain for this API key")
    private String registeredDomain;
    
    @Schema(description = "Expiration date and time")
    private LocalDateTime expiresAt;
    
    @Schema(description = "Creation date and time")
    private LocalDateTime createdAt;
    
    @Schema(description = "Last update date and time")
    private LocalDateTime updatedAt;
    
    @Schema(description = "Last used date and time")
    private LocalDateTime lastUsedAt;
    
    @Schema(description = "Revocation date and time")
    private LocalDateTime revokedAt;
    
    @Schema(description = "List of allowed IP addresses")
    private List<String> allowedIps;
    
    @Schema(description = "List of additional allowed domains")
    private List<String> allowedDomains;
    
    @Schema(description = "Rate limiting tier")
    private String rateLimitTier;
    
    @Schema(description = "List of granted scopes/permissions")
    private List<String> scopes;
    
    @Schema(description = "Environment where the API key is used", 
            example = "production", allowableValues = {"production", "development", "staging"})
    private String environment;
    
    @Schema(description = "Encrypted API key value for frontend decryption", 
            example = "v1:base64_salt:base64_iv:base64_encrypted:base64_auth_tag")
    private String encryptedKeyValue;

    // Static factory method for converting an ApiKey entity to this DTO
    public static ApiKeyResponseDTO fromEntity(ApiKey apiKey) {
        return ApiKeyResponseDTO.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .description(apiKey.getDescription())
                .prefix(apiKey.getPrefix())
                .keyPreview(apiKey.getDisplayPreview())
                .isActive(apiKey.isActive())
                .registeredDomain(apiKey.getRegisteredDomain()) // NEW: Include registered domain
                .expiresAt(apiKey.getExpiresAt())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .lastUsedAt(apiKey.getLastUsedAt())
                .revokedAt(apiKey.getRevokedAt())
                // Use helper methods from entity to convert comma-separated string to List
                .allowedIps(apiKey.getAllowedIpsAsList())
                .allowedDomains(apiKey.getAllowedDomainsAsList())
                .rateLimitTier(apiKey.getRateLimitTier() != null ? apiKey.getRateLimitTier().name() : null)
                .scopes(apiKey.getScopesAsList())
                .environment(apiKey.getEnvironment() != null ? apiKey.getEnvironment().getValue() : null)
                .encryptedKeyValue(apiKey.getEncryptedKeyValue())
                .build();
    }
}