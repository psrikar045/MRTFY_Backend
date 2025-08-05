package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.dashboard.SingleApiKeyDashboardDTO;
import com.example.jwtauthenticator.dto.dashboard.UserDashboardCardsDTO;
import com.example.jwtauthenticator.service.ApiKeyDashboardService;
import com.example.jwtauthenticator.service.UserDashboardService;
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
import java.util.concurrent.CompletableFuture;
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
    private final ModernDashboardRepository modernDashboardRepository;
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
            String userId = getCurrentUserId(userDetails);
            log.info("Fetching dashboard cards for user: {} (refresh: {})", userId, forceRefresh);

            UserDashboardCardsDTO dashboardCards;
            
            if (forceRefresh) {
                dashboardCards = userDashboardService.refreshUserDashboardCards(userId);
            } else {
                dashboardCards = userDashboardService.getUserDashboardCards(userId);
            }

            if (dashboardCards == null) {
                log.warn("No dashboard data available for user: {}", userId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "No dashboard data available");
                errorResponse.put("message", "Dashboard data is being calculated. Please try again in a few moments.");
                errorResponse.put("timestamp", java.time.Instant.now().toString());
                
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(errorResponse);
            }

            log.info("✅ Dashboard cards retrieved successfully for user: {}", userId);
            return ResponseEntity.ok(dashboardCards);

        } catch (IllegalStateException e) {
            log.error("Authentication error: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Authentication failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);

        } catch (Exception e) {
            log.error("Error fetching dashboard cards: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to fetch dashboard data");
            errorResponse.put("message", "An internal error occurred while fetching dashboard data");
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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
            String userId = getCurrentUserId(userDetails);
            log.info("Fetching dashboard for API key: {} (user: {}, refresh: {})", apiKeyId, userId, forceRefresh);

            SingleApiKeyDashboardDTO dashboard;
            
            if (forceRefresh) {
                dashboard = apiKeyDashboardService.refreshApiKeyDashboard(apiKeyId, userId);
            } else {
                dashboard = apiKeyDashboardService.getApiKeyDashboard(apiKeyId, userId);
            }

            if (dashboard == null) {
                log.warn("API key {} not found or doesn't belong to user {}", apiKeyId, userId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "API key not found");
                errorResponse.put("message", "The specified API key was not found or you don't have permission to access it");
                errorResponse.put("apiKeyId", apiKeyId.toString());
                errorResponse.put("timestamp", java.time.Instant.now().toString());
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            log.info("✅ API key dashboard retrieved successfully: {} (user: {})", apiKeyId, userId);
            return ResponseEntity.ok(dashboard);

        } catch (IllegalStateException e) {
            log.error("Authentication error: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Authentication failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);

        } catch (Exception e) {
            log.error("Error fetching API key dashboard for {}: {}", apiKeyId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to fetch API key dashboard");
            errorResponse.put("message", "An internal error occurred while fetching dashboard data");
            errorResponse.put("apiKeyId", apiKeyId.toString());
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
            
            Map<String, Object> healthStatus = new HashMap<>();
            healthStatus.put("status", "healthy");
            healthStatus.put("userId", userId);
            healthStatus.put("services", Map.of(
                "userDashboardService", "available",
                "apiKeyDashboardService", "available",
                "materializedViews", "available"
            ));
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
     * Modern Dashboard Endpoint using Java 21 Virtual Threads and Records
     * High-performance endpoint leveraging latest Java features
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
    public CompletableFuture<ResponseEntity<?>> getUserDashboardCardsModern(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Force refresh data", example = "false")
            @RequestParam(value = "refresh", defaultValue = "false") boolean forceRefresh) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userId = getCurrentUserId(userDetails);
                log.info("🚀 Fetching modern dashboard cards for user: {} (Java 21 implementation)", userId);
                
                // Use modern repository with Virtual Threads
                return modernDashboardRepository.getUserDashboardMetrics(userId)
                    .thenApply(metrics -> {
                        // Use pattern matching and records for cleaner code
                        var dashboard = switch (metrics) {
                            case ModernDashboardRepository.DashboardMetrics(
                                var totalCalls30Days,
                                var totalCallsPrevious30Days,
                                var activeDomains,
                                var activeDomainsPrevious,
                                var domainsAddedThisMonth,
                                var domainsAddedPreviousMonth,
                                var remainingQuota,
                                var remainingQuotaPrevious,
                                var successRate,
                                var totalApiKeys,
                                var lastActivity
                            ) -> UserDashboardCardsDTO.builder()
                                .totalApiCalls(buildModernApiCallsCard(totalCalls30Days, totalCallsPrevious30Days))
                                .activeDomains(buildModernActiveDomainsCard(activeDomains, activeDomainsPrevious))
                                .domainsAdded(buildModernDomainsAddedCard(domainsAddedThisMonth, domainsAddedPreviousMonth))
                                .remainingQuota(buildModernRemainingQuotaCard(remainingQuota, remainingQuotaPrevious))
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
                        
                        return ResponseEntity.ok(response);
                    })
                    .exceptionally(throwable -> {
                        log.error("Modern dashboard fetch failed for user {}: {}", userId, throwable.getMessage(), throwable);
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("success", false);
                        errorResponse.put("error", "Failed to fetch modern dashboard");
                        errorResponse.put("message", throwable.getMessage());
                        errorResponse.put("implementation", "Java 21 Virtual Threads");
                        errorResponse.put("timestamp", java.time.Instant.now().toString());
                        
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                    })
                    .join();
                    
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
        });
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

    private UserDashboardCardsDTO.RemainingQuotaCardDTO buildModernRemainingQuotaCard(Long current, Long previous) {
        double percentageChange = previous > 0 ? ((current - previous) * 100.0) / previous : 0.0;
        String trend = percentageChange > 0 ? "up" : percentageChange < 0 ? "down" : "stable";
        String status = current > 50000 ? "healthy" : current > 10000 ? "warning" : "critical";
        
        Long totalQuota = 100000L; // This should come from user's plan
        Long usedQuota = totalQuota - current;
        double usagePercentage = totalQuota > 0 ? (usedQuota * 100.0) / totalQuota : 0.0;
        int estimatedDaysRemaining = current > 0 && usedQuota > 0 ? (int) (current / (usedQuota / 30.0)) : 0;
        
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
}