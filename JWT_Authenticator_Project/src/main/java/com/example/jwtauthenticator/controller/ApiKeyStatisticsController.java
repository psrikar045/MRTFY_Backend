package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ApiKeyStatisticsDTO;
import com.example.jwtauthenticator.dto.SystemStatisticsDTO;
import com.example.jwtauthenticator.dto.UsageAnalyticsDTO;
import com.example.jwtauthenticator.service.ApiKeyStatisticsService;
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

/**
 * Controller for API key usage statistics and analytics
 * Provides comprehensive insights into API key usage patterns
 */
@RestController
@RequestMapping("/api/v1/api-keys/statistics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "API Key Statistics", description = "API key usage statistics and analytics")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyStatisticsController {

    private final ApiKeyStatisticsService statisticsService;

    @GetMapping("/{apiKeyId}")
    @Operation(
        summary = "Get API key statistics",
        description = "Get comprehensive usage statistics for a specific API key"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @ApiResponse(responseCode = "404", description = "API key not found")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiKeyStatisticsDTO> getApiKeyStatistics(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Getting statistics for API key: {} (last {} hours)", apiKeyId, hours);
        ApiKeyStatisticsDTO statistics = statisticsService.getApiKeyStatistics(apiKeyId, hours);
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/{apiKeyId}/analytics")
    @Operation(
        summary = "Get API key usage analytics",
        description = "Get advanced usage analytics with trends and patterns"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<UsageAnalyticsDTO> getUsageAnalytics(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Number of days to analyze") @RequestParam(defaultValue = "7") int days) {
        
        log.info("Getting usage analytics for API key: {} (last {} days)", apiKeyId, days);
        UsageAnalyticsDTO analytics = statisticsService.getUsageAnalytics(apiKeyId, days);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/system")
    @Operation(
        summary = "Get system-wide statistics",
        description = "Get system-wide API key usage statistics (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "System statistics retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemStatisticsDTO> getSystemStatistics(
            @Parameter(description = "Number of hours to analyze") @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Getting system-wide statistics (last {} hours)", hours);
        SystemStatisticsDTO statistics = statisticsService.getSystemStatistics(hours);
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/realtime")
    @Operation(
        summary = "Get real-time statistics",
        description = "Get real-time usage statistics for monitoring dashboards (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Real-time statistics retrieved successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRealTimeStatistics() {
        log.info("Getting real-time statistics");
        Map<String, Object> stats = statisticsService.getRealTimeStats();
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/cleanup")
    @Operation(
        summary = "Cleanup old statistics",
        description = "Clean up old usage statistics (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Cleanup completed successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> cleanupOldStatistics(
            @Parameter(description = "Number of days to keep") @RequestParam(defaultValue = "90") int daysToKeep) {
        
        log.info("Cleaning up statistics older than {} days", daysToKeep);
        statisticsService.cleanupOldStatistics(daysToKeep);
        
        return ResponseEntity.ok(Map.of(
            "message", "Statistics cleanup completed",
            "daysKept", String.valueOf(daysToKeep)
        ));
    }
}