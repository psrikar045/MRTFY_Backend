package com.example.jwtauthenticator.dto;

import com.example.jwtauthenticator.entity.AddOnPackage;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for purchasing add-on packages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOnPurchaseRequestDTO {

    @NotBlank(message = "API key ID is required")
    private String apiKeyId;

    @NotNull(message = "Add-on package is required")
    private AddOnPackage addOnPackage;

    @Min(value = 1, message = "Duration must be at least 1 month")
    private int durationMonths = 1;

    private boolean autoRenew = false;

    private String reason; // Why purchasing this add-on

    // For custom add-ons
    @Min(value = 1, message = "Custom requests must be at least 1")
    private Integer customRequests;

    @Min(value = 0, message = "Custom price cannot be negative")
    private Double customPrice;

    // Validation for custom add-ons
    public boolean isValidCustomAddOn() {
        if (addOnPackage == AddOnPackage.ADDON_CUSTOM) {
            return customRequests != null && customRequests > 0 && 
                   customPrice != null && customPrice >= 0;
        }
        return true;
    }
}