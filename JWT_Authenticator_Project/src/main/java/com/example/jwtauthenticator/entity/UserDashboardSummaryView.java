package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for user_dashboard_summary materialized view
 * ✅ UPDATED: Matches new view structure based on usage_stats table
 * Read-only entity for dashboard performance
 */
@Entity
@Table(name = "user_dashboard_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboardSummaryView {

    @Id
    @Column(name = "user_id")
    private String userId;

    // API Calls Metrics
    @Column(name = "total_calls_30_days")
    private Long totalCalls30Days;

    @Column(name = "total_calls_previous_30_days")
    private Long totalCallsPrevious30Days;

    @Column(name = "calls_percentage_change")
    private Double callsPercentageChange;

    // Active Domains Metrics
    @Column(name = "active_domains")
    private Long activeDomains;

    @Column(name = "active_domains_previous")
    private Long activeDomainsPrevious;

    @Column(name = "domains_percentage_change")
    private Double domainsPercentageChange;

    // Domains Added Metrics
    @Column(name = "domains_added_this_month")
    private Long domainsAddedThisMonth;

    @Column(name = "domains_added_previous_month")
    private Long domainsAddedPreviousMonth;

    @Column(name = "domains_added_percentage_change")
    private Double domainsAddedPercentageChange;

    // Quota Metrics
    @Column(name = "remaining_quota")
    private Long remainingQuota;

    @Column(name = "remaining_quota_previous")
    private Long remainingQuotaPrevious;

    @Column(name = "quota_percentage_change")
    private Double quotaPercentageChange;

    @Column(name = "total_calls_current_month")
    private Long totalCallsCurrentMonth;

    @Column(name = "total_quota_current_month")
    private Long totalQuotaCurrentMonth;

    @Column(name = "quota_usage_percentage")
    private Double quotaUsagePercentage;

    // Other Metrics
    @Column(name = "success_rate")
    private Double successRate;

    @Column(name = "total_api_keys")
    private Long totalApiKeys;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    // Status Indicators
    @Column(name = "activity_status")
    private String activityStatus;

    @Column(name = "quota_status")
    private String quotaStatus;

    // Metadata
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "data_source")
    private String dataSource;
}