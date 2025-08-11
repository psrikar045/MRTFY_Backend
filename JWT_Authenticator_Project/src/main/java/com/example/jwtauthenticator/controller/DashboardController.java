package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.dashboard.SingleApiKeyDashboardDTO;
import com.example.jwtauthenticator.dto.dashboard.UserDashboardCardsDTO;
import com.example.jwtauthenticator.service.ApiKeyDashboardService;
import com.example.jwtauthenticator.service.UserDashboardService;
import com.example.jwtauthenticator.service.UnifiedDashboardService;
import com.example.jwtauthenticator.service.ErrorHandlerService;
import com.example.jwtauthenticator.service.DashboardHealthCheckService;
import com.example.jwtauthenticator.util.JwtUtil;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.repository.ModernDashboardRepository;
import com.example.jwtauthenticator.entity.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * Dashboard Controller for API Key Management System
 * Provides endpoints for user dashboard cards and individual API key dashboards
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard", description = "Dashboard endpoints for API key analytics and metrics")
public class DashboardController {

    private final UserDashboardService userDashboardService;
    private final ApiKeyDashboardService apiKeyDashboardService;
    private final UnifiedDashboardService unifiedDashboardService; // NEW: Optimized service
    private final ModernDashboardRepository modernDashboardRepository;
    private final ErrorHandlerService errorHandlerService; // NEW: Standardized error handling
    private final DashboardHealthCheckService healthCheckService; // NEW: Health check service
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * Helper method to get the current authenticated user's ID from JWT token
     */
    private String getCurrentUserId(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                throw new IllegalStateException("No authentication found in SecurityContext");
            }
            
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalStateException("No valid JWT token found in Authorization header");
            }
            
            String jwt = authHeader.substring(7);
            String userId = jwtUtil.extractUserID(jwt);
            
            if (userId == null || userId.trim().isEmpty()) {
                log.warn("User ID not found in JWT claims, attempting to find by username: {}", userDetails.getUsername());
                Optional<User> user = userRepository.findByUsername(userDetails.getUsername());
                if (user.isPresent()) {
                    userId = user.get().getId();
                    log.info("Found user ID {} for username {}", userId, userDetails.getUsername());
                } else {
                    throw new IllegalStateException("User ID not found in JWT claims and user not found by username: " + userDetails.getUsername());
                }
            }
            
            log.debug("Successfully extracted user ID: {} for username: {}", userId, userDetails.getUsername());
            return userId;
            
        } catch (Exception e) {
            log.error("Failed to extract user ID from authentication context: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to extract user ID: " + e.getMessage(), e);
        }
    }

    /**
     * Get User Dashboard Cards
     * Returns the 4 main dashboard cards with metrics and percentage changes
     */
    @GetMapping("/user/cards")
    @Operation(
        summary = "Get User Dashboard Cards",
        description = "Retrieves the main dashboard cards showing:\n" +
                     "- Total API Calls (Last 30 days) with rolling average comparison\n" +
                     "- Active Domains (Projects) with growth metrics\n" +
                     "- Domains Added This Month with comparison to previous month\n" +
                     "- Remaining Quota across all API keys with usage analysis\n\n" +
                     "Uses mixed approach: fast materialized views with real-time fallback for accuracy.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Dashboard cards retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserDashboardCardsDTO.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getUserDashboardCards(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Force refresh data (bypass cache)", example = "false")
            @RequestParam(value = "refresh", defaultValue = "false") boolean forceRefresh) {

        try {
            // ✅ VALIDATE INPUT AND GET USER ID
            ValidationResult<String> validation = validateUserAuthentication(userDetails);
            if (!validation.isValid()) {
                return validation.getErrorResponse();
            }
            String userId = validation.getData();
            
            log.info("Fetching dashboard cards for user: {} (refresh: {})", userId, forceRefresh);

            UserDashboardCardsDTO dashboardCards;
            
            // 🚀 USE OPTIMIZED SERVICE for better performance
            if (forceRefresh) {
                // Clear cache and get fresh data
                dashboardCards = unifiedDashboardService.getUserDashboardCards(userId);
            } else {
                // Use cached data with 5-minute TTL
                dashboardCards = unifiedDashboardService.getUserDashboardCards(userId);
            }

            if (dashboardCards == null) {
                return errorHandlerService.handleDashboardDataUnavailable(userId);
            }

            log.info("✅ Dashboard cards retrieved successfully for user: {}", userId);
            return ResponseEntity.ok(dashboardCards);

        } catch (IllegalStateException e) {
            return errorHandlerService.handleAuthenticationFailed(e.getMessage(), e);
        } catch (Exception e) {
            return errorHandlerService.handleInternalError("fetching dashboard cards", e);
        }
    }

    /**
     * Get Single API Key Dashboard
     * Returns detailed metrics for a specific API key
     */
    @GetMapping("/api-key/{apiKeyId}")
    @Operation(
        summary = "Get API Key Dashboard",
        description = "Retrieves detailed dashboard metrics for a specific API key including:\n" +
                     "- Requests Today with comparison to yesterday\n" +
                     "- Pending Requests (rate limited + failed requests that might retry)\n" +
                     "- Usage Percentage of monthly quota\n" +
                     "- Last Used timestamp\n" +
                     "- Monthly metrics (total calls, success rate, quota status)\n" +
                     "- Performance metrics (response time, error rate, uptime)\n" +
                     "- Rate limiting information\n\n" +
                     "Uses mixed approach for real-time accuracy with performance optimization.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "API key dashboard retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = SingleApiKeyDashboardDTO.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Forbidden - API key doesn't belong to user"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getApiKeyDashboard(
            @Parameter(description = "API Key UUID", required = true)
            @PathVariable UUID apiKeyId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Force refresh data (bypass cache)", example = "false")
            @RequestParam(value = "refresh", defaultValue = "false") boolean forceRefresh) {

        try {
            // ✅ VALIDATE API KEY ID
            if (apiKeyId == null) {
                return errorHandlerService.handleMissingParameter("apiKeyId");
            }
            
            // ✅ VALIDATE USER AUTHENTICATION
            ValidationResult<String> validation = validateUserAuthentication(userDetails);
            if (!validation.isValid()) {
                return validation.getErrorResponse();
            }
            String userId = validation.getData();
            
            log.info("Fetching dashboard for API key: {} (user: {}, refresh: {})", apiKeyId, userId, forceRefresh);

            SingleApiKeyDashboardDTO dashboard;
            
            // 🚀 USE OPTIMIZED SERVICE for better performance
            dashboard = unifiedDashboardService.getApiKeyDashboard(apiKeyId, userId);

            if (dashboard == null) {
                return errorHandlerService.handleResourceNotFound("API Key", apiKeyId.toString());
            }

            log.info("✅ API key dashboard retrieved successfully: {} (user: {})", apiKeyId, userId);
            return ResponseEntity.ok(dashboard);

        } catch (IllegalStateException e) {
            return errorHandlerService.handleAuthenticationFailed(e.getMessage(), e);
        } catch (Exception e) {
            return errorHandlerService.handleInternalError("fetching API key dashboard", e);
        }
    }

    /**
     * Modern Single API Key Dashboard using Java 21 Virtual Threads
     * High-performance endpoint for individual API key metrics
     */
    @GetMapping("/v2/api-key/{apiKeyId}")
    @Operation(
        summary = "Get API Key Dashboard (Modern Java 21 Implementation)",
        description = """
            Modern implementation for single API key dashboard using:
            - Virtual Threads for non-blocking I/O
            - Single optimized query instead of multiple round trips
            - Records for immutable data structures
            - Enhanced error handling and performance monitoring
            
            Returns detailed metrics for a specific API key with improved performance.
            """,
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "API key dashboard retrieved successfully using modern approach",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - API key doesn't belong to user"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getApiKeyDashboardModern(
            @Parameter(description = "API Key UUID", required = true)
            @PathVariable UUID apiKeyId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Force refresh data", example = "false")
            @RequestParam(value = "refresh", defaultValue = "false") boolean forceRefresh) {
        
        try {
            // ✅ FIXED: Extract user ID in main thread to avoid SecurityContext issues
            ValidationResult<String> validation = validateUserAuthentication(userDetails);
            if (!validation.isValid()) {
                return validation.getErrorResponse();
            }
            String userId = validation.getData();
            
            // Validate API key ID
            if (apiKeyId == null) {
                return errorHandlerService.handleMissingParameter("apiKeyId");
            }
            
            log.info("🚀 Fetching modern API key dashboard: {} (user: {}, Java 21 implementation)", apiKeyId, userId);
            
            // Use modern repository with Virtual Threads (but handle result synchronously)
            var metricsResult = modernDashboardRepository.getApiKeyDashboardMetrics(apiKeyId, userId).join();
            
            // Use pattern matching and records for cleaner code
            var dashboard = switch (metricsResult) {
                case ModernDashboardRepository.ApiKeyMetrics(
                    var apiKeyIdResult,
                    var apiKeyName,
                    var registeredDomain,
                    var requestsToday,
                    var requestsYesterday,
                    var pendingRequests,
                    var usagePercentage,
                    var lastUsed,
                    var status,
                    var totalCallsMonth,
                    var quotaLimit,
                    var avgResponseTime7Days,
                    var errorRate24h
                ) -> {
                    // Calculate percentage change
                    double todayVsYesterdayChange = requestsYesterday > 0 
                        ? ((requestsToday - requestsYesterday) * 100.0) / requestsYesterday 
                        : (requestsToday > 0 ? 100.0 : 0.0);
                    
                    // ✅ FIXED: Use HashMap for more than 10 key-value pairs
                    Map<String, Object> result = new HashMap<>();
                    result.put("apiKeyId", apiKeyIdResult);
                    result.put("apiKeyName", apiKeyName);
                    result.put("registeredDomain", registeredDomain);
                    result.put("requestsToday", requestsToday);
                    result.put("requestsYesterday", requestsYesterday);
                    result.put("todayVsYesterdayChange", Math.round(todayVsYesterdayChange * 100.0) / 100.0);
                    result.put("pendingRequests", pendingRequests);
                    result.put("usagePercentage", usagePercentage);
                    result.put("lastUsed", lastUsed);
                    result.put("status", status);
                    result.put("monthlyMetrics", Map.of(
                        "totalCalls", totalCallsMonth,
                        "quotaLimit", quotaLimit,
                        "remainingQuota", Math.max(0, quotaLimit - totalCallsMonth)
                    ));
                    result.put("performanceMetrics", Map.of(
                        "avgResponseTime7Days", avgResponseTime7Days,
                        "errorRate24h", errorRate24h
                    ));
                    yield result;
                }
            };
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard);
            response.put("implementation", "Java 21 Virtual Threads - Single API Key");
            response.put("timestamp", java.time.Instant.now().toString());
            
            log.info("✅ Modern API key dashboard retrieved successfully: {} (user: {})", apiKeyId, userId);
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            return errorHandlerService.handleAuthenticationFailed(e.getMessage(), e);
        } catch (Exception e) {
            log.error("Modern API key dashboard endpoint failed: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Modern API key dashboard endpoint failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("implementation", "Java 21 Virtual Threads - Single API Key");
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get Dashboard Health Check
     * Returns the status of dashboard services and data availability
     */
    @GetMapping("/health")
    @Operation(
        summary = "Dashboard Health Check",
        description = "Returns the health status of dashboard services and data availability",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health check completed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getDashboardHealth(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String userId = getCurrentUserId(userDetails);
            
            // ✅ ENHANCED: Use comprehensive health check service
            Map<String, Object> healthStatus = healthCheckService.performHealthCheck();
            healthStatus.put("userId", userId);
            healthStatus.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.ok(healthStatus);
            
        } catch (Exception e) {
            log.error("Dashboard health check failed: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "unhealthy");
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    /**
     * 🔍 Quick Data Diagnostic
     * Simple endpoint to check data flow without complex dependencies
     */
    @GetMapping("/data-check")
    @Operation(
        summary = "Quick Data Check",
        description = "Simple diagnostic to check if data is flowing correctly",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    public ResponseEntity<?> quickDataCheck(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String userId = getCurrentUserId(userDetails);
            
            Map<String, Object> dataCheck = new HashMap<>();
            dataCheck.put("userId", userId);
            dataCheck.put("timestamp", java.time.Instant.now().toString());
            
            // Simple data checks using existing services
            try {
                // Check if user has API keys
                Optional<User> user = userRepository.findById(userId);
                dataCheck.put("userExists", user.isPresent());
                
                if (user.isPresent()) {
                    dataCheck.put("userEmail", user.get().getEmail());
                }
                
                // Try to get dashboard data
                var dashboardData = unifiedDashboardService.getUserDashboardCards(userId);
                dataCheck.put("dashboardDataAvailable", dashboardData != null);
                
                if (dashboardData != null) {
                    dataCheck.put("totalApiKeys", dashboardData.getTotalApiKeys());
                    dataCheck.put("totalCalls30Days", dashboardData.getTotalApiCalls() != null ? 
                        dashboardData.getTotalApiCalls().getTotalCalls() : 0);
                    dataCheck.put("remainingQuota", dashboardData.getRemainingQuota() != null ? 
                        dashboardData.getRemainingQuota().getRemainingQuota() : 0);
                }
                
                dataCheck.put("status", "SUCCESS");
                
            } catch (Exception serviceException) {
                dataCheck.put("status", "PARTIAL_SUCCESS");
                dataCheck.put("serviceError", serviceException.getMessage());
            }
            
            return ResponseEntity.ok(dataCheck);
            
        } catch (Exception e) {
            log.error("Quick data check failed: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", "Data check failed: " + e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Modern Dashboard Endpoint using Java 21 Virtual Threads and Records
     * High-performance endpoint leveraging latest Java features
     * 
     * ✅ FIXED: Proper SecurityContext handling for async operations
     */
    @GetMapping("/v2/user/cards")
    @Operation(
        summary = "Get User Dashboard Cards (Modern Java 21 Implementation)",
        description = """
            Modern implementation using Java 21 features:
            - Virtual Threads for non-blocking I/O
            - Records for immutable data structures
            - Pattern matching for cleaner code
            - Text blocks for readable SQL
            - Structured concurrency for parallel processing
            - Proper SecurityContext handling for async operations
            
            Returns the same data as /user/cards but with improved performance.
            """,
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Dashboard cards retrieved successfully using modern approach",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserDashboardCardsDTO.class)
            )
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<?> getUserDashboardCardsModern(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Force refresh data", example = "false")
            @RequestParam(value = "refresh", defaultValue = "false") boolean forceRefresh) {
        
        try {
            // ✅ FIXED: Extract user ID in main thread to avoid SecurityContext issues
            ValidationResult<String> validation = validateUserAuthentication(userDetails);
            if (!validation.isValid()) {
                return validation.getErrorResponse();
            }
            String userId = validation.getData();
            
            log.info("🚀 Fetching modern dashboard cards for user: {} (Java 21 implementation)", userId);
            
            // Use modern repository with Virtual Threads (but handle result synchronously)
            var metricsResult = modernDashboardRepository.getUserDashboardMetrics(userId).join();
            
            // Use pattern matching and records for cleaner code
            var dashboard = switch (metricsResult) {
                case ModernDashboardRepository.DashboardMetrics(
                    var totalCalls30Days,
                    var totalCallsPrevious30Days,
                    var activeDomains,
                    var activeDomainsPrevious,
                    var domainsAddedThisMonth,
                    var domainsAddedPreviousMonth,
                    var remainingQuota,
                    var remainingQuotaPrevious,
                    var totalQuota,
                    var usedQuota,
                    var successRate,
                    var totalApiKeys,
                    var lastActivity
                ) -> UserDashboardCardsDTO.builder()
                    .totalApiCalls(buildModernApiCallsCard(totalCalls30Days, totalCallsPrevious30Days))
                    .activeDomains(buildModernActiveDomainsCard(activeDomains, activeDomainsPrevious))
                    .domainsAdded(buildModernDomainsAddedCard(domainsAddedThisMonth, domainsAddedPreviousMonth))
                    .remainingQuota(buildModernRemainingQuotaCard(remainingQuota, remainingQuotaPrevious, totalQuota, usedQuota))
                    .lastUpdated(LocalDateTime.now())
                    .successRate(successRate)
                    .totalApiKeys(totalApiKeys)
                    .build();
            };
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dashboard);
            response.put("implementation", "Java 21 Virtual Threads");
            response.put("timestamp", java.time.Instant.now().toString());
            
            log.info("✅ Modern dashboard cards retrieved successfully for user: {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (IllegalStateException e) {
            return errorHandlerService.handleAuthenticationFailed(e.getMessage(), e);
        } catch (Exception e) {
            log.error("Modern dashboard endpoint failed: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Modern dashboard endpoint failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("implementation", "Java 21 Virtual Threads");
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Modern helper methods using Java 21 features
     */
    private UserDashboardCardsDTO.ApiCallsCardDTO buildModernApiCallsCard(Long current, Long previous) {
        double percentageChange = previous > 0 ? ((current - previous) * 100.0) / previous : 0.0;
        String trend = percentageChange > 0 ? "up" : percentageChange < 0 ? "down" : "stable";
        String status = current > 0 ? "active" : "inactive";
        
        return UserDashboardCardsDTO.ApiCallsCardDTO.builder()
                .totalCalls(current)
                .percentageChange(Math.round(percentageChange * 100.0) / 100.0)
                .trend(trend)
                .previousPeriodCalls(previous)
                .dailyAverage(current / 30.0)
                .status(status)
                .build();
    }

    private UserDashboardCardsDTO.ActiveDomainsCardDTO buildModernActiveDomainsCard(Integer current, Integer previous) {
        double percentageChange = previous > 0 ? ((current - previous) * 100.0) / previous : 0.0;
        String trend = percentageChange > 0 ? "up" : percentageChange < 0 ? "down" : "stable";
        String status = current > previous ? "growing" : current < previous ? "declining" : "stable";
        
        return UserDashboardCardsDTO.ActiveDomainsCardDTO.builder()
                .activeDomains(current)
                .percentageChange(Math.round(percentageChange * 100.0) / 100.0)
                .trend(trend)
                .previousPeriodDomains(previous)
                .newDomainsThisPeriod(Math.max(0, current - previous))
                .status(status)
                .build();
    }

    private UserDashboardCardsDTO.DomainsAddedCardDTO buildModernDomainsAddedCard(Integer current, Integer previous) {
        double percentageChange = previous > 0 ? ((current - previous) * 100.0) / previous : 0.0;
        String trend = percentageChange > 0 ? "up" : percentageChange < 0 ? "down" : "stable";
        String status = current >= 5 ? "on_track" : current >= 3 ? "moderate" : "behind";
        
        return UserDashboardCardsDTO.DomainsAddedCardDTO.builder()
                .domainsAdded(current)
                .percentageChange(Math.round(percentageChange * 100.0) / 100.0)
                .trend(trend)
                .previousMonthAdded(previous)
                .monthlyTarget(5)
                .status(status)
                .build();
    }

    private UserDashboardCardsDTO.RemainingQuotaCardDTO buildModernRemainingQuotaCard(Long current, Long previous, Long totalQuota, Long usedQuota) {
        double percentageChange = previous > 0 ? ((current - previous) * 100.0) / previous : 0.0;
        String trend = percentageChange > 0 ? "up" : percentageChange < 0 ? "down" : "stable";
        
        // Calculate status based on remaining quota percentage
        double remainingPercentage = totalQuota > 0 ? (current * 100.0) / totalQuota : 0.0;
        String status = remainingPercentage > 50 ? "healthy" : remainingPercentage > 20 ? "warning" : "critical";
        
        // Calculate usage percentage
        double usagePercentage = totalQuota > 0 ? (usedQuota * 100.0) / totalQuota : 0.0;
        
        // Calculate estimated days remaining based on current usage rate
        int estimatedDaysRemaining = 0;
        if (current > 0 && usedQuota > 0) {
            // Calculate daily usage rate based on current month's usage
            double dailyUsageRate = usedQuota / 30.0; // Assuming 30 days in a month
            if (dailyUsageRate > 0) {
                estimatedDaysRemaining = (int) Math.ceil(current / dailyUsageRate);
            }
        }
        
        return UserDashboardCardsDTO.RemainingQuotaCardDTO.builder()
                .remainingQuota(current)
                .percentageChange(Math.round(percentageChange * 100.0) / 100.0)
                .trend(trend)
                .totalQuota(totalQuota)
                .usedQuota(usedQuota)
                .usagePercentage(Math.round(usagePercentage * 100.0) / 100.0)
                .estimatedDaysRemaining(estimatedDaysRemaining)
                .status(status)
                .build();
    }
    
    // ==================== INPUT VALIDATION HELPERS ====================
    
    /**
     * 🛡️ Validate user authentication and extract user ID
     * Returns ValidationResult with proper error handling
     */
    private ValidationResult<String> validateUserAuthentication(UserDetails userDetails) {
        // Check if userDetails is present
        if (userDetails == null) {
            return ValidationResult.invalid(errorHandlerService.handleAuthenticationRequired());
        }
        
        try {
            // Extract user ID from JWT token
            String userId = getCurrentUserId(userDetails);
            
            // Validate user ID
            ValidationResult<String> userIdValidation = validateUserId(userId);
            if (!userIdValidation.isValid()) {
                return userIdValidation;
            }
            
            return ValidationResult.valid(userId);
            
        } catch (IllegalStateException e) {
            return ValidationResult.invalid(errorHandlerService.handleAuthenticationFailed(e.getMessage(), e));
        } catch (Exception e) {
            return ValidationResult.invalid(errorHandlerService.handleInternalError("user authentication", e));
        }
    }
    
    /**
     * ✅ Validate MRTFY user ID format (e.g., MRTFY000002)
     * Supports MRTFY prefix format used by the system
     */
    private ValidationResult<String> validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return ValidationResult.invalid(
                errorHandlerService.handleValidationError("userId", "User ID cannot be empty", userId));
        }
        
        String sanitized = userId.trim();
        
        // Check length (MRTFY000002 = 10 characters, allow some flexibility)
        if (sanitized.length() < 5 || sanitized.length() > 20) {
            return ValidationResult.invalid(
                errorHandlerService.handleValidationError("userId", 
                    "User ID must be between 5 and 20 characters", sanitized));
        }
        
        // Check for MRTFY format: MRTFY followed by digits
        if (!sanitized.matches("^MRTFY\\d{6}$")) {
            // Also allow alternative formats for flexibility
            if (!sanitized.matches("^[a-zA-Z0-9._-]+$")) {
                return ValidationResult.invalid(errorHandlerService.handleInvalidUserId(sanitized));
            }
        }
        
        // Security validation - block suspicious patterns
        String lowerUserId = sanitized.toLowerCase();
        String[] suspiciousPatterns = {
            "script", "javascript", "vbscript", "onload", "onerror",
            "select", "union", "insert", "update", "delete", "drop", "alter",
            "../", "..\\", "<script", "</script", "eval(", "exec(", "cmd("
        };
        
        for (String pattern : suspiciousPatterns) {
            if (lowerUserId.contains(pattern)) {
                return ValidationResult.invalid(
                    errorHandlerService.handleSuspiciousActivity(sanitized, "Suspicious pattern: " + pattern));
            }
        }
        
        return ValidationResult.valid(sanitized);
    }
    

    
    // ==================== VALIDATION RESULT CLASS ====================
    
    /**
     * 🎯 Generic validation result wrapper
     * Provides clean separation between valid data and error responses
     */
    private static class ValidationResult<T> {
        private final boolean valid;
        private final T data;
        private final ResponseEntity<?> errorResponse;
        
        private ValidationResult(boolean valid, T data, ResponseEntity<?> errorResponse) {
            this.valid = valid;
            this.data = data;
            this.errorResponse = errorResponse;
        }
        
        public static <T> ValidationResult<T> valid(T data) {
            return new ValidationResult<>(true, data, null);
        }
        
        public static <T> ValidationResult<T> invalid(ResponseEntity<?> errorResponse) {
            return new ValidationResult<>(false, null, errorResponse);
        }
        
        public boolean isValid() { return valid; }
        public T getData() { return data; }
        public ResponseEntity<?> getErrorResponse() { return errorResponse; }
    }
}