package com.example.jwtauthenticator.enums;

import lombok.Getter;

/**
 * Enum representing different user subscription plans
 * Based on the pricing structure from the image requirements
 */
@Getter
public enum UserPlan {
    FREE("Free", 1, 1, 100, 0.0, "Basic functionality with community support"),
    PRO("Pro", 5, 5, 1000, 25.0, "Enhanced features with priority support"),
    BUSINESS("Business", -1, -1, -1, 0.0, "Unlimited access with dedicated support"); // Custom pricing

    private final String displayName;
    private final int maxDomains;        // -1 = unlimited
    private final int maxApiKeys;        // -1 = unlimited  
    private final int monthlyApiCalls;   // -1 = unlimited
    private final double monthlyPrice;   // 0.0 = custom pricing for business
    private final String description;

    UserPlan(String displayName, int maxDomains, int maxApiKeys, 
             int monthlyApiCalls, double monthlyPrice, String description) {
        this.displayName = displayName;
        this.maxDomains = maxDomains;
        this.maxApiKeys = maxApiKeys;
        this.monthlyApiCalls = monthlyApiCalls;
        this.monthlyPrice = monthlyPrice;
        this.description = description;
    }

    /**
     * Check if the plan has unlimited access for a specific feature
     */
    public boolean isUnlimited(String feature) {
        return switch (feature.toLowerCase()) {
            case "domains" -> maxDomains == -1;
            case "api_keys" -> maxApiKeys == -1;
            case "api_calls" -> monthlyApiCalls == -1;
            default -> false;
        };
    }

    /**
     * Get the effective limit for a feature (considering unlimited as Integer.MAX_VALUE)
     */
    public int getEffectiveLimit(String feature) {
        int limit = switch (feature.toLowerCase()) {
            case "domains" -> maxDomains;
            case "api_keys" -> maxApiKeys;
            case "api_calls" -> monthlyApiCalls;
            default -> 0;
        };
        
        return limit == -1 ? Integer.MAX_VALUE : limit;
    }

    /**
     * Calculate grace limit (10% additional allowance)
     */
    public int getGraceLimit(String feature) {
        int baseLimit = switch (feature.toLowerCase()) {
            case "domains" -> maxDomains;
            case "api_keys" -> maxApiKeys;
            case "api_calls" -> monthlyApiCalls;
            default -> 0;
        };
        
        if (baseLimit == -1) return -1; // Unlimited
        return (int) Math.ceil(baseLimit * 1.10); // 10% grace period
    }

    /**
     * Get formatted price string
     */
    public String getFormattedPrice() {
        if (monthlyPrice == 0.0) {
            return this == FREE ? "Free" : "Custom Pricing";
        }
        return "$" + String.format("%.0f", monthlyPrice) + "/month";
    }

    /**
     * Check if this plan can be upgraded to target plan
     */
    public boolean canUpgradeTo(UserPlan targetPlan) {
        return this.ordinal() < targetPlan.ordinal();
    }

    /**
     * Check if this plan can be downgraded to target plan
     */
    public boolean canDowngradeTo(UserPlan targetPlan) {
        return this.ordinal() > targetPlan.ordinal();
    }

    /**
     * Get the next higher plan (for upgrade suggestions)
     */
    public UserPlan getNextPlan() {
        UserPlan[] plans = UserPlan.values();
        int currentIndex = this.ordinal();
        
        if (currentIndex < plans.length - 1) {
            return plans[currentIndex + 1];
        }
        
        return this; // Already at highest plan
    }

    /**
     * Get default plan for new users
     */
    public static UserPlan getDefaultPlan() {
        return FREE;
    }

    /**
     * Check if plan supports specific features
     */
    public boolean supportsFeature(String feature) {
        return switch (feature.toLowerCase()) {
            case "domain_health" -> true; // All plans
            case "basic_analytics" -> true; // All plans
            case "community_support" -> true; // All plans
            case "domain_insights" -> this != FREE;
            case "priority_support" -> this != FREE;
            case "ai_brand_summaries" -> this == BUSINESS;
            case "custom_usage_sla" -> this == BUSINESS;
            case "dedicated_account_manager" -> this == BUSINESS;
            default -> false;
        };
    }

    /**
     * Get all features supported by this plan
     */
    public String[] getSupportedFeatures() {
        return switch (this) {
            case FREE -> new String[]{
                "domain_health", "basic_analytics", "community_support"
            };
            case PRO -> new String[]{
                "domain_health", "basic_analytics", "community_support",
                "domain_insights", "priority_support"
            };
            case BUSINESS -> new String[]{
                "domain_health", "basic_analytics", "community_support",
                "domain_insights", "priority_support", "ai_brand_summaries",
                "custom_usage_sla", "dedicated_account_manager"
            };
        };
    }
}