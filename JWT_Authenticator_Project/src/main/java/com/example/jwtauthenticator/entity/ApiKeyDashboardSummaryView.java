package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for api_key_dashboard_summary materialized view
 * ✅ UPDATED: Matches new view structure based on usage_stats table
 * Read-only entity for dashboard performance
 */
@Entity
@Table(name = "api_key_dashboard_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDashboardSummaryView {

    @Id
    @Column(name = "api_key_id")
    private UUID apiKeyId;

    @Column(name = "user_fk_id")
    private String userFkId;

    @Column(name = "api_key_name")
    private String apiKeyName;

    @Column(name = "registered_domain")
    private String registeredDomain;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "rate_limit_tier")
    private String rateLimitTier;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Daily Metrics
    @Column(name = "requests_today")
    private Long requestsToday;

    @Column(name = "requests_yesterday")
    private Long requestsYesterday;

    @Column(name = "daily_change_percentage")
    private Double dailyChangePercentage;

    // Weekly Metrics
    @Column(name = "requests_7_days")
    private Long requests7Days;

    @Column(name = "successful_requests_7_days")
    private Long successfulRequests7Days;

    @Column(name = "failed_requests_7_days")
    private Long failedRequests7Days;

    @Column(name = "success_rate_7_days")
    private Double successRate7Days;

    // Monthly Metrics
    @Column(name = "requests_30_days")
    private Long requests30Days;

    @Column(name = "successful_requests_30_days")
    private Long successfulRequests30Days;

    // Performance Metrics
    @Column(name = "avg_response_time_7_days")
    private Double avgResponseTime7Days;

    @Column(name = "min_response_time_7_days")
    private Double minResponseTime7Days;

    @Column(name = "max_response_time_7_days")
    private Double maxResponseTime7Days;

    @Column(name = "error_rate_24h")
    private Double errorRate24h;

    // Rate Limiting
    @Column(name = "pending_requests")
    private Long pendingRequests;

    // Quota Information
    @Column(name = "quota_limit")
    private Long quotaLimit;

    @Column(name = "remaining_quota")
    private Long remainingQuota;

    @Column(name = "usage_percentage")
    private Double usagePercentage;

    @Column(name = "quota_status")
    private String quotaStatus;

    @Column(name = "total_calls_current_month")
    private Long totalCallsCurrentMonth;

    @Column(name = "successful_calls_current_month")
    private Long successfulCallsCurrentMonth;

    @Column(name = "failed_calls_current_month")
    private Long failedCallsCurrentMonth;

    // Usage Comparison
    @Column(name = "total_calls_previous_month")
    private Long totalCallsPreviousMonth;

    @Column(name = "remaining_quota_previous")
    private Long remainingQuotaPrevious;

    @Column(name = "monthly_change_percentage")
    private Double monthlyChangePercentage;

    // Activity Information
    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @Column(name = "unique_domains_accessed")
    private Long uniqueDomainsAccessed;

    // Overall Status
    @Column(name = "status")
    private String status;

    @Column(name = "performance_status")
    private String performanceStatus;

    // Metadata
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "data_source")
    private String dataSource;
}