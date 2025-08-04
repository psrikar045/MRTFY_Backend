package com.example.jwtauthenticator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for advanced usage analytics and trends
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageAnalyticsDTO {
    
    private UUID apiKeyId;
    private int periodDays;
    
    // Daily usage data
    private Map<LocalDateTime, Long> dailyRequestCounts;
    
    // Usage patterns
    private Map<Integer, Long> hourlyUsagePattern; // Hour (0-23) -> Request count
    
    // Peak usage information
    private LocalDateTime peakUsageDay;
    private long peakUsageCount;
    
    // Trend analysis
    private double usageTrend; // Positive = increasing, Negative = decreasing
    
    // Metadata
    private int totalDaysAnalyzed;
    private LocalDateTime generatedAt;
    
    // Computed properties
    public String getTrendDescription() {
        if (usageTrend > 1.0) return "Strongly Increasing";
        if (usageTrend > 0.1) return "Increasing";
        if (usageTrend > -0.1) return "Stable";
        if (usageTrend > -1.0) return "Decreasing";
        return "Strongly Decreasing";
    }
    
    public int getPeakUsageHour() {
        return hourlyUsagePattern.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(0);
    }
}