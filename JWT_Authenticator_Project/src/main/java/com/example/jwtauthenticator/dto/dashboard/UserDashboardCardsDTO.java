package com.example.jwtauthenticator.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for User Dashboard Cards - Main dashboard overview
 * Contains all 4 cards with percentage changes and trends
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User dashboard cards with metrics and percentage changes")
public class UserDashboardCardsDTO {

    @JsonProperty("totalApiCalls")
    @Schema(description = "Total API calls in last 30 days with trend analysis")
    private ApiCallsCardDTO totalApiCalls;

    @JsonProperty("activeDomains")
    @Schema(description = "Active domains (projects) with growth metrics")
    private ActiveDomainsCardDTO activeDomains;

    @JsonProperty("domainsAdded")
    @Schema(description = "New domains added this month")
    private DomainsAddedCardDTO domainsAdded;

    @JsonProperty("remainingQuota")
    @Schema(description = "Remaining API quota across all keys")
    private RemainingQuotaCardDTO remainingQuota;

    @JsonProperty("lastUpdated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this data was last calculated")
    private LocalDateTime lastUpdated;

    @JsonProperty("successRate")
    @Schema(description = "Overall success rate percentage for last 30 days")
    private Double successRate;

    @JsonProperty("totalApiKeys")
    @Schema(description = "Total number of active API keys")
    private Integer totalApiKeys;

    /**
     * Total API Calls Card (Last 30 days)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "API calls metrics with rolling 30-day comparison")
    public static class ApiCallsCardDTO {
        
        @JsonProperty("totalCalls")
        @Schema(description = "Total API calls in last 30 days", example = "15420")
        private Long totalCalls;

        @JsonProperty("percentageChange")
        @Schema(description = "Percentage change compared to previous 30 days", example = "12.5")
        private Double percentageChange;

        @JsonProperty("trend")
        @Schema(description = "Trend direction", allowableValues = {"up", "down", "stable"}, example = "up")
        private String trend;

        @JsonProperty("previousPeriodCalls")
        @Schema(description = "API calls in previous 30-day period for comparison", example = "13750")
        private Long previousPeriodCalls;

        @JsonProperty("dailyAverage")
        @Schema(description = "Daily average calls in current period", example = "514")
        private Double dailyAverage;

        @JsonProperty("status")
        @Schema(description = "Status indicator", allowableValues = {"healthy", "warning", "critical"}, example = "healthy")
        private String status;
    }

    /**
     * Active Domains Card (Projects)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Active domains metrics with growth analysis")
    public static class ActiveDomainsCardDTO {
        
        @JsonProperty("activeDomains")
        @Schema(description = "Number of unique active domains in last 30 days", example = "8")
        private Integer activeDomains;

        @JsonProperty("percentageChange")
        @Schema(description = "Percentage change compared to previous 30 days", example = "25.0")
        private Double percentageChange;

        @JsonProperty("trend")
        @Schema(description = "Trend direction", allowableValues = {"up", "down", "stable"}, example = "up")
        private String trend;

        @JsonProperty("previousPeriodDomains")
        @Schema(description = "Active domains in previous 30-day period", example = "6")
        private Integer previousPeriodDomains;

        @JsonProperty("newDomainsThisPeriod")
        @Schema(description = "New domains that appeared in current period", example = "3")
        private Integer newDomainsThisPeriod;

        @JsonProperty("status")
        @Schema(description = "Growth status", allowableValues = {"growing", "stable", "declining"}, example = "growing")
        private String status;
    }

    /**
     * Domains Added This Month Card
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "New domains added this month with comparison")
    public static class DomainsAddedCardDTO {
        
        @JsonProperty("domainsAdded")
        @Schema(description = "Number of new unique domains added this month", example = "3")
        private Integer domainsAdded;

        @JsonProperty("percentageChange")
        @Schema(description = "Percentage change compared to previous month", example = "50.0")
        private Double percentageChange;

        @JsonProperty("trend")
        @Schema(description = "Trend direction", allowableValues = {"up", "down", "stable"}, example = "up")
        private String trend;

        @JsonProperty("previousMonthAdded")
        @Schema(description = "Domains added in previous month", example = "2")
        private Integer previousMonthAdded;

        @JsonProperty("monthlyTarget")
        @Schema(description = "Monthly target for new domains (if set)", example = "5")
        private Integer monthlyTarget;

        @JsonProperty("status")
        @Schema(description = "Progress status", allowableValues = {"on_track", "behind", "ahead"}, example = "on_track")
        private String status;
    }

    /**
     * Remaining Quota Card
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Remaining API quota across all keys")
    public static class RemainingQuotaCardDTO {
        
        @JsonProperty("remainingQuota")
        @Schema(description = "Total remaining API calls across all keys", example = "84580")
        private Long remainingQuota;

        @JsonProperty("percentageChange")
        @Schema(description = "Percentage change in remaining quota compared to previous month", example = "-15.2")
        private Double percentageChange;

        @JsonProperty("trend")
        @Schema(description = "Trend direction", allowableValues = {"up", "down", "stable"}, example = "down")
        private String trend;

        @JsonProperty("totalQuota")
        @Schema(description = "Total quota limit across all keys", example = "100000")
        private Long totalQuota;

        @JsonProperty("usedQuota")
        @Schema(description = "Total used quota across all keys", example = "15420")
        private Long usedQuota;

        @JsonProperty("usagePercentage")
        @Schema(description = "Overall quota usage percentage", example = "15.42")
        private Double usagePercentage;

        @JsonProperty("estimatedDaysRemaining")
        @Schema(description = "Estimated days until quota exhaustion based on current usage", example = "45")
        private Integer estimatedDaysRemaining;

        @JsonProperty("status")
        @Schema(description = "Quota status", allowableValues = {"healthy", "warning", "critical", "unlimited"}, example = "healthy")
        private String status;
    }
}