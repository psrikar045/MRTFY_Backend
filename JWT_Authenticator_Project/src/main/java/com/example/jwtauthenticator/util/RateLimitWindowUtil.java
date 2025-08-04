package com.example.jwtauthenticator.util;

import com.example.jwtauthenticator.enums.RateLimitTier;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Utility class for calculating rate limit time windows.
 * Provides consistent window calculation logic across all rate limiting services.
 */
public class RateLimitWindowUtil {

    /**
     * Get the appropriate window start time based on the rate limit tier.
     * 
     * @param now The current time
     * @param tier The rate limit tier
     * @return The window start time
     */
    public static LocalDateTime getWindowStart(LocalDateTime now, RateLimitTier tier) {
        // All tiers now use monthly windows for consistency
        return now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    }

    /**
     * Get the window end time based on the window start and tier.
     * 
     * @param windowStart The window start time
     * @param tier The rate limit tier
     * @return The window end time
     */
    public static LocalDateTime getWindowEnd(LocalDateTime windowStart, RateLimitTier tier) {
        return windowStart.plusSeconds(tier.getWindowSizeSeconds());
    }

    /**
     * Check if a given time is within the current window for a tier.
     * 
     * @param time The time to check
     * @param now The current time
     * @param tier The rate limit tier
     * @return true if the time is within the current window
     */
    public static boolean isWithinCurrentWindow(LocalDateTime time, LocalDateTime now, RateLimitTier tier) {
        LocalDateTime windowStart = getWindowStart(now, tier);
        LocalDateTime windowEnd = getWindowEnd(windowStart, tier);
        
        return !time.isBefore(windowStart) && time.isBefore(windowEnd);
    }

    /**
     * Get a human-readable description of the window type for a tier.
     * 
     * @param tier The rate limit tier
     * @return A description of the window type
     */
    public static String getWindowDescription(RateLimitTier tier) {
        return switch (tier) {
            case FREE_TIER -> "Monthly (100 requests)";
            case PRO_TIER -> "Monthly (1,000 requests)";
            case BUSINESS_TIER -> "Monthly (Unlimited)";
        };
    }
}