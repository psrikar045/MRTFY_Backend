package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to track add-on packages purchased for API keys
 * Supports multiple add-ons per API key for flexible scaling
 */
@Entity
@Table(name = "api_key_addons", indexes = {
    @Index(name = "idx_api_key_addon", columnList = "apiKeyId"),
    @Index(name = "idx_addon_active", columnList = "isActive"),
    @Index(name = "idx_addon_expires", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyAddOn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "api_key_id", nullable = false)
    private String apiKeyId;

    @Column(name = "user_fk_id", nullable = false)
    private String userFkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "addon_package", nullable = false)
    private AddOnPackage addOnPackage;

    @Column(name = "additional_requests", nullable = false)
    private Integer additionalRequests;

    @Column(name = "monthly_price", nullable = false)
    private Double monthlyPrice;

    // Activation and expiration
    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Usage tracking
    @Column(name = "requests_used")
    private Integer requestsUsed = 0;

    @Column(name = "requests_remaining")
    private Integer requestsRemaining;

    // Billing information
    @Column(name = "billing_cycle_start")
    private LocalDateTime billingCycleStart;

    @Column(name = "billing_cycle_end")
    private LocalDateTime billingCycleEnd;

    @Column(name = "auto_renew")
    private Boolean autoRenew = false;

    // Metadata
    @Column(name = "purchase_reason", columnDefinition = "TEXT")
    private String purchaseReason; // Why was this add-on purchased

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (requestsRemaining == null) {
            requestsRemaining = additionalRequests;
        }
        if (billingCycleStart == null) {
            billingCycleStart = LocalDateTime.now();
        }
        if (billingCycleEnd == null) {
            billingCycleEnd = billingCycleStart.plusMonths(1);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if add-on is currently valid and active
     */
    public boolean isCurrentlyActive() {
        LocalDateTime now = LocalDateTime.now();
        return isActive && 
               now.isAfter(activatedAt) && 
               now.isBefore(expiresAt) &&
               requestsRemaining > 0;
    }

    /**
     * Use requests from this add-on
     */
    public boolean useRequests(int requestCount) {
        if (!isCurrentlyActive() || requestsRemaining < requestCount) {
            return false;
        }
        
        requestsUsed += requestCount;
        requestsRemaining -= requestCount;
        
        if (requestsRemaining <= 0) {
            isActive = false;
        }
        
        return true;
    }

    /**
     * Get usage percentage
     */
    public double getUsagePercentage() {
        if (additionalRequests == 0) return 0.0;
        return (double) requestsUsed / additionalRequests * 100.0;
    }

    /**
     * Check if add-on is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if add-on is nearly exhausted (< 10% remaining)
     */
    public boolean isNearlyExhausted() {
        return getUsagePercentage() > 90.0;
    }

    /**
     * Get days until expiration
     */
    public long getDaysUntilExpiration() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiresAt);
    }
}