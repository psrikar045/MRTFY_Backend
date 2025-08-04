package com.example.jwtauthenticator.dto;

import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for API key statistics and usage metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyStatisticsDTO {
    
    private UUID apiKeyId;
    private int periodHours;
    private ApiKeyUsageStats currentUsage;
    
    // Aggregate metrics
    private long totalRequests;
    private long totalBlockedRequests;
    private double successRate;
    private double averageRequestsPerHour;
    private int peakRequestsInHour;
    
    // Historical data
    private List<ApiKeyUsageStats> historicalData;
    
    // Metadata
    private LocalDateTime generatedAt;
    
    // Computed properties
    public boolean isCurrentlyRateLimited() {
        return currentUsage != null && currentUsage.getIsRateLimited();
    }
    
    public double getUsagePercentage() {
        return currentUsage != null ? currentUsage.getUsagePercentage() : 0.0;
    }
    
    public String getTierName() {
        return currentUsage != null ? currentUsage.getRateLimitTier().getDisplayName() : "Unknown";
    }
}