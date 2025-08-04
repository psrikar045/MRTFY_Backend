package com.example.jwtauthenticator.enums;

import java.time.Duration;

/**
 * Defines rate limiting tiers for API keys aligned with user plans.
 * All tiers use monthly windows (30 days) for consistent billing cycles.
 * 
 * Tier Types:
 * - FREE_TIER: 100 requests per month
 * - PRO_TIER: 1,000 requests per month
 * - BUSINESS_TIER: Unlimited requests
 */
public enum RateLimitTier {
    
    // FREE Plan: 100 monthly calls
    FREE_TIER(100, Duration.ofDays(30), "Free tier - 100 requests per month"),
    
    // PRO Plan: 1,000 monthly calls
    PRO_TIER(1000, Duration.ofDays(30), "Pro tier - 1,000 requests per month"),
    
    // BUSINESS Plan: Unlimited calls
    BUSINESS_TIER(-1, Duration.ofDays(30), "Business tier - Unlimited requests");
    
    private final int requestsPerWindow;
    private final Duration windowDuration;
    private final String description;
    
    RateLimitTier(int requestsPerWindow, Duration windowDuration, String description) {
        this.requestsPerWindow = requestsPerWindow;
        this.windowDuration = windowDuration;
        this.description = description;
    }
    
    public int getRequestsPerWindow() {
        return requestsPerWindow;
    }
    
    public Duration getWindowDuration() {
        return windowDuration;
    }
    
    public String getDescription() {
        return description;
    }
    
 
    
    /**
     * Get the window duration in milliseconds for easier calculations.
     * @return window duration in milliseconds
     */
    public long getWindowDurationMillis() {
        return windowDuration.toMillis();
    }
    
    /**
     * Get requests per hour for this tier (calculated from monthly limit)
     * @return requests per hour as a double
     */
    public double getRequestsPerHour() {
        if (this == BUSINESS_TIER) {
            return Double.MAX_VALUE;
        }
        // Monthly limit divided by hours in a month (30 days * 24 hours)
        return (double) requestsPerWindow / (30.0 * 24.0);
    }

    /**
     * Get requests per minute for this tier (calculated from monthly limit)
     * @return requests per minute as a double
     */
    public double getRequestsPerMinute() {
        if (this == BUSINESS_TIER) {
            return Double.MAX_VALUE;
        }
        // Monthly limit divided by minutes in a month (30 days * 24 hours * 60 minutes)
        return (double) requestsPerWindow / (30.0 * 24.0 * 60.0);
    }

    /**
     * Get requests per second for this tier (calculated from monthly limit)
     * @return requests per second as a double
     */
    public double getRequestsPerSecond() {
        if (this == BUSINESS_TIER) {
            return Double.MAX_VALUE;
        }
        // Monthly limit divided by seconds in a month (30 days * 24 hours * 60 minutes * 60 seconds)
        return (double) requestsPerWindow / (30.0 * 24.0 * 60.0 * 60.0);
    }

    /**
     * Get the monthly request limit for this tier
     * @return monthly request limit
     */
    public int getRequestsPerMonth() {
        return requestsPerWindow;
    }

    /**
     * Get display name for this tier
     * @return human-readable display name
     */
    public String getDisplayName() {
        return switch (this) {
            case FREE_TIER -> "Free";
            case PRO_TIER -> "Pro";
            case BUSINESS_TIER -> "Business";
        };
    }

    /**
     * Get request limit for this tier (alias for getRequestsPerWindow)
     * @return request limit
     */
    public int getRequestLimit() {
        return requestsPerWindow;
    }

    /**
     * Get window size in seconds
     * @return window size in seconds
     */
    public long getWindowSizeSeconds() {
        return windowDuration.getSeconds();
    }

    /**
     * Get monthly price for this tier
     * @return monthly price in USD
     */
    public double getMonthlyPrice() {
        return switch (this) {
            case FREE_TIER -> 0.0;
            case PRO_TIER -> 25.0;
            case BUSINESS_TIER -> 0.0; // Custom pricing
        };
    }

    /**
     * Get a default rate limit tier based on user role or API key type.
     * @param isAdminKey true if this is an admin-level API key
     * @return appropriate rate limit tier
     */
    public static RateLimitTier getDefaultTier(boolean isAdminKey) {
        return isAdminKey ? BUSINESS_TIER : FREE_TIER;
    }
    
    /**
     * Get rate limit tier based on user plan
     * @param userPlan the user's subscription plan
     * @return corresponding rate limit tier
     */
    public static RateLimitTier fromUserPlan(com.example.jwtauthenticator.enums.UserPlan userPlan) {
        return switch (userPlan) {
            case FREE -> FREE_TIER;
            case PRO -> PRO_TIER;
            case BUSINESS -> BUSINESS_TIER;
        };
    }
    
    /**
     * Check if this tier supports unlimited requests
     * @return true if unlimited
     */
    public boolean supportsUnlimitedRequests() {
        return this == BUSINESS_TIER;
    }
    
    /**
     * Check if this tier is unlimited (alias for supportsUnlimitedRequests)
     * @return true if unlimited
     */
    public boolean isUnlimited() {
        return supportsUnlimitedRequests();
    }
    
    /**
     * Get the corresponding user plan for this tier
     * @return user plan or null if legacy tier
     */
    public com.example.jwtauthenticator.enums.UserPlan getUserPlan() {
        return switch (this) {
            case FREE_TIER -> com.example.jwtauthenticator.enums.UserPlan.FREE;
            case PRO_TIER -> com.example.jwtauthenticator.enums.UserPlan.PRO;
            case BUSINESS_TIER -> com.example.jwtauthenticator.enums.UserPlan.BUSINESS;
            default -> null; // Legacy tiers
        };
    }
    
    /**
     * Check if this is a plan-based tier (not legacy)
     * @return true if plan-based
     */
    public boolean isPlanBasedTier() {
        return this == FREE_TIER || this == PRO_TIER || this == BUSINESS_TIER;
    }
}