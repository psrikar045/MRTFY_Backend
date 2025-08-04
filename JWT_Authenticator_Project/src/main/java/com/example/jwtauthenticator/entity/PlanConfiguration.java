package com.example.jwtauthenticator.entity;

import com.example.jwtauthenticator.enums.UserPlan;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing plan configurations with limits and features
 */
@Entity
@Table(name = "plan_configurations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanConfiguration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "plan_name", unique = true, nullable = false)
    @Enumerated(EnumType.STRING)
    private UserPlan planName;
    
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @Column(name = "max_domains", nullable = false)
    private Integer maxDomains; // -1 for unlimited
    
    @Column(name = "max_api_keys", nullable = false)
    private Integer maxApiKeys; // -1 for unlimited
    
    @Column(name = "monthly_api_calls", nullable = false)
    private Integer monthlyApiCalls; // -1 for unlimited
    
    @Column(name = "price_per_month", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerMonth;
    
    @Column(name = "features", columnDefinition = "TEXT")
    private String features; // Comma-separated list of features
    
    @Column(name = "grace_percentage", nullable = false)
    private Double gracePercentage = 10.0; // 10% grace period
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Helper methods
    public List<String> getFeaturesAsList() {
        if (features == null || features.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return Arrays.asList(features.split(","));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    public void setFeaturesFromList(List<String> featureList) {
        if (featureList == null || featureList.isEmpty()) {
            this.features = "";
        } else {
            this.features = String.join(",", featureList);
        }
    }
    
    public boolean isUnlimited(String feature) {
        return switch (feature.toLowerCase()) {
            case "domains" -> maxDomains == -1;
            case "api_keys" -> maxApiKeys == -1;
            case "api_calls" -> monthlyApiCalls == -1;
            default -> false;
        };
    }
    
    public boolean hasFeature(String feature) {
        return getFeaturesAsList().contains(feature);
    }
    
    public int getEffectiveLimit(String limitType) {
        return switch (limitType.toLowerCase()) {
            case "domains" -> maxDomains;
            case "api_keys" -> maxApiKeys;
            case "api_calls" -> monthlyApiCalls;
            default -> 0;
        };
    }
    
    public int getGraceLimit(String limitType) {
        int baseLimit = getEffectiveLimit(limitType);
        if (baseLimit == -1) return -1; // Unlimited
        
        return (int) Math.ceil(baseLimit * (1.0 + gracePercentage / 100.0));
    }
    
    public boolean isFreePlan() {
        return planName == UserPlan.FREE;
    }
    
    public boolean isBusinessPlan() {
        return planName == UserPlan.BUSINESS;
    }
    
    public String getFormattedPrice() {
        if (pricePerMonth.compareTo(BigDecimal.ZERO) == 0) {
            return "Free";
        } else if (isBusinessPlan()) {
            return "Custom Pricing";
        } else {
            return "$" + pricePerMonth + "/month";
        }
    }
}