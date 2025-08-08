package com.example.jwtauthenticator.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 🎯 STANDARDIZED ERROR RESPONSE DTO
 * 
 * Provides consistent error response format across all endpoints
 * Supports different error types and validation errors
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDTO {
    
    // ==================== BASIC ERROR INFO ====================
    
    /**
     * Success flag - always false for error responses
     */
    @Builder.Default
    private boolean success = false;
    
    /**
     * HTTP status code
     */
    private int status;
    
    /**
     * Machine-readable error code for client applications
     * Examples: "AUTHENTICATION_FAILED", "INVALID_API_KEY", "QUOTA_EXCEEDED"
     */
    private String errorCode;
    
    /**
     * Human-readable error message
     */
    private String message;
    
    /**
     * Detailed error description for developers
     */
    private String details;
    
    /**
     * Timestamp when error occurred
     */
    @Builder.Default
    private String timestamp = Instant.now().toString();
    
    // ==================== ADDITIONAL ERROR INFO ====================
    
    /**
     * Request path where error occurred
     */
    private String path;
    
    /**
     * Request method (GET, POST, etc.)
     */
    private String method;
    
    /**
     * Trace ID for debugging (useful in distributed systems)
     */
    private String traceId;
    
    /**
     * Validation errors for form submissions
     */
    private List<ValidationErrorDTO> validationErrors;
    
    /**
     * Additional context information
     */
    private Map<String, Object> context;
    
    // ==================== NESTED DTO ====================
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationErrorDTO {
        private String field;
        private String message;
        private Object rejectedValue;
        private String code;
    }
    
    // ==================== BUILDER HELPERS ====================
    
    /**
     * Create basic error response
     */
    public static ErrorResponseDTO basic(int status, String errorCode, String message, String details) {
        return ErrorResponseDTO.builder()
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .build();
    }
    
    /**
     * Create authentication error
     */
    public static ErrorResponseDTO authenticationFailed(String details) {
        return ErrorResponseDTO.builder()
                .status(401)
                .errorCode("AUTHENTICATION_FAILED")
                .message("Authentication failed")
                .details(details)
                .build();
    }
    
    /**
     * Create authorization error
     */
    public static ErrorResponseDTO forbidden(String details) {
        return ErrorResponseDTO.builder()
                .status(403)
                .errorCode("FORBIDDEN")
                .message("Access denied")
                .details(details)
                .build();
    }
    
    /**
     * Create validation error
     */
    public static ErrorResponseDTO validationFailed(String details) {
        return ErrorResponseDTO.builder()
                .status(400)
                .errorCode("VALIDATION_FAILED")
                .message("Validation failed")
                .details(details)
                .build();
    }
    
    /**
     * Create not found error
     */
    public static ErrorResponseDTO notFound(String resource, String details) {
        return ErrorResponseDTO.builder()
                .status(404)
                .errorCode("RESOURCE_NOT_FOUND")
                .message(resource + " not found")
                .details(details)
                .build();
    }
    
    /**
     * Create internal server error
     */
    public static ErrorResponseDTO internalError(String details) {
        return ErrorResponseDTO.builder()
                .status(500)
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An internal error occurred")
                .details(details)
                .build();
    }
    
    /**
     * Create quota exceeded error
     */
    public static ErrorResponseDTO quotaExceeded(String details) {
        return ErrorResponseDTO.builder()
                .status(429)
                .errorCode("QUOTA_EXCEEDED")
                .message("Quota limit exceeded")
                .details(details)
                .build();
    }
}