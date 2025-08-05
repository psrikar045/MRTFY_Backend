package com.example.jwtauthenticator.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Single API Key Dashboard
 * Contains metrics specific to one API key
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dashboard metrics for a single API key")
public class SingleApiKeyDashboardDTO {

    @JsonProperty("apiKeyId")
    @Schema(description = "API Key unique identifier")
    private UUID apiKeyId;

    @JsonProperty("apiKeyName")
    @Schema(description = "API Key name", example = "Production API Key")
    private String apiKeyName;

    @JsonProperty("registeredDomain")
    @Schema(description = "Registered domain for this API key", example = "example.com")
    private String registeredDomain;

    @JsonProperty("requestsToday")
    @Schema(description = "Number of requests made today", example = "245")
    private Long requestsToday;

    @JsonProperty("requestsYesterday")
    @Schema(description = "Number of requests made yesterday for comparison", example = "198")
    private Long requestsYesterday;

    @JsonProperty("todayVsYesterdayChange")
    @Schema(description = "Percentage change from yesterday", example = "23.7")
    private Double todayVsYesterdayChange;

    @JsonProperty("pendingRequests")
    @Schema(description = "Number of pending/failed requests that might retry", example = "3")
    private Long pendingRequests;

    @JsonProperty("usagePercentage")
    @Schema(description = "Monthly quota usage percentage", example = "67.5")
    private Double usagePercentage;

    @JsonProperty("lastUsed")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Timestamp of last API call")
    private LocalDateTime lastUsed;

    @JsonProperty("status")
    @Schema(description = "API key status", 
            allowableValues = {"active", "quota_exceeded", "inactive", "dormant"}, 
            example = "active")
    private String status;

    @JsonProperty("monthlyMetrics")
    @Schema(description = "Monthly usage metrics")
    private MonthlyMetricsDTO monthlyMetrics;

    @JsonProperty("performanceMetrics")
    @Schema(description = "Performance and reliability metrics")
    private PerformanceMetricsDTO performanceMetrics;

    @JsonProperty("rateLimitInfo")
    @Schema(description = "Rate limiting information")
    private RateLimitInfoDTO rateLimitInfo;

    @JsonProperty("lastUpdated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this data was last calculated")
    private LocalDateTime lastUpdated;

    /**
     * Monthly usage metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Monthly usage statistics")
    public static class MonthlyMetricsDTO {
        
        @JsonProperty("totalCalls")
        @Schema(description = "Total calls this month", example = "6750")
        private Long totalCalls;

        @JsonProperty("successfulCalls")
        @Schema(description = "Successful calls this month", example = "6580")
        private Long successfulCalls;

        @JsonProperty("failedCalls")
        @Schema(description = "Failed calls this month", example = "170")
        private Long failedCalls;

        @JsonProperty("quotaLimit")
        @Schema(description = "Monthly quota limit", example = "10000")
        private Long quotaLimit;

        @JsonProperty("remainingQuota")
        @Schema(description = "Remaining quota this month", example = "3250")
        private Long remainingQuota;

        @JsonProperty("successRate")
        @Schema(description = "Success rate percentage", example = "97.5")
        private Double successRate;

        @JsonProperty("estimatedDaysToQuotaExhaustion")
        @Schema(description = "Estimated days until quota is exhausted", example = "12")
        private Integer estimatedDaysToQuotaExhaustion;

        @JsonProperty("quotaStatus")
        @Schema(description = "Quota health status", 
                allowableValues = {"healthy", "warning", "critical", "exceeded", "unlimited"}, 
                example = "healthy")
        private String quotaStatus;

        /**
         * Calculate usage percentage
         */
        public Double getUsagePercentage() {
            if (quotaLimit != null && quotaLimit > 0 && totalCalls != null) {
                return (totalCalls.doubleValue() / quotaLimit.doubleValue()) * 100.0;
            }
            return 0.0;
        }
    }

    /**
     * Performance and reliability metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Performance and reliability metrics")
    public static class PerformanceMetricsDTO {
        
        @JsonProperty("averageResponseTime")
        @Schema(description = "Average response time in milliseconds (last 7 days)", example = "245.5")
        private Double averageResponseTime;

        @JsonProperty("errorRate24h")
        @Schema(description = "Error rate percentage in last 24 hours", example = "2.1")
        private Double errorRate24h;

        @JsonProperty("uptime")
        @Schema(description = "Uptime percentage (last 7 days)", example = "99.8")
        private Double uptime;

        @JsonProperty("performanceStatus")
        @Schema(description = "Overall performance status", 
                allowableValues = {"excellent", "good", "fair", "poor"}, 
                example = "good")
        private String performanceStatus;

        @JsonProperty("lastError")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Timestamp of last error")
        private LocalDateTime lastError;

        @JsonProperty("consecutiveSuccessfulCalls")
        @Schema(description = "Number of consecutive successful calls", example = "1250")
        private Long consecutiveSuccessfulCalls;
    }

    /**
     * Rate limiting information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Rate limiting information")
    public static class RateLimitInfoDTO {
        
        @JsonProperty("tier")
        @Schema(description = "Rate limit tier", example = "PRO_TIER")
        private String tier;

        @JsonProperty("currentWindowRequests")
        @Schema(description = "Requests in current rate limit window", example = "45")
        private Integer currentWindowRequests;

        @JsonProperty("windowLimit")
        @Schema(description = "Request limit per window", example = "1000")
        private Integer windowLimit;

        @JsonProperty("windowResetTime")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "When the current rate limit window resets")
        private LocalDateTime windowResetTime;

        @JsonProperty("rateLimitStatus")
        @Schema(description = "Rate limit status", 
                allowableValues = {"normal", "approaching_limit", "rate_limited", "unlimited"}, 
                example = "normal")
        private String rateLimitStatus;

        @JsonProperty("rateLimitUtilization")
        @Schema(description = "Rate limit utilization percentage", example = "4.5")
        private Double rateLimitUtilization;
    }

    /**
     * Helper method to determine overall health status
     */
    public String getOverallHealthStatus() {
        if ("quota_exceeded".equals(status) || "inactive".equals(status)) {
            return "critical";
        }
        
        if (performanceMetrics != null && performanceMetrics.getErrorRate24h() != null) {
            if (performanceMetrics.getErrorRate24h() > 10.0) {
                return "critical";
            } else if (performanceMetrics.getErrorRate24h() > 5.0) {
                return "warning";
            }
        }
        
        if (monthlyMetrics != null && monthlyMetrics.getUsagePercentage() != null) {
            if (monthlyMetrics.getUsagePercentage() > 90.0) {
                return "warning";
            }
        }
        
        return "healthy";
    }

    /**
     * Helper method to get usage percentage from monthly metrics
     */
    public Double getUsagePercentageFromMonthly() {
        if (monthlyMetrics != null && monthlyMetrics.getQuotaLimit() != null && 
            monthlyMetrics.getQuotaLimit() > 0 && monthlyMetrics.getTotalCalls() != null) {
            return (monthlyMetrics.getTotalCalls().doubleValue() / monthlyMetrics.getQuotaLimit().doubleValue()) * 100.0;
        }
        return 0.0;
    }
}