package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.entity.ApiKeyRequestLog;
import com.example.jwtauthenticator.service.ApiKeyRequestLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for API key analytics and request logging.
 * Provides comprehensive insights into API key usage patterns.
 */
@RestController
@RequestMapping("/api/v1/api-keys/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "API Key Analytics", description = "Analytics and monitoring for API key usage")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyAnalyticsController {

    private final ApiKeyRequestLogService requestLogService;

    @GetMapping("/{apiKeyId}/logs")
    @Operation(
        summary = "Get request logs for an API key",
        description = "Retrieve paginated request logs for a specific API key with detailed information about each request"
    )
    @ApiResponse(responseCode = "200", description = "Request logs retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "API key not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getRequestLogs(
            @Parameter(description = "API key ID") @PathVariable UUID apiKeyId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ApiKeyRequestLog> logs = requestLogService.getRequestLogs(apiKeyId, pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("logs", logs.getContent());
            response.put("totalElements", logs.getTotalElements());
            response.put("totalPages", logs.getTotalPages());
            response.put("currentPage", logs.getNumber());
            response.put("pageSize", logs.getSize());
            response.put("hasNext", logs.hasNext());
            response.put("hasPrevious", logs.hasPrevious());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving request logs for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve request logs"));
        }
    }

    @GetMapping("/{apiKeyId}/security-violations")
    @Operation(
        summary = "Get security violations for an API key",
        description = "Retrieve requests that violated IP or domain restrictions"
    )
    @ApiResponse(responseCode = "200", description = "Security violations retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getSecurityViolations(
            @Parameter(description = "API key ID") @PathVariable UUID apiKeyId) {
        
        try {
            List<ApiKeyRequestLog> violations = requestLogService.getSecurityViolations(apiKeyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("violations", violations);
            response.put("totalViolations", violations.size());
            response.put("retrievedAt", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving security violations for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve security violations"));
        }
    }

    @GetMapping("/{apiKeyId}/statistics")
    @Operation(
        summary = "Get usage statistics for an API key",
        description = "Retrieve comprehensive usage statistics including request counts, top IPs, and domains"
    )
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getUsageStatistics(
            @Parameter(description = "API key ID") @PathVariable UUID apiKeyId,
            @Parameter(description = "Hours to look back") @RequestParam(defaultValue = "24") int hours) {
        
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            
            // Get basic statistics
            long requestCount = requestLogService.getRequestCount(apiKeyId, since);
            List<Object[]> topIps = requestLogService.getTopClientIps(apiKeyId, 10);
            List<Object[]> topDomains = requestLogService.getTopDomains(apiKeyId, 10);
            List<ApiKeyRequestLog> violations = requestLogService.getSecurityViolations(apiKeyId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ));
            response.put("requestCount", requestCount);
            response.put("securityViolations", violations.size());
            
            // Format top IPs
            response.put("topClientIps", topIps.stream()
                    .map(row -> Map.of("ip", row[0], "requestCount", row[1]))
                    .toList());
            
            // Format top domains
            response.put("topDomains", topDomains.stream()
                    .map(row -> Map.of("domain", row[0], "requestCount", row[1]))
                    .toList());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving statistics for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve statistics"));
        }
    }

    @GetMapping("/{apiKeyId}/geographic-distribution")
    @Operation(
        summary = "Get geographic distribution of requests",
        description = "Retrieve geographic distribution of requests for an API key"
    )
    @ApiResponse(responseCode = "200", description = "Geographic distribution retrieved successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> getGeographicDistribution(
            @Parameter(description = "API key ID") @PathVariable UUID apiKeyId,
            @Parameter(description = "Maximum results") @RequestParam(defaultValue = "20") int limit) {
        
        try {
            // This would be implemented when GeoIP integration is added
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Geographic distribution feature will be available when GeoIP integration is implemented");
            response.put("apiKeyId", apiKeyId);
            response.put("limit", limit);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving geographic distribution for API key: {}", apiKeyId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve geographic distribution"));
        }
    }

    @PostMapping("/cleanup")
    @Operation(
        summary = "Clean up old request logs",
        description = "Remove request logs older than specified number of days (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Cleanup completed successfully")
    @ApiResponse(responseCode = "403", description = "Admin access required")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupOldLogs(
            @Parameter(description = "Days to keep") @RequestParam(defaultValue = "90") int daysToKeep) {
        
        try {
            requestLogService.cleanupOldLogs(daysToKeep);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Cleanup completed successfully");
            response.put("daysKept", daysToKeep);
            response.put("cleanupTime", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during log cleanup", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to cleanup logs"));
        }
    }

    @GetMapping("/ip/{clientIp}/logs")
    @Operation(
        summary = "Get logs by client IP",
        description = "Retrieve request logs for a specific client IP address (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "IP logs retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Admin access required")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getLogsByClientIp(
            @Parameter(description = "Client IP address") @PathVariable String clientIp,
            @Parameter(description = "Maximum results") @RequestParam(defaultValue = "50") int limit) {
        
        try {
            // This would require additional repository method
            Map<String, Object> response = new HashMap<>();
            response.put("message", "IP-based log retrieval feature");
            response.put("clientIp", clientIp);
            response.put("limit", limit);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving logs for IP: {}", clientIp, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve IP logs"));
        }
    }

    @GetMapping("/domain/{domain}/logs")
    @Operation(
        summary = "Get logs by domain",
        description = "Retrieve request logs for a specific domain (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Domain logs retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Admin access required")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getLogsByDomain(
            @Parameter(description = "Domain name") @PathVariable String domain,
            @Parameter(description = "Maximum results") @RequestParam(defaultValue = "50") int limit) {
        
        try {
            // This would require additional repository method
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Domain-based log retrieval feature");
            response.put("domain", domain);
            response.put("limit", limit);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving logs for domain: {}", domain, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve domain logs"));
        }
    }
}