package com.example.jwtauthenticator.entity;

import lombok.Getter;

/**
 * Enum defining add-on packages for additional API requests
 * Professional add-on system similar to Stripe, AWS, GitHub
 */
@Getter
public enum AddOnPackage {
    ADDON_SMALL("Small Add-on", 100, 5.0, "Additional 100 requests per day"),
    ADDON_MEDIUM("Medium Add-on", 500, 20.0, "Additional 500 requests per day"),
    ADDON_LARGE("Large Add-on", 2000, 75.0, "Additional 2000 requests per day"),
    ADDON_ENTERPRISE("Enterprise Add-on", 10000, 300.0, "Additional 10000 requests per day"),
    ADDON_CUSTOM("Custom Add-on", 0, 0.0, "Custom request limit"); // For custom negotiations

    private final String displayName;
    private final int additionalRequests;
    private final double monthlyPrice; // Monthly price in USD
    private final String description;

    AddOnPackage(String displayName, int additionalRequests, double monthlyPrice, String description) {
        this.displayName = displayName;
        this.additionalRequests = additionalRequests;
        this.monthlyPrice = monthlyPrice;
        this.description = description;
    }

    /**
     * Get cost per request for this add-on
     */
    public double getCostPerRequest() {
        if (additionalRequests == 0) return 0.0;
        return monthlyPrice / additionalRequests;
    }

    /**
     * Check if this is a custom add-on
     */
    public boolean isCustom() {
        return this == ADDON_CUSTOM;
    }

    /**
     * Get recommended add-on based on overage amount
     */
    public static AddOnPackage getRecommendedAddOn(int overageRequests) {
        if (overageRequests <= 100) return ADDON_SMALL;
        if (overageRequests <= 500) return ADDON_MEDIUM;
        if (overageRequests <= 2000) return ADDON_LARGE;
        if (overageRequests <= 10000) return ADDON_ENTERPRISE;
        return ADDON_CUSTOM;
    }
}