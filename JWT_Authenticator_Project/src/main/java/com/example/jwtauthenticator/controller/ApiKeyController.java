package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyUpdateRequestDTO;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.ApiKeyEnvironment;
import com.example.jwtauthenticator.exception.PlanLimitExceededException;
import com.example.jwtauthenticator.exception.DomainValidationException;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.service.ApiKeyService;
import com.example.jwtauthenticator.service.EnhancedApiKeyService;
import com.example.jwtauthenticator.util.JwtUtil;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping; // For revoke, if you prefer PATCH
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys") // Base path for API key related endpoints
@RequiredArgsConstructor // For constructor injection of ApiKeyService
@Slf4j // For logging
@Tag(name = "API Keys", description = "Endpoints for managing user API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final EnhancedApiKeyService enhancedApiKeyService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    
    // INTEGRATION: Add new services for comprehensive API key functionality
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.jwtauthenticator.service.UsageStatsService usageStatsService;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.jwtauthenticator.service.RequestLoggingService requestLoggingService;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.jwtauthenticator.service.ApiKeyAddOnService addOnService;

    /**
     * Helper method to get the current authenticated user's 'id' (String) from Spring Security.
     * Extracts the user ID from JWT token claims via the SecurityContext.
     *
     * @param userDetails The authenticated UserDetails object provided by Spring Security.
     * @return The String ID of the authenticated user.
     * @throws IllegalStateException if the user ID cannot be extracted.
     */
    private String getCurrentUserId(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            // Get the JWT token from the SecurityContext
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                throw new IllegalStateException("No authentication found in SecurityContext");
            }
            
            // Extract JWT token from the request
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new IllegalStateException("No valid JWT token found in Authorization header");
            }
            
            String jwt = authHeader.substring(7);
            String userId = jwtUtil.extractUserID(jwt);
            
            if (userId == null || userId.trim().isEmpty()) {
                // Fallback: try to find user by username
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


    @PostMapping
    @Operation(
            summary = "Create a new API Key",
            description = "Generates and returns a new API key for the authenticated user. " +
                          "The key value is shown ONLY once upon creation. " +
                          "Supports all rate limit tiers (FREE_TIER, PRO_TIER, BUSINESS_TIER) " +
                          "and comprehensive scope-based permissions.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "API Key created successfully",
                        content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiKeyGeneratedResponseDTO.class),
                            examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                name = "API Key Created",
                                value = com.example.jwtauthenticator.dto.ApiKeyExamples.API_KEY_CREATE_RESPONSE
                            )
                        )),
            @ApiResponse(responseCode = "400", description = "Invalid request payload - Check domain format, scopes, and rate limit tier"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated"),
            @ApiResponse(responseCode = "409", description = "Conflict - Domain already registered by another API key")
    })
    public ResponseEntity<?> createApiKey(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "API Key creation request with domain, scopes, and rate limiting configuration",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyCreateRequestDTO.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Basic API Key",
                            description = "Create a basic API key with read permissions",
                            value = com.example.jwtauthenticator.dto.ApiKeyExamples.BASIC_API_KEY_CREATE_REQUEST
                        ),
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Business API Key",
                            description = "Create a premium business API key with advanced features",
                            value = com.example.jwtauthenticator.dto.ApiKeyExamples.BUSINESS_API_KEY_CREATE_REQUEST
                        )
                    }
                )
            )
            @Valid @RequestBody ApiKeyCreateRequestDTO request,
            @RequestParam(value = "environment", defaultValue = "production") String environmentParam,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.info("User {} creating new API key with name: '{}', domain: '{}', environment: '{}'", 
                userFkId, request.getName(), request.getRegisteredDomain(), environmentParam);

        try {
            // Parse environment parameter
            ApiKeyEnvironment environment = ApiKeyEnvironment.fromValue(environmentParam);
            
            // Use enhanced API key service with comprehensive validation
            EnhancedApiKeyService.ApiKeyCreateResult result = 
                    enhancedApiKeyService.createApiKeyWithPlanValidation(request, userFkId, environment);
            
            if (!result.isSuccess()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", result.getErrorMessage());
                errorResponse.put("errorCode", result.getErrorCode());
                errorResponse.put("timestamp", java.time.Instant.now().toString());
                
                // Determine HTTP status based on error code
                HttpStatus status = switch (result.getErrorCode()) {
                    case "API_KEY_LIMIT_EXCEEDED", "DOMAIN_LIMIT_EXCEEDED" -> HttpStatus.FORBIDDEN;
                    case "DOMAIN_VALIDATION_FAILED", "INVALID_DOMAIN_FORMAT" -> HttpStatus.BAD_REQUEST;
                    case "DOMAIN_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
                    default -> HttpStatus.BAD_REQUEST;
                };
                
                return ResponseEntity.status(status).body(errorResponse);
            }
            
            // Success - create response DTO
            ApiKeyGeneratedResponseDTO response = ApiKeyGeneratedResponseDTO.builder()
                    .id(result.getApiKey().getId())
                    .name(result.getApiKey().getName())
                    .description(result.getApiKey().getDescription())
                    .prefix(result.getApiKey().getPrefix())
                    .keyValue(result.getRawApiKey()) // Only shown once
                    .registeredDomain(result.getApiKey().getRegisteredDomain())
                    .mainDomain(result.getApiKey().getMainDomain())
                    .subdomainPattern(result.getApiKey().getSubdomainPattern())
                    .environment(result.getApiKey().getEnvironment().getValue())
                    .allowedDomains(result.getApiKey().getAllowedDomains())
                    .allowedIps(result.getApiKey().getAllowedIps())
                    .scopes(result.getApiKey().getScopes())
                    .rateLimitTier(result.getApiKey().getRateLimitTier().name())
                    .isActive(result.getApiKey().getIsActive())
                    .expiresAt(result.getApiKey().getExpiresAt())
                    .createdAt(result.getApiKey().getCreatedAt())
                    .build();
            
            log.info("✅ API key created successfully for user '{}': ID={}, Domain={}", 
                    userFkId, result.getApiKey().getId(), result.getApiKey().getRegisteredDomain());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (PlanLimitExceededException e) {
            log.warn("Plan limit exceeded for user '{}': {}", userFkId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("errorCode", e.getErrorCode());
            errorResponse.put("currentPlan", e.getUserPlan() != null ? e.getUserPlan().name() : null);
            errorResponse.put("upgradeMessage", e.getUpgradeMessage());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            
        } catch (DomainValidationException e) {
            log.warn("Domain validation failed for user '{}': {}", userFkId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("errorCode", e.getErrorCode());
            errorResponse.put("domain", e.getDomain());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            
        } catch (Exception e) {
            log.error("❌ Unexpected error creating API key for user '{}': {}", userFkId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            errorResponse.put("errorCode", "INTERNAL_ERROR");
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping
    @Operation(
            summary = "Get all API Keys for the current user",
            description = "Retrieves a list of all API keys associated with the authenticated user. " +
                          "The actual key value is NOT included for security reasons.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of API keys retrieved successfully",
                        content = @Content(schema = @Schema(implementation = ApiKeyResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<List<ApiKeyResponseDTO>> getApiKeysForCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.info("User {} requesting all API keys.", userFkId);
        List<ApiKeyResponseDTO> apiKeys = apiKeyService.getApiKeysForUser(userFkId);
        return ResponseEntity.ok(apiKeys);
    }

    @GetMapping("/{keyId}")
    @Operation(
            summary = "Get a specific API Key by ID",
            description = "Retrieves details for a specific API key belonging to the authenticated user. " +
                          "The actual key value is NOT included.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "API Key found",
                        content = @Content(schema = @Schema(implementation = ApiKeyResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "API Key not found or does not belong to user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<ApiKeyResponseDTO> getApiKeyById(
            @Parameter(description = "UUID of the API Key to retrieve", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.info("User {} requesting API key by ID: {}", userFkId, keyId);
        return apiKeyService.getApiKeyByIdForUser(keyId, userFkId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("API Key with ID {} not found for user {}", keyId, userFkId);
                    return ResponseEntity.notFound().build();
                });
    }

    @PutMapping("/{keyId}")
    @Operation(
            summary = "Update an existing API Key",
            description = "Updates the mutable properties (name, description, active status, expiration, restrictions) " +
                          "of an API key belonging to the authenticated user.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "API Key updated successfully",
                        content = @Content(schema = @Schema(implementation = ApiKeyResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "API Key not found or does not belong to user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<ApiKeyResponseDTO> updateApiKey(
            @Parameter(description = "UUID of the API Key to update", required = true)
            @PathVariable UUID keyId,
            @Valid @RequestBody ApiKeyUpdateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.info("User {} updating API key with ID: {}", userFkId, keyId);
        return apiKeyService.updateApiKey(keyId, userFkId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("API Key with ID {} not found for user {} for update.", keyId, userFkId);
                    return ResponseEntity.notFound().build();
                });
    }

    @PatchMapping("/{keyId}/revoke") // Using PATCH for partial update/state change
    @Operation(
            summary = "Revoke an API Key",
            description = "Deactivates and marks an API key as revoked, preventing its future use.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "API Key revoked successfully"),
            @ApiResponse(responseCode = "404", description = "API Key not found or does not belong to user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<Void> revokeApiKey(
            @Parameter(description = "UUID of the API Key to revoke", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.info("User {} revoking API key with ID: {}", userFkId, keyId);
        boolean revoked = apiKeyService.revokeApiKey(keyId, userFkId);
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{keyId}")
    @Operation(
            summary = "Delete an API Key",
            description = "Permanently deletes an API key belonging to the authenticated user.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "API Key deleted successfully"),
            @ApiResponse(responseCode = "404", description = "API Key not found or does not belong to user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<Void> deleteApiKey(
            @Parameter(description = "UUID of the API Key to delete", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.info("User {} deleting API key with ID: {}", userFkId, keyId);
        boolean deleted = apiKeyService.deleteApiKey(keyId, userFkId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // --- NEW PLAN AND USAGE ENDPOINTS ---
    
    @GetMapping("/plan-usage")
    @Operation(
            summary = "Get User Plan Usage",
            description = "Get current user's plan usage including API keys, domains, and quotas",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan usage retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<?> getUserPlanUsage(@AuthenticationPrincipal UserDetails userDetails) {
        String userId = getCurrentUserId(userDetails);
        log.info("User {} requesting plan usage information", userId);

        try {
            EnhancedApiKeyService.UserPlanUsage planUsage = enhancedApiKeyService.getUserPlanUsage(userId);
            
            if (planUsage == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("plan", planUsage.getPlan().name());
            response.put("planDisplayName", planUsage.getPlan().getDisplayName());
            response.put("currentApiKeys", planUsage.getCurrentApiKeys());
            response.put("maxApiKeys", planUsage.getMaxApiKeys());
            response.put("remainingApiKeys", planUsage.getRemainingApiKeys());
            response.put("currentDomains", planUsage.getCurrentDomains());
            response.put("maxDomains", planUsage.getMaxDomains());
            response.put("remainingDomains", planUsage.getRemainingDomains());
            response.put("canCreateApiKey", planUsage.isCanCreateApiKey());
            response.put("canClaimDomain", planUsage.isCanClaimDomain());
            response.put("monthlyApiCalls", planUsage.getPlan().getMonthlyApiCalls());
            response.put("price", planUsage.getPlan().getFormattedPrice());
            response.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting plan usage for user '{}': {}", userId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to get plan usage: " + e.getMessage());
            errorResponse.put("errorCode", "PLAN_USAGE_ERROR");
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // --- NEW DOMAIN-RELATED ENDPOINTS ---

    @PostMapping("/rivo-create-api")
    @Operation(
            summary = "Create Enhanced API Key (Rivo Platform)",
            description = "Creates a new API key with advanced domain-based access control for Rivo platform integration. " +
                         "Features include: multiple domain support, IP restrictions, development domain support, " +
                         "unlimited rate limiting, and comprehensive scope management. " +
                         "Supports all domain types: .com, .org, .io, .in, .co, localhost, IP addresses, etc.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                responseCode = "201", 
                description = "Enhanced API Key created successfully with domain validation",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyGeneratedResponseDTO.class),
                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "Enhanced API Key Created",
                        value = com.example.jwtauthenticator.dto.ApiKeyExamples.API_KEY_CREATE_RESPONSE
                    )
                )
            ),
            @ApiResponse(
                responseCode = "400", 
                description = "Invalid request - Domain format invalid, scopes invalid, or validation failed",
                content = @Content(
                    mediaType = "application/json",
                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "Validation Error",
                        value = com.example.jwtauthenticator.dto.ApiKeyExamples.ERROR_RESPONSE
                    )
                )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated"),
            @ApiResponse(responseCode = "409", description = "Conflict - Domain already registered by another API key")
    })
    public ResponseEntity<?> createApiKeyWithDomain(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Enhanced API Key creation request with domain validation, IP restrictions, and advanced features",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyCreateRequestDTO.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Rivo Enhanced Key",
                            description = "Create enhanced API key with full access and development support",
                            value = com.example.jwtauthenticator.dto.ApiKeyExamples.ENHANCED_API_KEY_CREATE_REQUEST
                        ),
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Server-to-Server Key",
                            description = "Create server-to-server API key for backend services",
                            value = com.example.jwtauthenticator.dto.ApiKeyExamples.SERVER_API_KEY_CREATE_REQUEST
                        )
                    }
                )
            )
            @Valid @RequestBody ApiKeyCreateRequestDTO request,
            @RequestParam(value = "environment", defaultValue = "production") String environmentParam,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = getCurrentUserId(userDetails);
        log.info("User {} creating enhanced API key with domain: '{}', environment: '{}'", 
                userId, request.getRegisteredDomain(), environmentParam);

        try {
            // Parse environment parameter
            ApiKeyEnvironment environment = ApiKeyEnvironment.fromValue(environmentParam);
            
            // Use the same comprehensive validation as the main endpoint
            EnhancedApiKeyService.ApiKeyCreateResult result = 
                    enhancedApiKeyService.createApiKeyWithPlanValidation(request, userId, environment);

            if (!result.isSuccess()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", result.getErrorMessage());
                errorResponse.put("errorCode", result.getErrorCode());
                errorResponse.put("timestamp", java.time.Instant.now().toString());
                
                // Determine HTTP status based on error code
                HttpStatus status = switch (result.getErrorCode()) {
                    case "API_KEY_LIMIT_EXCEEDED", "DOMAIN_LIMIT_EXCEEDED" -> HttpStatus.FORBIDDEN;
                    case "DOMAIN_VALIDATION_FAILED", "INVALID_DOMAIN_FORMAT" -> HttpStatus.BAD_REQUEST;
                    case "DOMAIN_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
                    default -> HttpStatus.BAD_REQUEST;
                };
                
                return ResponseEntity.status(status).body(errorResponse);
            }

            // Success - create enhanced response DTO
            ApiKeyGeneratedResponseDTO response = ApiKeyGeneratedResponseDTO.builder()
                    .id(result.getApiKey().getId())
                    .name(result.getApiKey().getName())
                    .description(result.getApiKey().getDescription())
                    .prefix(result.getApiKey().getPrefix())
                    .keyValue(result.getRawApiKey()) // Only shown once
                    .registeredDomain(result.getApiKey().getRegisteredDomain())
                    .mainDomain(result.getApiKey().getMainDomain())
                    .subdomainPattern(result.getApiKey().getSubdomainPattern())
                    .environment(result.getApiKey().getEnvironment().getValue())
                    .allowedDomains(result.getApiKey().getAllowedDomains())
                    .allowedIps(result.getApiKey().getAllowedIps())
                    .scopes(result.getApiKey().getScopes())
                    .rateLimitTier(result.getApiKey().getRateLimitTier().name())
                    .isActive(result.getApiKey().getIsActive())
                    .expiresAt(result.getApiKey().getExpiresAt())
                    .createdAt(result.getApiKey().getCreatedAt())
                    .build();

            log.info("✅ Enhanced API key created successfully for user '{}': ID={}, Domain={}", 
                    userId, result.getApiKey().getId(), result.getApiKey().getRegisteredDomain());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (PlanLimitExceededException e) {
            log.warn("Plan limit exceeded for user '{}': {}", userId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("errorCode", e.getErrorCode());
            errorResponse.put("currentPlan", e.getUserPlan() != null ? e.getUserPlan().name() : null);
            errorResponse.put("upgradeMessage", e.getUpgradeMessage());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            
        } catch (DomainValidationException e) {
            log.warn("Domain validation failed for user '{}': {}", userId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("errorCode", e.getErrorCode());
            errorResponse.put("domain", e.getDomain());
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            
        } catch (Exception e) {
            log.error("❌ Unexpected error creating enhanced API key for user '{}': {}", userId, e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            errorResponse.put("errorCode", "INTERNAL_ERROR");
            errorResponse.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PutMapping("/{keyId}/domain")
    @Operation(
            summary = "Update API Key Domain",
            description = "Updates the registered domain for an existing API key",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domain updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid domain format or domain already exists"),
            @ApiResponse(responseCode = "404", description = "API Key not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<?> updateApiKeyDomain(
            @Parameter(description = "UUID of the API Key", required = true)
            @PathVariable UUID keyId,
            @Parameter(description = "New domain", required = true)
            @RequestBody Map<String, String> domainRequest,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = getCurrentUserId(userDetails);
        String newDomain = domainRequest.get("domain");

        if (newDomain == null || newDomain.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Domain is required",
                "errorCode", "MISSING_DOMAIN"
            ));
        }

        log.info("User {} updating domain for API key {}: {}", userId, keyId, newDomain);

        EnhancedApiKeyService.ApiKeyUpdateResult result = 
            enhancedApiKeyService.updateRegisteredDomain(keyId, userId, newDomain);

        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", result.getErrorMessage(),
                "errorCode", result.getErrorCode(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }

        return ResponseEntity.ok(ApiKeyResponseDTO.fromEntity(result.getApiKey()));
    }

    @GetMapping("/with-domains")
    @Operation(
            summary = "Get API Keys with Domain Information",
            description = "Retrieves all API keys for the authenticated user with domain information",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200", 
                description = "API Keys retrieved successfully",
                content = @Content(schema = @Schema(implementation = ApiKeyResponseDTO.class))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<List<ApiKeyResponseDTO>> getApiKeysWithDomains(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = getCurrentUserId(userDetails);
        log.debug("User {} fetching API keys with domains", userId);

        List<ApiKeyResponseDTO> apiKeys = enhancedApiKeyService.getUserApiKeysWithDomains(userId);
        return ResponseEntity.ok(apiKeys);
    }

    @GetMapping("/domain/check")
    @Operation(
            summary = "Check Domain Availability",
            description = "Checks if a domain is available for API key registration",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domain availability checked"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<?> checkDomainAvailability(
            @Parameter(description = "Domain to check", required = true)
            @RequestParam String domain,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("Checking domain availability: {}", domain);

        EnhancedApiKeyService.DomainValidationInfo validationInfo = 
            enhancedApiKeyService.validateDomainForRegistration(domain);

        Map<String, Object> response = Map.of(
            "domain", domain,
            "available", validationInfo.isValid(),
            "message", validationInfo.getMessage(),
            "suggestions", validationInfo.getSuggestions()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/domain/suggestions")
    @Operation(
            summary = "Get Domain Suggestions",
            description = "Get domain suggestions based on a base domain name",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Domain suggestions retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated")
    })
    public ResponseEntity<?> getDomainSuggestions(
            @Parameter(description = "Base domain for suggestions", required = true)
            @RequestParam String baseDomain,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.debug("Getting domain suggestions for: {}", baseDomain);

        List<String> suggestions = enhancedApiKeyService.getDomainSuggestions(baseDomain);

        Map<String, Object> response = Map.of(
            "baseDomain", baseDomain,
            "suggestions", suggestions
        );

        return ResponseEntity.ok(response);
    }

    // ========================================
    // INTEGRATION: NEW ENDPOINTS FOR COMPREHENSIVE API KEY FUNCTIONALITY
    // ========================================

    @GetMapping("/{keyId}/usage-stats")
    @Operation(
        summary = "Get Usage Statistics",
        description = "Get detailed usage statistics for an API key",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Usage statistics retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getUsageStats(
            @Parameter(description = "API Key ID", required = true)
            @PathVariable UUID keyId,
            @Parameter(description = "Start date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "End date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.debug("Getting usage statistics for API key: {} by user: {}", keyId, userFkId);

        // Validate API key belongs to user
        if (!apiKeyService.apiKeyBelongsToUser(keyId, userFkId)) {
            return ResponseEntity.notFound().build();
        }

        if (usageStatsService == null) {
            return ResponseEntity.ok(Map.of("message", "Usage statistics service not available"));
        }

        // Set default date range if not provided
        if (from == null) from = LocalDateTime.now().minusDays(30);
        if (to == null) to = LocalDateTime.now();

        try {
            var usageSummary = usageStatsService.getUsageSummary(keyId);
            var detailedStats = usageStatsService.getUsageStatsForApiKey(keyId, from, to);

            Map<String, Object> response = Map.of(
                "apiKeyId", keyId,
                "summary", usageSummary,
                "detailedStats", detailedStats,
                "dateRange", Map.of("from", from, "to", to)
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving usage statistics for API key: {}", keyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve usage statistics"));
        }
    }

    @GetMapping("/{keyId}/request-logs")
    @Operation(
        summary = "Get Request Logs",
        description = "Get detailed request logs for an API key",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Request logs retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getRequestLogs(
            @Parameter(description = "API Key ID", required = true)
            @PathVariable UUID keyId,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Start date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "End date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.debug("Getting request logs for API key: {} by user: {}", keyId, userFkId);

        // Validate API key belongs to user
        if (!apiKeyService.apiKeyBelongsToUser(keyId, userFkId)) {
            return ResponseEntity.notFound().build();
        }

        if (requestLoggingService == null) {
            return ResponseEntity.ok(Map.of("message", "Request logging service not available"));
        }

        // Set default date range if not provided
        if (from == null) from = LocalDateTime.now().minusDays(7);
        if (to == null) to = LocalDateTime.now();

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("requestTimestamp").descending());
            var requestLogs = requestLoggingService.getRequestLogsForApiKey(keyId, from, to, pageable);
            var logSummary = requestLoggingService.getRequestLogSummary(keyId);

            Map<String, Object> response = Map.of(
                "apiKeyId", keyId,
                "summary", logSummary,
                "logs", requestLogs,
                "dateRange", Map.of("from", from, "to", to)
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving request logs for API key: {}", keyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve request logs"));
        }
    }

    @GetMapping("/{keyId}/addons")
    @Operation(
        summary = "Get API Key Add-ons",
        description = "Get all add-on packages for an API key",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Add-ons retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getApiKeyAddOns(
            @Parameter(description = "API Key ID", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.debug("Getting add-ons for API key: {} by user: {}", keyId, userFkId);

        // Validate API key belongs to user
        if (!apiKeyService.apiKeyBelongsToUser(keyId, userFkId)) {
            return ResponseEntity.notFound().build();
        }

        if (addOnService == null) {
            return ResponseEntity.ok(Map.of("message", "Add-on service not available"));
        }

        try {
            var activeAddOns = addOnService.getActiveAddOnsForApiKey(keyId.toString());
            int totalAdditionalRequests = activeAddOns.stream()
                .mapToInt(addon -> addon.getRequestsRemaining())
                .sum();

            Map<String, Object> response = Map.of(
                "apiKeyId", keyId,
                "activeAddOns", activeAddOns,
                "totalAdditionalRequests", totalAdditionalRequests,
                "addOnCount", activeAddOns.size()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving add-ons for API key: {}", keyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve add-ons"));
        }
    }

    @GetMapping("/{keyId}/complete-info")
    @Operation(
        summary = "Get Complete API Key Information",
        description = "Get comprehensive information about an API key including usage stats, logs, and add-ons",
        security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Complete information retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getCompleteApiKeyInfo(
            @Parameter(description = "API Key ID", required = true)
            @PathVariable UUID keyId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.debug("Getting complete info for API key: {} by user: {}", keyId, userFkId);

        // Validate API key belongs to user
        Optional<ApiKeyResponseDTO> apiKeyOpt = apiKeyService.getApiKeyByIdForUser(keyId, userFkId);
        if (apiKeyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            ApiKeyResponseDTO apiKey = apiKeyOpt.get();
            Map<String, Object> completeInfo = new HashMap<>();
            completeInfo.put("apiKey", apiKey);

            // Add usage statistics if service is available
            if (usageStatsService != null) {
                try {
                    var usageSummary = usageStatsService.getUsageSummary(keyId);
                    completeInfo.put("usageStats", usageSummary);
                } catch (Exception e) {
                    log.warn("Failed to get usage stats for API key: {}", keyId, e);
                    completeInfo.put("usageStats", null);
                }
            }

            // Add recent request logs if service is available
            if (requestLoggingService != null) {
                try {
                    var recentLogs = requestLoggingService.getRecentRequestLogs(keyId, 10);
                    var logSummary = requestLoggingService.getRequestLogSummary(keyId);
                    completeInfo.put("recentLogs", recentLogs);
                    completeInfo.put("logSummary", logSummary);
                } catch (Exception e) {
                    log.warn("Failed to get request logs for API key: {}", keyId, e);
                    completeInfo.put("recentLogs", null);
                    completeInfo.put("logSummary", null);
                }
            }

            // Add add-ons if service is available
            if (addOnService != null) {
                try {
                    var activeAddOns = addOnService.getActiveAddOnsForApiKey(keyId.toString());
                    int totalAdditionalRequests = activeAddOns.stream()
                        .mapToInt(addon -> addon.getRequestsRemaining())
                        .sum();
                    
                    completeInfo.put("activeAddOns", activeAddOns);
                    completeInfo.put("totalAdditionalRequests", totalAdditionalRequests);
                } catch (Exception e) {
                    log.warn("Failed to get add-ons for API key: {}", keyId, e);
                    completeInfo.put("activeAddOns", null);
                    completeInfo.put("totalAdditionalRequests", 0);
                }
            }

            completeInfo.put("timestamp", LocalDateTime.now());
            completeInfo.put("servicesAvailable", Map.of(
                "usageStats", usageStatsService != null,
                "requestLogs", requestLoggingService != null,
                "addOns", addOnService != null
            ));

            return ResponseEntity.ok(completeInfo);
        } catch (Exception e) {
            log.error("Error retrieving complete info for API key: {}", keyId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve complete API key information"));
        }
    }
}