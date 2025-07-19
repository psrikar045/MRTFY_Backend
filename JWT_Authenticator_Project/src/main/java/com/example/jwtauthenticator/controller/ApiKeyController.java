package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyUpdateRequestDTO;
import com.example.jwtauthenticator.service.ApiKeyService;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails; // Or your custom UserDetails implementation
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping; // For revoke, if you prefer PATCH
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys") // Base path for API key related endpoints
@RequiredArgsConstructor // For constructor injection of ApiKeyService
@Slf4j // For logging
@Tag(name = "API Keys", description = "Endpoints for managing user API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * Helper method to get the current authenticated user's 'id' (String) from Spring Security.
     * YOU MUST CUSTOMIZE THIS METHOD based on your UserDetails implementation.
     *
     * @param userDetails The authenticated UserDetails object provided by Spring Security.
     * @return The String ID of the authenticated user.
     * @throws IllegalStateException if the user ID cannot be extracted (e.g., not logged in).
     */
    private String getCurrentUserId(@AuthenticationPrincipal UserDetails userDetails) {
        // --- CUSTOMIZATION POINT ---
        // Replace this with your actual logic to extract the String 'id'
        // from your UserDetails implementation.
        // Example if CustomUserDetails has a getId() method:
        // if (userDetails instanceof CustomUserDetails customUserDetails) {
        //     return customUserDetails.getId();
        // }
        // If your UserDetails implementation's getUsername() method returns the user's 'id' (String):
        log.debug("Attempting to get current user ID from UserDetails.getUsername()");
        return userDetails.getUsername();
        // If you can't get it, throw an exception or handle accordingly:
        // throw new IllegalStateException("Authenticated user ID not found in principal.");
        // --- END CUSTOMIZATION POINT ---
    }


    @PostMapping
    @Operation(
            summary = "Create a new API Key",
            description = "Generates and returns a new API key for the authenticated user. " +
                          "The key value is shown ONLY once upon creation.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "API Key created successfully",
                        content = @Content(schema = @Schema(implementation = ApiKeyGeneratedResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User not authenticated"),
            @ApiResponse(responseCode = "404", description = "User not found (if internal validation fails)")
    })
    public ResponseEntity<ApiKeyGeneratedResponseDTO> createApiKey(
            @Valid @RequestBody ApiKeyCreateRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userFkId = getCurrentUserId(userDetails);
        log.info("User {} creating new API key with name: {}", userFkId, request.getName());
        ApiKeyGeneratedResponseDTO response = apiKeyService.createApiKey(userFkId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
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
}