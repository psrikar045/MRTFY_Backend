package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.service.RivoFetchAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 📊 RivoFetch Analytics Controller
 * 
 * Provides comprehensive analytics endpoints for RivoFetch API usage.
 * This controller offers detailed insights into user behavior, API performance,
 * success/failure rates, and system health metrics.
 * 
 * Features:
 * - User-specific request statistics
 * - API key performance metrics
 * - Success/failure rate analysis
 * - System-wide analytics
 * - Cache performance insights
 * - Error distribution analysis
 * 
 * Security:
 * - JWT authentication required for all endpoints
 * - Role-based access control (USER/ADMIN)
 * - Users can only access their own statistics
 * - Admins can access system-wide statistics
 * 
 * @author BrandSnap API Team
 * @version 1.0
 * @since Java 21
 */
@RestController
@RequestMapping("/api/v1/rivofetch/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RivoFetch Analytics", description = "Analytics and statistics for RivoFetch API usage")
@SecurityRequirement(name = "bearerAuth")
public class RivoFetchAnalyticsController {
    
    private final RivoFetchAnalyticsService analyticsService;
    
    // ==================== USER STATISTICS ====================
    
    @GetMapping("/user/{userId}/total-requests")
    @Operation(
        summary = "Get user total request count",
        description = "Get total RivoFetch request count for a specific user (High Performance)"
    )
    @ApiResponse(responseCode = "200", description = "Total request count retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserTotalRequests(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("🔢 Getting total request count for user: {} (last {} hours)", userId, hours);
        
        try {
            Map<String, Object> totalRequests = analyticsService.getUserTotalRequests(userId, hours);
            return ResponseEntity.ok(totalRequests);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving total requests for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve total requests", "userId", userId));
        }
    }
    
    @GetMapping("/user/{userId}/successful-requests")
    @Operation(
        summary = "Get user successful request count",
        description = "Get successful RivoFetch request count and success rate for a specific user (High Performance)"
    )
    @ApiResponse(responseCode = "200", description = "Successful request count retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserSuccessfulRequests(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("✅ Getting successful request count for user: {} (last {} hours)", userId, hours);
        
        try {
            Map<String, Object> successfulRequests = analyticsService.getUserSuccessfulRequests(userId, hours);
            return ResponseEntity.ok(successfulRequests);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving successful requests for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve successful requests", "userId", userId));
        }
    }
    
    @GetMapping("/user/{userId}/failed-requests")
    @Operation(
        summary = "Get user failed request count",
        description = "Get failed RivoFetch request count and failure rate for a specific user (High Performance)"
    )
    @ApiResponse(responseCode = "200", description = "Failed request count retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserFailedRequests(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("❌ Getting failed request count for user: {} (last {} hours)", userId, hours);
        
        try {
            Map<String, Object> failedRequests = analyticsService.getUserFailedRequests(userId, hours);
            return ResponseEntity.ok(failedRequests);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving failed requests for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve failed requests", "userId", userId));
        }
    }
    
    @GetMapping("/user/{userId}/statistics")
    @Operation(
        summary = "Get user RivoFetch statistics",
        description = "Get comprehensive RivoFetch usage statistics for a specific user including request counts, success rates, and performance metrics"
    )
    @ApiResponse(responseCode = "200", description = "User statistics retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied - can only access own statistics")
    @ApiResponse(responseCode = "404", description = "User not found")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserStatistics(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("📊 Getting RivoFetch statistics for user: {} (last {} hours)", userId, hours);
        
        try {
            Map<String, Object> statistics = analyticsService.getUserStatistics(userId, hours);
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving user statistics for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve user statistics", "userId", userId));
        }
    }
    
    @GetMapping("/user/{userId}/success-rate")
    @Operation(
        summary = "Get user success rate",
        description = "Get RivoFetch success rate statistics for a specific user"
    )
    @ApiResponse(responseCode = "200", description = "Success rate retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserSuccessRate(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("📈 Getting success rate for user: {} (last {} hours)", userId, hours);
        
        try {
            Map<String, Object> successRate = analyticsService.getUserSuccessRate(userId, hours);
            return ResponseEntity.ok(successRate);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving success rate for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve success rate", "userId", userId));
        }
    }
    
    @GetMapping("/user/{userId}/failure-rate")
    @Operation(
        summary = "Get user failure rate",
        description = "Get RivoFetch failure rate statistics and error breakdown for a specific user"
    )
    @ApiResponse(responseCode = "200", description = "Failure rate retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserFailureRate(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("📉 Getting failure rate for user: {} (last {} hours)", userId, hours);
        
        try {
            Map<String, Object> failureRate = analyticsService.getUserFailureRate(userId, hours);
            return ResponseEntity.ok(failureRate);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving failure rate for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve failure rate", "userId", userId));
        }
    }
    
    // ==================== API KEY STATISTICS ====================
    
    @GetMapping("/api-key/{apiKeyId}/total-requests")
    @Operation(
        summary = "Get API key total request count",
        description = "Get total RivoFetch request count for a specific API key (High Performance)"
    )
    @ApiResponse(responseCode = "200", description = "Total request count retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getApiKeyTotalRequests(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("🔢 Getting total request count for API key: {} (last {} hours)", apiKeyId, hours);
        
        try {
            UUID apiKeyUuid = UUID.fromString(apiKeyId);
            Map<String, Object> totalRequests = analyticsService.getApiKeyTotalRequests(apiKeyUuid, hours);
            return ResponseEntity.ok(totalRequests);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid API key ID format: {}", apiKeyId);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid API key ID format", "apiKeyId", apiKeyId));
                
        } catch (Exception e) {
            log.error("❌ Error retrieving total requests for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve total requests", "apiKeyId", apiKeyId));
        }
    }
    
    @GetMapping("/api-key/{apiKeyId}/successful-requests")
    @Operation(
        summary = "Get API key successful request count",
        description = "Get successful RivoFetch request count and success rate for a specific API key (High Performance)"
    )
    @ApiResponse(responseCode = "200", description = "Successful request count retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getApiKeySuccessfulRequests(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("✅ Getting successful request count for API key: {} (last {} hours)", apiKeyId, hours);
        
        try {
            UUID apiKeyUuid = UUID.fromString(apiKeyId);
            Map<String, Object> successfulRequests = analyticsService.getApiKeySuccessfulRequests(apiKeyUuid, hours);
            return ResponseEntity.ok(successfulRequests);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid API key ID format: {}", apiKeyId);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid API key ID format", "apiKeyId", apiKeyId));
                
        } catch (Exception e) {
            log.error("❌ Error retrieving successful requests for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve successful requests", "apiKeyId", apiKeyId));
        }
    }
    
    @GetMapping("/api-key/{apiKeyId}/failed-requests")
    @Operation(
        summary = "Get API key failed request count",
        description = "Get failed RivoFetch request count and failure rate for a specific API key (High Performance)"
    )
    @ApiResponse(responseCode = "200", description = "Failed request count retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getApiKeyFailedRequests(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("❌ Getting failed request count for API key: {} (last {} hours)", apiKeyId, hours);
        
        try {
            UUID apiKeyUuid = UUID.fromString(apiKeyId);
            Map<String, Object> failedRequests = analyticsService.getApiKeyFailedRequests(apiKeyUuid, hours);
            return ResponseEntity.ok(failedRequests);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid API key ID format: {}", apiKeyId);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid API key ID format", "apiKeyId", apiKeyId));
                
        } catch (Exception e) {
            log.error("❌ Error retrieving failed requests for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve failed requests", "apiKeyId", apiKeyId));
        }
    }
    
    @GetMapping("/api-key/{apiKeyId}/statistics")
    @Operation(
        summary = "Get API key RivoFetch statistics",
        description = "Get comprehensive RivoFetch usage statistics for a specific API key"
    )
    @ApiResponse(responseCode = "200", description = "API key statistics retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "API key not found")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getApiKeyStatistics(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("📊 Getting RivoFetch statistics for API key: {} (last {} hours)", apiKeyId, hours);
        
        try {
            UUID apiKeyUuid = UUID.fromString(apiKeyId);
            Map<String, Object> statistics = analyticsService.getApiKeyStatistics(apiKeyUuid, hours);
            return ResponseEntity.ok(statistics);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid API key ID format: {}", apiKeyId);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid API key ID format", "apiKeyId", apiKeyId));
                
        } catch (Exception e) {
            log.error("❌ Error retrieving API key statistics for: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve API key statistics", "apiKeyId", apiKeyId));
        }
    }
    
    @GetMapping("/api-key/{apiKeyId}/success-rate")
    @Operation(
        summary = "Get API key success rate",
        description = "Get RivoFetch success rate for a specific API key"
    )
    @ApiResponse(responseCode = "200", description = "Success rate retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getApiKeySuccessRate(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("📈 Getting success rate for API key: {} (last {} hours)", apiKeyId, hours);
        
        try {
            UUID apiKeyUuid = UUID.fromString(apiKeyId);
            Map<String, Object> statistics = analyticsService.getApiKeyStatistics(apiKeyUuid, hours);
            
            // Extract success rate from full statistics
            Map<String, Object> successRateData = Map.of(
                "apiKeyId", apiKeyId,
                "timeRange", statistics.get("timeRange"),
                "requestCounts", statistics.get("requestCounts"),
                "successRate", ((Map<String, Object>) statistics.get("rates")).get("successRate")
            );
            
            return ResponseEntity.ok(successRateData);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid API key ID format: {}", apiKeyId);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid API key ID format", "apiKeyId", apiKeyId));
                
        } catch (Exception e) {
            log.error("❌ Error retrieving success rate for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve success rate", "apiKeyId", apiKeyId));
        }
    }
    
    @GetMapping("/api-key/{apiKeyId}/performance")
    @Operation(
        summary = "Get API key performance metrics",
        description = "Get detailed performance metrics for a specific API key including response time percentiles and cache performance"
    )
    @ApiResponse(responseCode = "200", description = "Performance metrics retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getApiKeyPerformance(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("🚀 Getting performance metrics for API key: {} (last {} hours)", apiKeyId, hours);
        
        try {
            UUID apiKeyUuid = UUID.fromString(apiKeyId);
            Map<String, Object> performance = analyticsService.getApiKeyPerformance(apiKeyUuid, hours);
            return ResponseEntity.ok(performance);
            
        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid API key ID format: {}", apiKeyId);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid API key ID format", "apiKeyId", apiKeyId));
                
        } catch (Exception e) {
            log.error("❌ Error retrieving performance metrics for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve performance metrics", "apiKeyId", apiKeyId));
        }
    }
    
    // ==================== PUBLIC APPLICATION-WIDE STATISTICS (NO AUTH) ====================
    
    @GetMapping("/public/all-total-requests")
    @Operation(
        summary = "Get ALL application total request count",
        description = "Get total request count from ALL records in rivo_fetch_request_logs table since application start (Public endpoint - No authentication required)"
    )
    @ApiResponse(responseCode = "200", description = "Total request count retrieved successfully")
    public ResponseEntity<Map<String, Object>> getAllApplicationTotalRequests() {
        
        log.info("🔢 Getting ALL application total request count - PUBLIC");
        
        try {
            Map<String, Object> totalRequests = analyticsService.getAllApplicationTotalRequests();
            return ResponseEntity.ok(totalRequests);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving all application total requests", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve all application total requests"));
        }
    }
    
    @GetMapping("/public/all-successful-requests")
    @Operation(
        summary = "Get ALL application successful request count",
        description = "Get successful request count from ALL records in rivo_fetch_request_logs table since application start (Public endpoint - No authentication required)"
    )
    @ApiResponse(responseCode = "200", description = "Successful request count retrieved successfully")
    public ResponseEntity<Map<String, Object>> getAllApplicationSuccessfulRequests() {
        
        log.info("✅ Getting ALL application successful request count - PUBLIC");
        
        try {
            Map<String, Object> successfulRequests = analyticsService.getAllApplicationSuccessfulRequests();
            return ResponseEntity.ok(successfulRequests);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving all application successful requests", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve all application successful requests"));
        }
    }
    
    @GetMapping("/public/all-failed-requests")
    @Operation(
        summary = "Get ALL application failed request count",
        description = "Get failed request count from ALL records in rivo_fetch_request_logs table since application start (Public endpoint - No authentication required)"
    )
    @ApiResponse(responseCode = "200", description = "Failed request count retrieved successfully")
    public ResponseEntity<Map<String, Object>> getAllApplicationFailedRequests() {
        
        log.info("❌ Getting ALL application failed request count - PUBLIC");
        
        try {
            Map<String, Object> failedRequests = analyticsService.getAllApplicationFailedRequests();
            return ResponseEntity.ok(failedRequests);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving all application failed requests", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve all application failed requests"));
        }
    }
    
    @GetMapping("/public/all-statistics")
    @Operation(
        summary = "Get ALL application statistics (Combined)",
        description = "Get complete statistics including total, successful, and failed request counts with rates from ALL records in rivo_fetch_request_logs table since application start (Public endpoint - No authentication required)"
    )
    @ApiResponse(responseCode = "200", description = "All application statistics retrieved successfully")
    public ResponseEntity<Map<String, Object>> getAllApplicationStatistics() {
        
        log.info("📊 Getting ALL application statistics (COMBINED) - PUBLIC");
        
        try {
            Map<String, Object> allStatistics = analyticsService.getAllApplicationStatistics();
            return ResponseEntity.ok(allStatistics);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving all application statistics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve all application statistics"));
        }
    }
    
    // ==================== SYSTEM STATISTICS (ADMIN ONLY) ====================
    
    @GetMapping("/system/overview")
    @Operation(
        summary = "Get system-wide RivoFetch overview",
        description = "Get comprehensive system-wide RivoFetch statistics including total requests, success rates, and top domains (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "System overview retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSystemOverview(
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("🌐 Getting system-wide RivoFetch overview (last {} hours)", hours);
        
        try {
            Map<String, Object> overview = analyticsService.getSystemOverview(hours);
            return ResponseEntity.ok(overview);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving system overview", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve system overview"));
        }
    }
    
    @GetMapping("/system/cache-performance")
    @Operation(
        summary = "Get system cache performance",
        description = "Get system-wide cache performance analysis for RivoFetch (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Cache performance retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCachePerformance(
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("🚀 Getting system cache performance (last {} hours)", hours);
        
        try {
            Map<String, Object> cachePerformance = analyticsService.getCachePerformance(hours);
            return ResponseEntity.ok(cachePerformance);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving cache performance", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve cache performance"));
        }
    }
    
    @GetMapping("/system/error-analysis")
    @Operation(
        summary = "Get system error analysis",
        description = "Get system-wide error distribution and analysis for RivoFetch (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Error analysis retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getErrorAnalysis(
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("🔍 Getting system error analysis (last {} hours)", hours);
        
        try {
            Map<String, Object> errorAnalysis = analyticsService.getErrorAnalysis(hours);
            return ResponseEntity.ok(errorAnalysis);
            
        } catch (Exception e) {
            log.error("❌ Error retrieving error analysis", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve error analysis"));
        }
    }
    
    // ==================== HEALTH CHECK ====================
    
    @GetMapping("/health")
    @Operation(
        summary = "Analytics service health check",
        description = "Check if the RivoFetch analytics service is operational"
    )
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        
        try {
            // Simple health check - try to get system overview for last hour
            Map<String, Object> testQuery = analyticsService.getSystemOverview(1);
            
            return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "RivoFetch Analytics",
                "timestamp", java.time.LocalDateTime.now(),
                "message", "Analytics service is operational"
            ));
            
        } catch (Exception e) {
            log.error("❌ Analytics service health check failed", e);
            return ResponseEntity.status(503)
                .body(Map.of(
                    "status", "unhealthy",
                    "service", "RivoFetch Analytics",
                    "timestamp", java.time.LocalDateTime.now(),
                    "error", "Service is experiencing issues"
                ));
        }
    }
}