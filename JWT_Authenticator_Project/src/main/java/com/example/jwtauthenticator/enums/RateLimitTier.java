package com.example.jwtauthenticator.enums;

import java.time.Duration;

/**
 * Defines rate limiting tiers for API keys.
 * Each tier has different request limits per time window.
 */
public enum RateLimitTier {
    
    BASIC(100, Duration.ofHours(1), "Basic tier - 100 requests per hour"),
    STANDARD(1000, Duration.ofHours(1), "Standard tier - 1,000 requests per hour"),
    PREMIUM(10000, Duration.ofHours(1), "Premium tier - 10,000 requests per hour"),
    ENTERPRISE(50000, Duration.ofHours(1), "Enterprise tier - 50,000 requests per hour"),
    UNLIMITED(-1, Duration.ofHours(1), "Unlimited tier - No rate limits");
    
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
     * Check if this tier is unlimited.
     * @return true if this tier has no rate limits
     */
    public boolean isUnlimited() {
        return requestsPerWindow == -1;
    }
    
    /**
     * Get the window duration in milliseconds for easier calculations.
     * @return window duration in milliseconds
     */
    public long getWindowDurationMillis() {
        return windowDuration.toMillis();
    }
    
    /**
     * Get a default rate limit tier based on user role or API key type.
     * @param isAdminKey true if this is an admin-level API key
     * @return appropriate rate limit tier
     */
    public static RateLimitTier getDefaultTier(boolean isAdminKey) {
        return isAdminKey ? ENTERPRISE : BASIC;
    }
}