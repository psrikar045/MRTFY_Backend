package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.annotation.RequireApiKeyScope;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.enums.ApiKeyScope;
import com.example.jwtauthenticator.security.ApiKeyAuthentication;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * External API Controller for business integrations.
 * All endpoints require valid API keys with appropriate scopes.
 * 
 * This controller demonstrates how to create scope-protected endpoints
 * that external businesses can access using their API keys.
 */
@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "External API", description = "API endpoints for external business integrations")
public class ExternalApiController {
    
    /**
     * Example endpoint for reading user data (requires READ_USERS scope).
     */
    @GetMapping("/users")
    @RequireApiKeyScope(ApiKeyScope.READ_USERS)
    @Operation(
        summary = "Get users data (API Key required)",
        description = "Retrieves users data. Requires API key with READ_USERS scope.",
        security = { @SecurityRequirement(name = "API Key") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Users data retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
        @ApiResponse(responseCode = "403", description = "Insufficient API key permissions"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Map<String, Object>> getUsers(
            @AuthenticationPrincipal ApiKeyAuthentication apiKeyAuth) {
        
        log.info("External API request for users data from API key: {} (user: {})",
                apiKeyAuth.getName(), apiKeyAuth.getUserId());
        
        // Simulate user data response
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Users data accessed successfully");
        response.put("apiKey", apiKeyAuth.getName());
        response.put("userId", apiKeyAuth.getUserId());
        response.put("scopes", apiKeyAuth.getScopes());
        
        // In real implementation, you would fetch actual user data
        response.put("users", List.of(
            Map.of("id", "1", "name", "John Doe", "email", "john@example.com"),
            Map.of("id", "2", "name", "Jane Smith", "email", "jane@example.com")
        ));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Example endpoint for creating user data (requires WRITE_USERS scope).
     */
    @PostMapping("/users")
    @RequireApiKeyScope(ApiKeyScope.WRITE_USERS)
    @Operation(
        summary = "Create user data (API Key required)",
        description = "Creates new user data. Requires API key with WRITE_USERS scope.",
        security = { @SecurityRequirement(name = "API Key") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
        @ApiResponse(responseCode = "403", description = "Insufficient API key permissions"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, Object> userData,
            @AuthenticationPrincipal ApiKeyAuthentication apiKeyAuth) {
        
        log.info("External API request to create user from API key: {} (user: {})",
                apiKeyAuth.getName(), apiKeyAuth.getUserId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User created successfully");
        response.put("apiKey", apiKeyAuth.getName());
        response.put("userData", userData);
        
        return ResponseEntity.status(201).body(response);
    }
    
    /**
     * Example endpoint for reading brand data (requires READ_BRANDS scope).
     */
    @GetMapping("/brands")
    @RequireApiKeyScope(ApiKeyScope.READ_BRANDS)
    @Operation(
        summary = "Get brands data (API Key required)",
        description = "Retrieves brands data. Requires API key with READ_BRANDS scope.",
        security = { @SecurityRequirement(name = "API Key") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Brands data retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
        @ApiResponse(responseCode = "403", description = "Insufficient API key permissions"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Map<String, Object>> getBrands(
            @AuthenticationPrincipal ApiKeyAuthentication apiKeyAuth) {
        
        log.info("External API request for brands data from API key: {} (user: {})",
                apiKeyAuth.getName(), apiKeyAuth.getUserId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Brands data accessed successfully");
        response.put("apiKey", apiKeyAuth.getName());
        
        // Simulate brand data response
        response.put("brands", List.of(
            Map.of("id", "1", "name", "Brand A", "category", "Technology"),
            Map.of("id", "2", "name", "Brand B", "category", "Fashion")
        ));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Example endpoint for business-specific operations.
     */
    @GetMapping("/business-data")
    @RequireApiKeyScope({ApiKeyScope.BUSINESS_READ, ApiKeyScope.BUSINESS_WRITE})
    @Operation(
        summary = "Get business data (API Key required)",
        description = "Retrieves business-specific data. Requires API key with BUSINESS_READ or BUSINESS_WRITE scope.",
        security = { @SecurityRequirement(name = "API Key") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Business data retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
        @ApiResponse(responseCode = "403", description = "Insufficient API key permissions"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Map<String, Object>> getBusinessData(
            @AuthenticationPrincipal ApiKeyAuthentication apiKeyAuth) {
        
        log.info("External API request for business data from API key: {} (user: {})",
                apiKeyAuth.getName(), apiKeyAuth.getUserId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Business data accessed successfully");
        response.put("apiKey", apiKeyAuth.getName());
        response.put("businessData", Map.of(
            "revenue", "$50,000",
            "customers", 150,
            "products", 25
        ));
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Example endpoint requiring multiple scopes.
     */
    @PostMapping("/admin-operation")
    @RequireApiKeyScope(value = {ApiKeyScope.ADMIN_ACCESS, ApiKeyScope.WRITE_USERS}, requireAll = true)
    @Operation(
        summary = "Perform admin operation (API Key required)",
        description = "Performs administrative operation. Requires API key with BOTH ADMIN_ACCESS AND WRITE_USERS scopes.",
        security = { @SecurityRequirement(name = "API Key") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Admin operation completed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
        @ApiResponse(responseCode = "403", description = "Insufficient API key permissions - requires both ADMIN_ACCESS and WRITE_USERS scopes"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Map<String, Object>> performAdminOperation(
            @RequestBody Map<String, Object> operationData,
            @AuthenticationPrincipal ApiKeyAuthentication apiKeyAuth) {
        
        log.info("External API admin operation from API key: {} (user: {})",
                apiKeyAuth.getName(), apiKeyAuth.getUserId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Admin operation completed successfully");
        response.put("apiKey", apiKeyAuth.getName());
        response.put("operation", operationData);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Endpoint to check API key status and permissions.
     */
    @GetMapping("/key-info")
    @RequireApiKeyScope(ApiKeyScope.READ_API_KEYS)
    @Operation(
        summary = "Get API key information (API Key required)",
        description = "Retrieves information about the current API key. Requires API key with READ_API_KEYS scope.",
        security = { @SecurityRequirement(name = "API Key") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "API key information retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid or missing API key"),
        @ApiResponse(responseCode = "403", description = "Insufficient API key permissions"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<Map<String, Object>> getApiKeyInfo(
            @AuthenticationPrincipal ApiKeyAuthentication apiKeyAuth) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("keyName", apiKeyAuth.getName());
        response.put("userId", apiKeyAuth.getUserId());
        response.put("scopes", apiKeyAuth.getScopes());
        response.put("authorities", apiKeyAuth.getAuthorities());
        
        return ResponseEntity.ok(response);
    }
}