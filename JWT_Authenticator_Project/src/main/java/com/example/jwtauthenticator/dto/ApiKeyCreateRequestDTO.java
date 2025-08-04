package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for creating a new API key with domain-based access control")
public class ApiKeyCreateRequestDTO {
    @NotBlank(message = "Key name cannot be blank")
    @Size(max = 255)
    @Schema(description = "Name for the API key", example = "My Production API Key", required = true)
    private String name;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Schema(description = "Optional description for the API key", example = "API key for production website integration")
    private String description; // Optional
    
    @NotBlank(message = "Registered domain is required")
    @Size(max = 255, message = "Domain cannot exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", 
             message = "Invalid domain format. Use format: example.com or subdomain.example.com")
    @Schema(description = "Primary registered domain for this API key (must be unique)", 
            example = "api.mywebsite.com", required = true)
    private String registeredDomain;
    
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Prefix can only contain letters, numbers, hyphens, and underscores")
    @Size(max = 10, message = "Prefix cannot exceed 10 characters")
    private String prefix; // Optional (e.g., "sk-", "admin-", "biz-")
    
    @Future(message = "Expiration date must be in the future")
    private LocalDateTime expiresAt; // Optional: specific expiration date/time
    
    // Optional: for IP restrictions
    @Schema(description = "Optional list of allowed IP addresses for additional security", 
            example = "[\"192.168.1.100\", \"10.0.0.50\"]")
    private List<String> allowedIps; // TODO: Add IP validation    
    
    // Optional: for additional domains (future hybrid approach)
    @Schema(description = "Optional additional domains (for future hybrid approach)", 
            example = "[\"backup.mywebsite.com\", \"staging.mywebsite.com\"]")
    private List<String> allowedDomains;
    
    @Pattern(regexp = "^(FREE_TIER|PRO_TIER|BUSINESS_TIER)$", 
             message = "Rate limit tier must be one of: FREE_TIER, PRO_TIER, BUSINESS_TIER")
    private String rateLimitTier; // Optional rate limiting tier
    
    // Scopes for API key permissions
    private List<String> scopes; // Optional: List of scope names (e.g., ["READ_USERS", "WRITE_BRANDS"])
}