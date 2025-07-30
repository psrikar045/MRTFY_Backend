package com.example.jwtauthenticator.entity;

import lombok.Getter;

/**
 * Enum defining rate limit tiers with day-based limits and add-on support
 */
@Getter
public enum RateLimitTier {
    BASIC("Basic", 100, 86400, "100 requests per day", 0.0),
    STANDARD("Standard", 500, 86400, "500 requests per day", 10.0),
    PREMIUM("Premium", 2000, 86400, "2000 requests per day", 50.0),
    ENTERPRISE("Enterprise", 10000, 86400, "10000 requests per day", 200.0),
    UNLIMITED("Unlimited", Integer.MAX_VALUE, 86400, "Unlimited requests", 500.0);

    private final String displayName;
    private final int requestLimit;
    private final int windowSizeSeconds; // Time window in seconds (86400 = 1 day)
    private final String description;
    private final double monthlyPrice; // Monthly price in USD

    RateLimitTier(String displayName, int requestLimit, int windowSizeSeconds, String description, double monthlyPrice) {
        this.displayName = displayName;
        this.requestLimit = requestLimit;
        this.windowSizeSeconds = windowSizeSeconds;
        this.description = description;
        this.monthlyPrice = monthlyPrice;
    }

    /**
     * Get requests per hour for this tier
     */
    public double getRequestsPerHour() {
        if (this == UNLIMITED) {
            return Double.MAX_VALUE;
        }
        return (double) requestLimit / (windowSizeSeconds / 3600.0);
    }

    /**
     * Get requests per minute for this tier
     */
    public double getRequestsPerMinute() {
        if (this == UNLIMITED) {
            return Double.MAX_VALUE;
        }
        return (double) requestLimit / (windowSizeSeconds / 60.0);
    }

    /**
     * Get requests per second for this tier
     */
    public double getRequestsPerSecond() {
        if (this == UNLIMITED) {
            return Double.MAX_VALUE;
        }
        return (double) requestLimit / windowSizeSeconds;
    }

    /**
     * Check if this tier allows unlimited requests
     */
    public boolean isUnlimited() {
        return this == UNLIMITED;
    }
}