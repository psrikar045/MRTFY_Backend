package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.enums.ApiKeyScope;
import com.example.jwtauthenticator.enums.RateLimitTier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for providing reference data and metadata about API key system
 */
@RestController
@RequestMapping("/api/v1/reference")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reference Data", description = "Endpoints for getting system metadata and reference information")
public class ReferenceDataController {

    @GetMapping("/scopes")
    @Operation(
            summary = "Get Available API Key Scopes",
            description = "Retrieve all available API key scopes with descriptions and categories. " +
                         "Useful for UI dropdowns and documentation.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scopes retrieved successfully",
                        content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required")
    })
    public ResponseEntity<Map<String, Object>> getAvailableScopes() {
        log.debug("Retrieving available API key scopes");

        List<Map<String, Object>> scopes = Arrays.stream(ApiKeyScope.values())
                .map(scope -> {
                    Map<String, Object> scopeInfo = new HashMap<>();
                    scopeInfo.put("name", scope.name());
                    scopeInfo.put("permission", scope.getPermission());
                    scopeInfo.put("description", scope.getDescription());
                    scopeInfo.put("category", getScopeCategory(scope));
                    return scopeInfo;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("scopes", scopes);
        response.put("totalCount", scopes.size());
        response.put("categories", getScopeCategories());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/rate-limit-tiers")
    @Operation(
            summary = "Get Available Rate Limit Tiers",
            description = "Retrieve all available rate limit tiers with their limits, pricing, and features. " +
                         "Perfect for pricing pages and tier selection UI.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rate limit tiers retrieved successfully",
                        content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required")
    })
    public ResponseEntity<Map<String, Object>> getRateLimitTiers() {
        log.debug("Retrieving available rate limit tiers");

        List<Map<String, Object>> tiers = Arrays.stream(RateLimitTier.values())
                .map(tier -> {
                    Map<String, Object> tierInfo = new HashMap<>();
                    tierInfo.put("name", tier.name());
                    tierInfo.put("displayName", tier.getDisplayName());
                    tierInfo.put("description", tier.getDescription());
                    tierInfo.put("requestsPerMonth", tier.getRequestsPerMonth());
                    tierInfo.put("requestsPerHour", tier.getRequestsPerHour());
                    tierInfo.put("requestsPerMinute", tier.getRequestsPerMinute());
                    tierInfo.put("requestsPerSecond", tier.getRequestsPerSecond());
                    tierInfo.put("monthlyPrice", tier.getMonthlyPrice());
                    tierInfo.put("isUnlimited", tier.isUnlimited());
                    tierInfo.put("windowDurationSeconds", tier.getWindowSizeSeconds());
                    return tierInfo;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("tiers", tiers);
        response.put("totalCount", tiers.size());
        response.put("defaultTier", RateLimitTier.getDefaultTier(false).name());
        response.put("adminDefaultTier", RateLimitTier.getDefaultTier(true).name());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api-key-prefixes")
    @Operation(
            summary = "Get Common API Key Prefixes",
            description = "Retrieve commonly used API key prefixes and their meanings",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Prefixes retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required")
    })
    public ResponseEntity<Map<String, Object>> getApiKeyPrefixes() {
        log.debug("Retrieving common API key prefixes");

        List<Map<String, String>> prefixes = List.of(
                Map.of("prefix", "sk-", "description", "Standard API key for general use", "category", "Standard"),
                Map.of("prefix", "biz-", "description", "Business API key for commercial operations", "category", "Business"),
                Map.of("prefix", "admin-", "description", "Administrative API key with elevated privileges", "category", "Administrative"),
                Map.of("prefix", "srv-", "description", "Server-to-server API key for backend services", "category", "Server"),
                Map.of("prefix", "dev-", "description", "Development API key for testing", "category", "Development"),
                Map.of("prefix", "test-", "description", "Testing API key for QA environments", "category", "Testing"),
                Map.of("prefix", "mrtfy-", "description", "MRTFY platform specific API key", "category", "Platform"),
                Map.of("prefix", "s2s-", "description", "Server-to-server communication key", "category", "Server"),
                Map.of("prefix", "int-", "description", "Internal service API key", "category", "Internal"),
                Map.of("prefix", "pub-", "description", "Public API key with limited access", "category", "Public")
        );

        Map<String, Object> response = new HashMap<>();
        response.put("prefixes", prefixes);
        response.put("totalCount", prefixes.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/system-info")
    @Operation(
            summary = "Get System Information",
            description = "Retrieve general system information and API capabilities",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "System information retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Authentication required")
    })
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        log.debug("Retrieving system information");

        Map<String, Object> systemInfo = new HashMap<>();
        systemInfo.put("apiVersion", "2.0.0");
        systemInfo.put("supportedAuthMethods", List.of("JWT Bearer Token", "API Key"));
        systemInfo.put("maxApiKeysPerUser", 50);
        systemInfo.put("supportedDomainFormats", List.of("example.com", "subdomain.example.com", "localhost", "127.0.0.1"));
        systemInfo.put("supportedIpFormats", List.of("192.168.1.1", "192.168.1.0/24", "::1"));
        systemInfo.put("defaultKeyExpiration", "Never (unless specified)");
        systemInfo.put("keyRotationSupported", true);
        systemInfo.put("domainValidationEnabled", true);
        systemInfo.put("rateLimitingEnabled", true);
        systemInfo.put("usageAnalyticsEnabled", true);
        systemInfo.put("requestLoggingEnabled", true);

        Map<String, Object> response = new HashMap<>();
        response.put("system", systemInfo);
        response.put("timestamp", java.time.Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Get scope category for grouping
     */
    private String getScopeCategory(ApiKeyScope scope) {
        String scopeName = scope.name();
        
        if (scopeName.startsWith("READ_")) {
            return "Read Permissions";
        } else if (scopeName.startsWith("WRITE_")) {
            return "Write Permissions";
        } else if (scopeName.startsWith("DELETE_")) {
            return "Delete Permissions";
        } else if (scopeName.contains("ADMIN") || scopeName.equals("SYSTEM_MONITOR")) {
            return "Administrative";
        } else if (scopeName.startsWith("BUSINESS_")) {
            return "Business Operations";
        } else if (scopeName.contains("SERVER") || scopeName.contains("BACKEND") || scopeName.contains("INTERNAL")) {
            return "Server-to-Server";
        } else if (scopeName.contains("API_KEY")) {
            return "API Key Management";
        } else if (scopeName.equals("FULL_ACCESS") || scopeName.equals("DOMAINLESS_ACCESS")) {
            return "Special Permissions";
        } else {
            return "Other";
        }
    }

    /**
     * Get all scope categories
     */
    private List<String> getScopeCategories() {
        return List.of(
                "Read Permissions",
                "Write Permissions", 
                "Delete Permissions",
                "Administrative",
                "Business Operations",
                "Server-to-Server",
                "API Key Management",
                "Special Permissions",
                "Other"
        );
    }
}