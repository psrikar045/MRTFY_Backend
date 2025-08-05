package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for decrypted API key retrieval
 * 
 * SECURITY CONSIDERATIONS:
 * - This DTO contains the actual API key value
 * - Should only be used for secure endpoints with proper authentication
 * - Should be transmitted over HTTPS only
 * - Frontend should clear this data from memory after use
 * - Should not be logged or cached
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Decrypted API key response for secure viewing/copying")
public class ApiKeyDecryptedResponseDTO {
    
    @Schema(description = "Unique identifier for the API key")
    private UUID id;
    
    @Schema(description = "Name of the API key")
    private String name;
    
    @Schema(description = "The actual decrypted API key value", 
            example = "sk-1234567890abcdef1234567890abcdef12345678",
            accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty("keyValue")
    private String keyValue; // The actual decrypted API key - SENSITIVE DATA
    
    @Schema(description = "Masked preview of the API key for verification", 
            example = "sk-1234...cdef")
    private String keyPreview;
    
    @Schema(description = "Timestamp when this key was retrieved/decrypted")
    private LocalDateTime retrievedAt;
    
    @Schema(description = "Security warning message for frontend display")
    @Builder.Default
    private String securityWarning = "This is your actual API key. Store it securely and never share it publicly.";
    
    /**
     * Clear sensitive data from memory (should be called by frontend after use)
     * Note: This is a best-effort approach, actual memory clearing depends on GC
     */
    public void clearSensitiveData() {
        if (this.keyValue != null) {
            // Replace with zeros (though String immutability limits effectiveness)
            this.keyValue = "CLEARED";
        }
    }
    
    /**
     * Get a safe representation for logging (without the actual key value)
     */
    public String toSafeString() {
        return String.format("ApiKeyDecryptedResponseDTO{id=%s, name='%s', keyPreview='%s', retrievedAt=%s}", 
                           id, name, keyPreview, retrievedAt);
    }
}