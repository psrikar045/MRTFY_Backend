package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.error.ErrorResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * 🛡️ STANDARDIZED ERROR HANDLER SERVICE
 * 
 * Provides consistent error handling across all services and controllers
 * Includes logging, monitoring, and standardized response formats
 */
@Service
@Slf4j
public class ErrorHandlerService {
    
    // ==================== AUTHENTICATION ERRORS ====================
    
    /**
     * Handle authentication required errors
     */
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationRequired() {
        String traceId = generateTraceId();
        log.warn("🚫 [{}] Authentication required", traceId);
        
        ErrorResponseDTO error = ErrorResponseDTO.authenticationFailed(
            "Please provide valid JWT token in Authorization header")
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    /**
     * Handle authentication failed errors
     */
    public ResponseEntity<ErrorResponseDTO> handleAuthenticationFailed(String details, Exception exception) {
        String traceId = generateTraceId();
        log.error("🚫 [{}] Authentication failed: {}", traceId, details, exception);
        
        ErrorResponseDTO error = ErrorResponseDTO.authenticationFailed(details)
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    /**
     * Handle forbidden access errors
     */
    public ResponseEntity<ErrorResponseDTO> handleForbiddenAccess(String resource, String details) {
        String traceId = generateTraceId();
        log.warn("🚫 [{}] Forbidden access to {}: {}", traceId, resource, details);
        
        ErrorResponseDTO error = ErrorResponseDTO.forbidden(details)
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    // ==================== VALIDATION ERRORS ====================
    
    /**
     * Handle validation errors
     */
    public ResponseEntity<ErrorResponseDTO> handleValidationError(String field, String message, Object rejectedValue) {
        String traceId = generateTraceId();
        log.warn("❌ [{}] Validation error - {}: {}", traceId, field, message);
        
        ErrorResponseDTO.ValidationErrorDTO validationError = ErrorResponseDTO.ValidationErrorDTO.builder()
            .field(field)
            .message(message)
            .rejectedValue(rejectedValue)
            .code("INVALID_VALUE")
            .build();
        
        ErrorResponseDTO error = ErrorResponseDTO.validationFailed("Input validation failed")
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .validationErrors(java.util.List.of(validationError))
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle invalid user ID format
     */
    public ResponseEntity<ErrorResponseDTO> handleInvalidUserId(String userId) {
        String traceId = generateTraceId();
        log.warn("❌ [{}] Invalid userId format: {}", traceId, userId);
        
        ErrorResponseDTO error = ErrorResponseDTO.validationFailed(
            "User ID must follow MRTFY format (e.g., MRTFY000002)")
            .toBuilder()
            .errorCode("INVALID_USER_ID_FORMAT")
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * Handle missing required parameters
     */
    public ResponseEntity<ErrorResponseDTO> handleMissingParameter(String parameterName) {
        String traceId = generateTraceId();
        log.warn("❌ [{}] Missing required parameter: {}", traceId, parameterName);
        
        ErrorResponseDTO error = ErrorResponseDTO.validationFailed(
            "Required parameter '" + parameterName + "' is missing")
            .toBuilder()
            .errorCode("MISSING_PARAMETER")
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    // ==================== RESOURCE ERRORS ====================
    
    /**
     * Handle resource not found errors
     */
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(String resourceType, String resourceId) {
        String traceId = generateTraceId();
        log.warn("🔍 [{}] {} not found: {}", traceId, resourceType, resourceId);
        
        ErrorResponseDTO error = ErrorResponseDTO.notFound(resourceType, 
            resourceType + " with ID '" + resourceId + "' was not found or you don't have access to it")
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    // ==================== BUSINESS LOGIC ERRORS ====================
    
    /**
     * Handle quota exceeded errors
     */
    public ResponseEntity<ErrorResponseDTO> handleQuotaExceeded(String details, long currentUsage, long quotaLimit) {
        String traceId = generateTraceId();
        log.warn("📊 [{}] Quota exceeded: {}", traceId, details);
        
        ErrorResponseDTO error = ErrorResponseDTO.quotaExceeded(details)
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .context(java.util.Map.of(
                "currentUsage", currentUsage,
                "quotaLimit", quotaLimit,
                "usagePercentage", quotaLimit > 0 ? (currentUsage * 100.0) / quotaLimit : 0.0
            ))
            .build();
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }
    
    /**
     * Handle rate limiting errors
     */
    public ResponseEntity<ErrorResponseDTO> handleRateLimited(String details, long retryAfterSeconds) {
        String traceId = generateTraceId();
        log.warn("🚦 [{}] Rate limited: {}", traceId, details);
        
        ErrorResponseDTO error = ErrorResponseDTO.basic(429, "RATE_LIMITED", 
            "Rate limit exceeded", details)
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .context(java.util.Map.of(
                "retryAfterSeconds", retryAfterSeconds,
                "retryAfter", java.time.Instant.now().plusSeconds(retryAfterSeconds).toString()
            ))
            .build();
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }
    
    // ==================== SYSTEM ERRORS ====================
    
    /**
     * Handle internal server errors
     */
    public ResponseEntity<ErrorResponseDTO> handleInternalError(String operation, Exception exception) {
        String traceId = generateTraceId();
        log.error("💥 [{}] Internal error during {}: {}", traceId, operation, exception.getMessage(), exception);
        
        ErrorResponseDTO error = ErrorResponseDTO.internalError(
            "An internal error occurred during " + operation + ". Please try again later.")
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * Handle service unavailable errors
     */
    public ResponseEntity<ErrorResponseDTO> handleServiceUnavailable(String serviceName, String details) {
        String traceId = generateTraceId();
        log.error("🚫 [{}] Service unavailable - {}: {}", traceId, serviceName, details);
        
        ErrorResponseDTO error = ErrorResponseDTO.basic(503, "SERVICE_UNAVAILABLE",
            serviceName + " is temporarily unavailable", details)
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Generate unique trace ID for error tracking
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Get current request path
     */
    private String getCurrentPath() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            return request.getRequestURI();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Get current HTTP method
     */
    private String getCurrentMethod() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            return request.getMethod();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    // ==================== DASHBOARD SPECIFIC ERRORS ====================
    
    /**
     * Handle dashboard data unavailable
     */
    public ResponseEntity<ErrorResponseDTO> handleDashboardDataUnavailable(String userId) {
        String traceId = generateTraceId();
        log.warn("📊 [{}] Dashboard data unavailable for user: {}", traceId, userId);
        
        ErrorResponseDTO error = ErrorResponseDTO.basic(204, "DASHBOARD_DATA_UNAVAILABLE",
            "Dashboard data is being calculated", 
            "Dashboard data is not available yet. Please try again in a few moments.")
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .context(java.util.Map.of(
                "userId", userId,
                "retryRecommendation", "Try again in 30 seconds"
            ))
            .build();
        
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(error);
    }
    
    /**
     * Handle suspicious activity
     */
    public ResponseEntity<ErrorResponseDTO> handleSuspiciousActivity(String userId, String activity) {
        String traceId = generateTraceId();
        log.warn("🚨 [{}] Suspicious activity detected - User: {}, Activity: {}", traceId, userId, activity);
        
        ErrorResponseDTO error = ErrorResponseDTO.basic(400, "SUSPICIOUS_ACTIVITY",
            "Suspicious activity detected", 
            "The request contains potentially harmful content and has been blocked.")
            .toBuilder()
            .traceId(traceId)
            .path(getCurrentPath())
            .method(getCurrentMethod())
            .context(java.util.Map.of(
                "detectedActivity", activity,
                "securityAction", "Request blocked for security reasons"
            ))
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}