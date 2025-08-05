package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for api_key_dashboard_summary materialized view
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

    @Column(name = "requests_today")
    private Long requestsToday;

    @Column(name = "requests_yesterday")
    private Long requestsYesterday;

    @Column(name = "pending_requests")
    private Long pendingRequests;

    @Column(name = "usage_percentage")
    private Double usagePercentage;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @Column(name = "status")
    private String status;

    @Column(name = "total_calls_month")
    private Long totalCallsMonth;

    @Column(name = "quota_limit")
    private Long quotaLimit;

    @Column(name = "successful_calls_month")
    private Long successfulCallsMonth;

    @Column(name = "failed_calls_month")
    private Long failedCallsMonth;

    @Column(name = "rate_limit_tier")
    private String rateLimitTier;

    @Column(name = "avg_response_time_7_days")
    private Double avgResponseTime7Days;

    @Column(name = "error_rate_24h")
    private Double errorRate24h;
}