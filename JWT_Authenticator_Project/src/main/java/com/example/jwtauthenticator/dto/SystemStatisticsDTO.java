package com.example.jwtauthenticator.dto;

import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.enums.RateLimitTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for system-wide API key statistics and health metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatisticsDTO {
    
    private int periodHours;
    
    // System-wide metrics
    private long totalActiveApiKeys;
    private long totalRequests;
    private long totalBlockedRequests;
    private double averageRequestsPerKey;
    private int currentlyRateLimitedKeys;
    
    // Distribution metrics
    private Map<RateLimitTier, Long> tierDistribution;
    
    // Top performers
    private List<ApiKeyUsageStats> topApiKeysByUsage;
    
    // Metadata
    private LocalDateTime generatedAt;
    
    // Computed properties
    public double getBlockedRequestsPercentage() {
        return totalRequests == 0 ? 0.0 : 
            ((double) totalBlockedRequests / totalRequests) * 100.0;
    }
    
    public double getSuccessRate() {
        return totalRequests == 0 ? 100.0 : 
            ((double) (totalRequests - totalBlockedRequests) / totalRequests) * 100.0;
    }
    
    public String getSystemHealth() {
        double successRate = getSuccessRate();
        if (successRate >= 99.0) return "Excellent";
        if (successRate >= 95.0) return "Good";
        if (successRate >= 90.0) return "Fair";
        return "Poor";
    }
}