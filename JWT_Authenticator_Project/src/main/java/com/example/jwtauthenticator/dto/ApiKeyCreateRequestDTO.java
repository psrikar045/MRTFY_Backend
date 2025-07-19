package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
    
    @Size(max = 1000)
    private String description; // Optional
    
    private String prefix; // Optional (e.g., "sk-")
    
    private LocalDateTime expiresAt; // Optional: specific expiration date/time
    
    // Optional: for IP/domain restrictions
    private List<String> allowedIps;    
    private List<String> allowedDomains; 
    
    private String rateLimitTier; // Optional (e.g., "FREE", "PRO")
}