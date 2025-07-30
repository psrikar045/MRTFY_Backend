package com.example.jwtauthenticator.dto;

import com.example.jwtauthenticator.entity.AddOnPackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for add-on package recommendations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddOnRecommendationDTO {

    private String apiKeyId;
    private int overageRequests;
    private AddOnPackage recommendedPackage;
    private List<AddOnPackage> alternativePackages;
    private double estimatedMonthlySavings;
    private LocalDateTime generatedAt;

    // Computed properties
    public String getRecommendationReason() {
        if (recommendedPackage == null) return "No recommendation available";
        
        return String.format("Based on %d overage requests, %s provides the best value at $%.2f/month (%.3f per request)",
                overageRequests, 
                recommendedPackage.getDisplayName(),
                recommendedPackage.getMonthlyPrice(),
                recommendedPackage.getCostPerRequest());
    }

    public boolean hasSavings() {
        return estimatedMonthlySavings > 0;
    }
}