package com.example.jwtauthenticator.dto;

import com.example.jwtauthenticator.enums.RateLimitTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 🚀 PERFORMANCE OPTIMIZED DTO for API Key with Usage Statistics
 * 
 * This DTO eliminates N+1 queries by fetching all required data in a single query.
 * Used by the optimized repository method to return API keys with their usage stats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyWithUsageDTO {
    
    private UUID id;
    private String name;
    private String keyPreview;
    private String registeredDomain;
    private RateLimitTier rateLimitTier;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    
    // Usage statistics (aggregated from api_key_usage_stats)
    private Long totalRequests;
    private Long successfulRequests;
    private Long blockedRequests;
    private LocalDateTime lastRequestAt;
    
    /**
     * Factory method to create DTO from raw query result
     * Used with native queries that return Object[] arrays
     */
    public static ApiKeyWithUsageDTO fromQueryResult(Object[] result) {
        if (result == null || result.length < 12) {
            throw new IllegalArgumentException("Invalid query result array");
        }
        
        return ApiKeyWithUsageDTO.builder()
            .id(UUID.fromString(result[0].toString()))
            .name((String) result[1])
            .keyPreview((String) result[2])
            .registeredDomain((String) result[3])
            .rateLimitTier(result[4] != null ? RateLimitTier.valueOf(result[4].toString()) : RateLimitTier.FREE_TIER)
            .isActive((Boolean) result[5])
            .createdAt((LocalDateTime) result[6])
            .lastUsedAt((LocalDateTime) result[7])
            .totalRequests(result[8] != null ? ((Number) result[8]).longValue() : 0L)
            .successfulRequests(result[9] != null ? ((Number) result[9]).longValue() : 0L)
            .blockedRequests(result[10] != null ? ((Number) result[10]).longValue() : 0L)
            .lastRequestAt((LocalDateTime) result[11])
            .build();
    }
    
    /**
     * Calculate usage percentage for the current period
     */
    public double getUsagePercentage() {
        if (rateLimitTier == null || rateLimitTier.isUnlimited()) {
            return 0.0;
        }
        
        int monthlyLimit = rateLimitTier.getRequestsPerMonth();
        if (monthlyLimit <= 0) {
            return 0.0;
        }
        
        return (totalRequests != null ? totalRequests.doubleValue() : 0.0) / monthlyLimit * 100.0;
    }
    
    /**
     * Get remaining requests for the current period
     */
    public long getRemainingRequests() {
        if (rateLimitTier == null || rateLimitTier.isUnlimited()) {
            return Long.MAX_VALUE;
        }
        
        int monthlyLimit = rateLimitTier.getRequestsPerMonth();
        long used = totalRequests != null ? totalRequests : 0L;
        
        return Math.max(0, monthlyLimit - used);
    }
    
    /**
     * Check if the API key is approaching its limit (>80% usage)
     */
    public boolean isApproachingLimit() {
        return !rateLimitTier.isUnlimited() && getUsagePercentage() > 80.0;
    }
    
    /**
     * Check if the API key has exceeded its limit
     */
    public boolean isLimitExceeded() {
        return !rateLimitTier.isUnlimited() && getUsagePercentage() >= 100.0;
    }
    
    /**
     * Get success rate as percentage
     */
    public double getSuccessRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        
        long successful = successfulRequests != null ? successfulRequests : 0L;
        return (successful * 100.0) / totalRequests;
    }
}