package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyUpdateRequestDTO {
    @Size(max = 255)
    private String name; // Optional: only update if provided
    
    @Size(max = 1000)
    private String description; // Optional
    
    private Boolean isActive; // Optional: to activate/deactivate the key
    
    private LocalDateTime expiresAt; // Optional: to change expiration
    
    // Optional: for IP/domain restrictions
    private List<String> allowedIps;    
    private List<String> allowedDomains; 
    
    private String rateLimitTier; // Optional
}