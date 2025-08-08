package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import lombok.Builder;
import lombok.Data;

/**
 * 🔒 Security Validation Result
 * 
 * Represents the result of security validation for API requests.
 * Contains all necessary information about the validation outcome.
 */
@Data
@Builder
public class SecurityValidationResult {
    
    private boolean success;
    private String errorMessage;
    private String errorCode;
    private ApiKey apiKey;
    private User user;
    private String validatedDomain;
    private String clientIp;
    private String validationType; // "DOMAIN_VALIDATED", "IP_VALIDATED", etc.
    
    // ==================== FACTORY METHODS ====================
    
    /**
     * Create successful validation result
     */
    public static SecurityValidationResult success(ApiKey apiKey, User user, 
            String validatedDomain, String clientIp, String validationType) {
        return SecurityValidationResult.builder()
            .success(true)
            .apiKey(apiKey)
            .user(user)
            .validatedDomain(validatedDomain)
            .clientIp(clientIp)
            .validationType(validationType)
            .build();
    }
    
    /**
     * Create authentication failure result
     */
    public static SecurityValidationResult authFailure(String errorMessage, String errorCode) {
        return SecurityValidationResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .build();
    }
    
    /**
     * Create domain validation failure result
     */
    public static SecurityValidationResult domainFailure(String errorMessage, String errorCode, 
            String requestedDomain, String allowedDomain) {
        return SecurityValidationResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .validatedDomain(requestedDomain)
            .build();
    }
    
    /**
     * Create rate limit failure result
     */
    public static SecurityValidationResult rateLimitFailure(String errorMessage, String errorCode) {
        return SecurityValidationResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .build();
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Check if validation was successful
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Check if validation failed
     */
    public boolean isFailure() {
        return !success;
    }
    
    /**
     * Get error message for failed validations
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Get error code for failed validations
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Get validated API key (only for successful validations)
     */
    public ApiKey getApiKey() {
        return apiKey;
    }
    
    /**
     * Get validated user (only for successful validations)
     */
    public User getUser() {
        return user;
    }
    
    /**
     * Get validated domain or IP
     */
    public String getValidatedDomain() {
        return validatedDomain;
    }
    
    /**
     * Get client IP address
     */
    public String getClientIp() {
        return clientIp;
    }
    
    /**
     * Get validation type (DOMAIN_VALIDATED, IP_VALIDATED, etc.)
     */
    public String getValidationType() {
        return validationType;
    }
}