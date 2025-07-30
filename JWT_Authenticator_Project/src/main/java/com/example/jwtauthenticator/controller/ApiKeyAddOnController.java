package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.AddOnPurchaseRequestDTO;
import com.example.jwtauthenticator.dto.AddOnRecommendationDTO;
import com.example.jwtauthenticator.entity.ApiKeyAddOn;
import com.example.jwtauthenticator.entity.AddOnPackage;
import com.example.jwtauthenticator.service.ApiKeyAddOnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Controller for API key add-on package management
 * Professional add-on system for scaling API usage
 */
@RestController
@RequestMapping("/api/v1/api-keys/addons")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "API Key Add-ons", description = "Add-on packages for additional API requests")
@SecurityRequirement(name = "bearerAuth")
public class ApiKeyAddOnController {

    private final ApiKeyAddOnService addOnService;

    @GetMapping("/packages")
    @Operation(
        summary = "Get available add-on packages",
        description = "Get list of all available add-on packages with pricing"
    )
    @ApiResponse(responseCode = "200", description = "Add-on packages retrieved successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<AddOnPackage>> getAvailablePackages() {
        log.info("Getting available add-on packages");
        List<AddOnPackage> packages = Arrays.asList(AddOnPackage.values());
        return ResponseEntity.ok(packages);
    }

    @PostMapping("/purchase")
    @Operation(
        summary = "Purchase an add-on package",
        description = "Purchase additional API requests through add-on packages"
    )
    @ApiResponse(responseCode = "200", description = "Add-on purchased successfully")
    @ApiResponse(responseCode = "400", description = "Invalid purchase request")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiKeyAddOn> purchaseAddOn(@Valid @RequestBody AddOnPurchaseRequestDTO request) {
        log.info("Purchasing add-on: {} for API key: {}", 
                request.getAddOnPackage(), request.getApiKeyId());

        if (!request.isValidCustomAddOn()) {
            return ResponseEntity.badRequest().build();
        }

        ApiKeyAddOn addOn = addOnService.purchaseAddOn(request);
        return ResponseEntity.ok(addOn);
    }

    @GetMapping("/{apiKeyId}")
    @Operation(
        summary = "Get add-ons for API key",
        description = "Get all add-on packages for a specific API key"
    )
    @ApiResponse(responseCode = "200", description = "Add-ons retrieved successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<ApiKeyAddOn>> getAddOnsForApiKey(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId) {
        
        log.info("Getting add-ons for API key: {}", apiKeyId);
        List<ApiKeyAddOn> addOns = addOnService.getAddOnsForApiKey(apiKeyId);
        return ResponseEntity.ok(addOns);
    }

    @GetMapping("/{apiKeyId}/active")
    @Operation(
        summary = "Get active add-ons for API key",
        description = "Get currently active add-on packages for a specific API key"
    )
    @ApiResponse(responseCode = "200", description = "Active add-ons retrieved successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<ApiKeyAddOn>> getActiveAddOnsForApiKey(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId) {
        
        log.info("Getting active add-ons for API key: {}", apiKeyId);
        List<ApiKeyAddOn> activeAddOns = addOnService.getActiveAddOnsForApiKey(apiKeyId);
        return ResponseEntity.ok(activeAddOns);
    }

    @GetMapping("/{apiKeyId}/recommendations")
    @Operation(
        summary = "Get add-on recommendations",
        description = "Get personalized add-on recommendations based on usage patterns"
    )
    @ApiResponse(responseCode = "200", description = "Recommendations retrieved successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<AddOnRecommendationDTO> getAddOnRecommendations(
            @Parameter(description = "API key ID") @PathVariable String apiKeyId,
            @Parameter(description = "Expected overage requests") @RequestParam(defaultValue = "0") int overageRequests) {
        
        log.info("Getting add-on recommendations for API key: {} with {} overage requests", 
                apiKeyId, overageRequests);
        
        AddOnRecommendationDTO recommendations = addOnService.getAddOnRecommendations(apiKeyId, overageRequests);
        return ResponseEntity.ok(recommendations);
    }

    @PostMapping("/{addOnId}/cancel")
    @Operation(
        summary = "Cancel add-on auto-renewal",
        description = "Cancel automatic renewal for an add-on package"
    )
    @ApiResponse(responseCode = "200", description = "Add-on cancelled successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> cancelAddOn(
            @Parameter(description = "Add-on ID") @PathVariable String addOnId,
            @RequestParam(required = false) String reason) {
        
        log.info("Cancelling add-on: {} - Reason: {}", addOnId, reason);
        addOnService.cancelAddOn(addOnId, reason != null ? reason : "User requested cancellation");
        
        return ResponseEntity.ok(Map.of(
            "message", "Add-on cancelled successfully",
            "addOnId", addOnId
        ));
    }

    @PostMapping("/{addOnId}/renew")
    @Operation(
        summary = "Renew add-on package",
        description = "Manually renew an add-on package for additional months"
    )
    @ApiResponse(responseCode = "200", description = "Add-on renewed successfully")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiKeyAddOn> renewAddOn(
            @Parameter(description = "Add-on ID") @PathVariable String addOnId,
            @Parameter(description = "Duration in months") @RequestParam(defaultValue = "1") int durationMonths) {
        
        log.info("Renewing add-on: {} for {} months", addOnId, durationMonths);
        ApiKeyAddOn renewedAddOn = addOnService.renewAddOn(addOnId, durationMonths);
        return ResponseEntity.ok(renewedAddOn);
    }

    @GetMapping("/expiring")
    @Operation(
        summary = "Get expiring add-ons",
        description = "Get add-ons that are expiring within 7 days (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Expiring add-ons retrieved successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ApiKeyAddOn>> getExpiringAddOns() {
        log.info("Getting expiring add-ons");
        List<ApiKeyAddOn> expiringAddOns = addOnService.getExpiringAddOns();
        return ResponseEntity.ok(expiringAddOns);
    }

    @GetMapping("/nearly-exhausted")
    @Operation(
        summary = "Get nearly exhausted add-ons",
        description = "Get add-ons with less than 10% requests remaining (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Nearly exhausted add-ons retrieved successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ApiKeyAddOn>> getNearlyExhaustedAddOns() {
        log.info("Getting nearly exhausted add-ons");
        List<ApiKeyAddOn> nearlyExhaustedAddOns = addOnService.getNearlyExhaustedAddOns();
        return ResponseEntity.ok(nearlyExhaustedAddOns);
    }

    @PostMapping("/process-auto-renewals")
    @Operation(
        summary = "Process auto-renewals",
        description = "Process automatic renewals for eligible add-ons (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Auto-renewals processed successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> processAutoRenewals() {
        log.info("Processing auto-renewals");
        addOnService.processAutoRenewals();
        
        return ResponseEntity.ok(Map.of(
            "message", "Auto-renewals processed successfully"
        ));
    }

    @PostMapping("/cleanup-expired")
    @Operation(
        summary = "Cleanup expired add-ons",
        description = "Clean up expired add-on packages (Admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Expired add-ons cleaned up successfully")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupExpiredAddOns() {
        log.info("Cleaning up expired add-ons");
        int cleanedUp = addOnService.cleanupExpiredAddOns();
        
        return ResponseEntity.ok(Map.of(
            "message", "Expired add-ons cleaned up successfully",
            "cleanedUpCount", cleanedUp
        ));
    }
}