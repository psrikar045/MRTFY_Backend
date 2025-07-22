package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.service.AdminApiKeyService;
import com.example.jwtauthenticator.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin controller for managing API keys across all users.
 * Only accessible by users with ADMIN role via JWT authentication.
 */
@RestController
@RequestMapping("/api/admin/api-keys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin API Keys", description = "Administrative endpoints for managing all API keys")
@PreAuthorize("hasRole('ADMIN')") // All methods require ADMIN role
public class AdminApiKeyController {
    
    private final AdminApiKeyService adminApiKeyService;
    private final RateLimitService rateLimitService;
    
    @GetMapping("/all")
    @Operation(
        summary = "Get all API keys (Admin only)",
        description = "Retrieves all API keys across all users. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of all API keys retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<List<ApiKeyResponseDTO>> getAllApiKeys() {
        log.info("Admin retrieving all API keys");
        List<ApiKeyResponseDTO> apiKeys = adminApiKeyService.getAllApiKeys();
        return ResponseEntity.ok(apiKeys);
    }
    
    @GetMapping("/user/{userId}")
    @Operation(
        summary = "Get API keys for a specific user (Admin only)",
        description = "Retrieves all API keys for a specific user. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User API keys retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<List<ApiKeyResponseDTO>> getApiKeysForUser(
            @Parameter(description = "User ID to retrieve API keys for", required = true)
            @PathVariable String userId) {
        log.info("Admin retrieving API keys for user: {}", userId);
        List<ApiKeyResponseDTO> apiKeys = adminApiKeyService.getApiKeysForUser(userId);
        return ResponseEntity.ok(apiKeys);
    }
    
    @PostMapping("/user/{userId}")
    @Operation(
        summary = "Create API key for a user (Admin only)",
        description = "Creates a new API key for the specified user. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "API Key created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<ApiKeyGeneratedResponseDTO> createApiKeyForUser(
            @Parameter(description = "User ID to create API key for", required = true)
            @PathVariable String userId,
            @Valid @RequestBody ApiKeyCreateRequestDTO request,
            @AuthenticationPrincipal UserDetails adminUser) {
        
        log.info("Admin {} creating API key for user: {} with name: {}", 
                adminUser.getUsername(), userId, request.getName());
        
        ApiKeyGeneratedResponseDTO response = adminApiKeyService.createApiKeyForUser(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @PatchMapping("/{keyId}/revoke")
    @Operation(
        summary = "Revoke any API key (Admin only)",
        description = "Revokes any API key in the system. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "API Key revoked successfully"),
        @ApiResponse(responseCode = "404", description = "API Key not found"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<Void> revokeApiKey(
            @Parameter(description = "API Key ID to revoke", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails adminUser) {
        
        log.info("Admin {} revoking API key: {}", adminUser.getUsername(), keyId);
        boolean revoked = adminApiKeyService.revokeApiKey(keyId);
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
    
    @DeleteMapping("/{keyId}")
    @Operation(
        summary = "Delete any API key (Admin only)",
        description = "Permanently deletes any API key in the system. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "API Key deleted successfully"),
        @ApiResponse(responseCode = "404", description = "API Key not found"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<Void> deleteApiKey(
            @Parameter(description = "API Key ID to delete", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails adminUser) {
        
        log.info("Admin {} deleting API key: {}", adminUser.getUsername(), keyId);
        boolean deleted = adminApiKeyService.deleteApiKey(keyId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/{keyId}/usage")
    @Operation(
        summary = "Get API key usage statistics (Admin only)",
        description = "Retrieves usage statistics for a specific API key. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Usage statistics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "API Key not found"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<Map<String, Object>> getApiKeyUsage(
            @Parameter(description = "API Key ID to get usage for", required = true)
            @PathVariable UUID keyId) {
        
        log.info("Admin retrieving usage statistics for API key: {}", keyId);
        Map<String, Object> usageStats = adminApiKeyService.getApiKeyUsageStats(keyId);
        
        if (usageStats.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(usageStats);
    }
    
    @GetMapping("/stats")
    @Operation(
        summary = "Get system-wide API key statistics (Admin only)",
        description = "Retrieves system-wide API key statistics. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "System statistics retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        log.info("Admin retrieving system-wide API key statistics");
        Map<String, Object> stats = adminApiKeyService.getSystemStats();
        return ResponseEntity.ok(stats);
    }
    
    @PostMapping("/{keyId}/rate-limit/reset")
    @Operation(
        summary = "Reset rate limit for an API key (Admin only)",
        description = "Resets the rate limit counter for a specific API key. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Rate limit reset successfully"),
        @ApiResponse(responseCode = "404", description = "API Key not found"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<Void> resetRateLimit(
            @Parameter(description = "API Key ID to reset rate limit for", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails adminUser) {
        
        log.info("Admin {} resetting rate limit for API key: {}", adminUser.getUsername(), keyId);
        boolean reset = adminApiKeyService.resetRateLimit(keyId);
        return reset ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
    
    @PutMapping("/{keyId}/scopes")
    @Operation(
        summary = "Update API key scopes (Admin only)",
        description = "Updates the scopes for a specific API key. Only accessible by admin users.",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Scopes updated successfully"),
        @ApiResponse(responseCode = "404", description = "API Key not found"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
    })
    public ResponseEntity<ApiKeyResponseDTO> updateApiKeyScopes(
            @Parameter(description = "API Key ID to update scopes for", required = true)
            @PathVariable UUID keyId,
            @RequestBody Map<String, String> scopeRequest,
            @AuthenticationPrincipal UserDetails adminUser) {
        
        String newScopes = scopeRequest.get("scopes");
        log.info("Admin {} updating scopes for API key: {} to: {}", 
                adminUser.getUsername(), keyId, newScopes);
        
        ApiKeyResponseDTO updatedKey = adminApiKeyService.updateApiKeyScopes(keyId, newScopes);
        return updatedKey != null ? ResponseEntity.ok(updatedKey) : ResponseEntity.notFound().build();
    }
}