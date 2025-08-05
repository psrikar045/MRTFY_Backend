package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity for user_dashboard_summary materialized view
 * Read-only entity for dashboard performance
 */
@Entity
@Table(name = "user_dashboard_summary")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboardSummaryView {

    @Id
    @Column(name = "user_fk_id")
    private String userFkId;

    @Column(name = "total_calls_30_days")
    private Long totalCalls30Days;

    @Column(name = "total_calls_previous_30_days")
    private Long totalCallsPrevious30Days;

    @Column(name = "active_domains_count")
    private Integer activeDomainsCount;

    @Column(name = "active_domains_previous_count")
    private Integer activeDomainsPreviousCount;

    @Column(name = "domains_added_this_month")
    private Integer domainsAddedThisMonth;

    @Column(name = "domains_added_previous_month")
    private Integer domainsAddedPreviousMonth;

    @Column(name = "remaining_quota_total")
    private Long remainingQuotaTotal;

    @Column(name = "remaining_quota_previous_month")
    private Long remainingQuotaPreviousMonth;

    @Column(name = "total_api_keys")
    private Integer totalApiKeys;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    @Column(name = "success_rate_30_days")
    private Double successRate30Days;
}