package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyCreateRequestDTO {
    @NotBlank(message = "Key name cannot be blank")
    @Size(max = 255)
    private String name;
    
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description; // Optional
    
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Prefix can only contain letters, numbers, hyphens, and underscores")
    @Size(max = 10, message = "Prefix cannot exceed 10 characters")
    private String prefix; // Optional (e.g., "sk-", "admin-", "biz-")
    
    @Future(message = "Expiration date must be in the future")
    private LocalDateTime expiresAt; // Optional: specific expiration date/time
    
    // Optional: for IP/domain restrictions
    private List<String> allowedIps; // TODO: Add IP validation    
    private List<String> allowedDomains; // TODO: Add domain validation
    
    @Pattern(regexp = "^(BASIC|STANDARD|PREMIUM|ENTERPRISE|UNLIMITED)$", 
             message = "Rate limit tier must be one of: BASIC, STANDARD, PREMIUM, ENTERPRISE, UNLIMITED")
    private String rateLimitTier; // Optional rate limiting tier
    
    // Scopes for API key permissions
    private List<String> scopes; // Optional: List of scope names (e.g., ["READ_USERS", "WRITE_BRANDS"])
}