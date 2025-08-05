package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for API key operations like delete, revoke, etc.")
public class ApiKeyOperationResponseDTO {
    
    @Schema(description = "Whether the operation was successful")
    private boolean success;
    
    @Schema(description = "Human-readable message describing the operation result")
    private String message;
    
    @Schema(description = "The type of operation performed", example = "DELETE, REVOKE")
    private String operation;
    
    @Schema(description = "ID of the API key that was operated on")
    private UUID keyId;
    
    @Schema(description = "Name of the API key that was operated on")
    private String keyName;
    
    @Schema(description = "Prefix of the API key that was operated on")
    private String keyPrefix;
    
    @Schema(description = "Timestamp when the operation was performed")
    private String timestamp;
    
    @Schema(description = "Previous status of the API key before the operation")
    private String previousStatus;
    
    @Schema(description = "Current status of the API key after the operation")
    private String currentStatus;
    
    // Static factory methods for common operations
    public static ApiKeyOperationResponseDTO deleteSuccess(UUID keyId, String keyName, String keyPrefix) {
        return ApiKeyOperationResponseDTO.builder()
                .success(true)
                .message("API key deleted successfully")
                .operation("DELETE")
                .keyId(keyId)
                .keyName(keyName)
                .keyPrefix(keyPrefix)
                .timestamp(java.time.Instant.now().toString())
                .previousStatus("ACTIVE")
                .currentStatus("DELETED")
                .build();
    }
    
    public static ApiKeyOperationResponseDTO revokeSuccess(UUID keyId, String keyName, String keyPrefix) {
        return ApiKeyOperationResponseDTO.builder()
                .success(true)
                .message("API key revoked successfully")
                .operation("REVOKE")
                .keyId(keyId)
                .keyName(keyName)
                .keyPrefix(keyPrefix)
                .timestamp(java.time.Instant.now().toString())
                .previousStatus("ACTIVE")
                .currentStatus("REVOKED")
                .build();
    }
    
    public static ApiKeyOperationResponseDTO notFound(UUID keyId, String operation) {
        return ApiKeyOperationResponseDTO.builder()
                .success(false)
                .message("API key not found or does not belong to the authenticated user")
                .operation(operation)
                .keyId(keyId)
                .timestamp(java.time.Instant.now().toString())
                .build();
    }
}