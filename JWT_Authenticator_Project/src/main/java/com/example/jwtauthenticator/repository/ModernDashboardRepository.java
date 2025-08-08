package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.dto.dashboard.UserDashboardCardsDTO;
import com.example.jwtauthenticator.dto.dashboard.SingleApiKeyDashboardDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Modern Dashboard Repository using Java 21 features
 * Leverages Virtual Threads, Records, and Pattern Matching
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ModernDashboardRepository {

    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * Modern dashboard metrics record using Java 21 Records
     */
    public record DashboardMetrics(
        Long totalCalls30Days,
        Long totalCallsPrevious30Days,
        Integer activeDomains,
        Integer activeDomainsPrevious,
        Integer domainsAddedThisMonth,
        Integer domainsAddedPreviousMonth,
        Long remainingQuota,
        Long remainingQuotaPrevious,
        Long totalQuota,
        Long usedQuota,
        Double successRate,
        Integer totalApiKeys,
        LocalDateTime lastActivity
    ) {}

    /**
     * API Key metrics record
     */
    public record ApiKeyMetrics(
        UUID apiKeyId,
        String apiKeyName,
        String registeredDomain,
        Long requestsToday,
        Long requestsYesterday,
        Long pendingRequests,
        Double usagePercentage,
        LocalDateTime lastUsed,
        String status,
        Long totalCallsMonth,
        Long quotaLimit,
        Double avgResponseTime7Days,
        Double errorRate24h
    ) {}

    /**
     * Get user dashboard metrics using modern Java 21 approach
     * ✅ FIXED: Removed Virtual Threads to prevent connection pool issues
     */
    @Transactional(readOnly = true)
    public CompletableFuture<DashboardMetrics> getUserDashboardMetrics(String userId) {
        // ✅ FIXED: Execute synchronously to avoid connection pool issues with Virtual Threads
        try {
            // Use modern text blocks for complex queries (Java 21)
                var query = entityManager.createNativeQuery("""
                    WITH date_ranges AS (
                        SELECT 
                            CURRENT_DATE - INTERVAL '30 days' as thirty_days_ago,
                            CURRENT_DATE - INTERVAL '60 days' as sixty_days_ago,
                            CURRENT_DATE as today
                    ),
                    user_metrics AS (
                        SELECT 
                            COUNT(CASE WHEN arl.request_timestamp >= dr.thirty_days_ago THEN 1 END) as total_calls_30_days,
                            COUNT(CASE WHEN arl.request_timestamp >= dr.sixty_days_ago 
                                      AND arl.request_timestamp < dr.thirty_days_ago THEN 1 END) as total_calls_previous_30_days,
                            COUNT(DISTINCT CASE WHEN arl.domain IS NOT NULL 
                                               AND arl.request_timestamp >= dr.thirty_days_ago THEN arl.domain END) as active_domains,
                            COUNT(DISTINCT CASE WHEN arl.domain IS NOT NULL 
                                               AND arl.request_timestamp >= dr.sixty_days_ago 
                                               AND arl.request_timestamp < dr.thirty_days_ago THEN arl.domain END) as active_domains_previous,
                            COUNT(DISTINCT CASE WHEN arl.domain IS NOT NULL 
                                               AND EXTRACT(YEAR FROM arl.request_timestamp) = EXTRACT(YEAR FROM CURRENT_DATE)
                                               AND EXTRACT(MONTH FROM arl.request_timestamp) = EXTRACT(MONTH FROM CURRENT_DATE)
                                               THEN arl.domain END) as domains_added_this_month,
                            COUNT(DISTINCT CASE WHEN arl.domain IS NOT NULL 
                                               AND EXTRACT(YEAR FROM arl.request_timestamp) = EXTRACT(YEAR FROM CURRENT_DATE - INTERVAL '1 month')
                                               AND EXTRACT(MONTH FROM arl.request_timestamp) = EXTRACT(MONTH FROM CURRENT_DATE - INTERVAL '1 month')
                                               THEN arl.domain END) as domains_added_previous_month,
                            CASE WHEN COUNT(arl.id) > 0 THEN 
                                ROUND(CAST(COUNT(CASE WHEN arl.success = true THEN 1 END) AS DECIMAL) / CAST(COUNT(arl.id) AS DECIMAL) * 100, 2)
                            ELSE 0 END as success_rate,
                            MAX(arl.request_timestamp) as last_activity
                        FROM api_keys ak
                        LEFT JOIN api_key_request_logs arl ON ak.id = arl.api_key_id 
                            AND arl.request_timestamp >= (SELECT sixty_days_ago FROM date_ranges)
                        CROSS JOIN date_ranges dr
                        WHERE ak.user_fk_id = :userId AND ak.is_active = true
                    ),
                    quota_metrics AS (
                        SELECT 
                            COALESCE(SUM(CASE WHEN amu.quota_limit > 0 
                                         THEN GREATEST(0, amu.quota_limit - amu.total_calls) ELSE 0 END), 0) as remaining_quota,
                            COALESCE(SUM(CASE WHEN amu_prev.quota_limit > 0 
                                         THEN GREATEST(0, amu_prev.quota_limit - amu_prev.total_calls) ELSE 0 END), 0) as remaining_quota_previous,
                            COALESCE(SUM(CASE WHEN amu.quota_limit > 0 THEN amu.quota_limit ELSE 0 END), 0) as total_quota,
                            COALESCE(SUM(CASE WHEN amu.quota_limit > 0 THEN amu.total_calls ELSE 0 END), 0) as used_quota,
                            COUNT(DISTINCT ak.id) as total_api_keys
                        FROM api_keys ak
                        LEFT JOIN api_key_monthly_usage amu ON ak.id = amu.api_key_id 
                            AND amu.month_year = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
                        LEFT JOIN api_key_monthly_usage amu_prev ON ak.id = amu_prev.api_key_id 
                            AND amu_prev.month_year = TO_CHAR(CURRENT_DATE - INTERVAL '1 month', 'YYYY-MM')
                        WHERE ak.user_fk_id = :userId AND ak.is_active = true
                    )
                    SELECT 
                        um.total_calls_30_days,
                        um.total_calls_previous_30_days,
                        um.active_domains,
                        um.active_domains_previous,
                        um.domains_added_this_month,
                        um.domains_added_previous_month,
                        qm.remaining_quota,
                        qm.remaining_quota_previous,
                        qm.total_quota,
                        qm.used_quota,
                        um.success_rate,
                        qm.total_api_keys,
                        um.last_activity
                    FROM user_metrics um
                    CROSS JOIN quota_metrics qm
                    """);

                query.setParameter("userId", userId);
                var result = query.getSingleResult();
                
                if (result instanceof Object[] row) {
                    return CompletableFuture.completedFuture(new DashboardMetrics(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).intValue(),
                        ((Number) row[3]).intValue(),
                        ((Number) row[4]).intValue(),
                        ((Number) row[5]).intValue(),
                        ((Number) row[6]).longValue(),
                        ((Number) row[7]).longValue(),
                        ((Number) row[8]).longValue(),
                        ((Number) row[9]).longValue(),
                        ((Number) row[10]).doubleValue(),
                        ((Number) row[11]).intValue(),
                        row[12] instanceof java.sql.Timestamp ? 
                            ((java.sql.Timestamp) row[12]).toLocalDateTime() : 
                            row[12] instanceof LocalDateTime ? (LocalDateTime) row[12] : LocalDateTime.now()
                    ));
                }
                
                return CompletableFuture.failedFuture(new IllegalStateException("Unexpected query result format"));
                
        } catch (Exception e) {
            log.error("Failed to fetch dashboard metrics for user {}: {}", userId, e.getMessage(), e);
            // Return empty metrics as fallback
            return CompletableFuture.completedFuture(
                new DashboardMetrics(0L, 0L, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0.0, 0, LocalDateTime.now())
            );
        }
    }

    /**
     * Get API key dashboard metrics using modern approach
     * ✅ FIXED: Removed Virtual Threads to prevent connection pool issues
     */
    @Transactional(readOnly = true)
    public CompletableFuture<ApiKeyMetrics> getApiKeyDashboardMetrics(UUID apiKeyId, String userId) {
            try {
                var query = entityManager.createNativeQuery("""
                    WITH api_key_data AS (
                        SELECT 
                            ak.id as api_key_id,
                            ak.name as api_key_name,
                            ak.registered_domain,
                            ak.is_active,
                            ak.rate_limit_tier,
                            amu.total_calls as total_calls_month,
                            amu.quota_limit,
                            amu.successful_calls as successful_calls_month,
                            amu.failed_calls as failed_calls_month
                        FROM api_keys ak
                        LEFT JOIN api_key_monthly_usage amu ON ak.id = amu.api_key_id 
                            AND amu.month_year = TO_CHAR(CURRENT_DATE, 'YYYY-MM')
                        WHERE ak.id = :apiKeyId AND ak.user_fk_id = :userId
                    ),
                    request_metrics AS (
                        SELECT 
                            COUNT(CASE WHEN CAST(arl.request_timestamp AS DATE) = CURRENT_DATE THEN 1 END) as requests_today,
                            COUNT(CASE WHEN CAST(arl.request_timestamp AS DATE) = CURRENT_DATE - INTERVAL '1 day' THEN 1 END) as requests_yesterday,
                            COUNT(CASE WHEN arl.request_timestamp >= CURRENT_TIMESTAMP - INTERVAL '1 hour'
                                      AND (arl.response_status = 429 OR (arl.success = false AND arl.response_status IN (500, 502, 503, 504)))
                                      THEN 1 END) as pending_requests,
                            MAX(arl.request_timestamp) as last_used,
                            AVG(CASE WHEN arl.response_time_ms IS NOT NULL AND arl.request_timestamp >= CURRENT_DATE - INTERVAL '7 days'
                                    THEN arl.response_time_ms END) as avg_response_time_7_days,
                            CASE WHEN COUNT(CASE WHEN arl.request_timestamp >= CURRENT_DATE - INTERVAL '1 day' THEN 1 END) > 0 THEN
                                ROUND(CAST(COUNT(CASE WHEN arl.request_timestamp >= CURRENT_DATE - INTERVAL '1 day' AND arl.success = false THEN 1 END) AS DECIMAL) / 
                                      CAST(COUNT(CASE WHEN arl.request_timestamp >= CURRENT_DATE - INTERVAL '1 day' THEN 1 END) AS DECIMAL) * 100, 2)
                            ELSE 0 END as error_rate_24h
                        FROM api_key_request_logs arl
                        WHERE arl.api_key_id = :apiKeyId AND arl.request_timestamp >= CURRENT_DATE - INTERVAL '7 days'
                    )
                    SELECT 
                        akd.api_key_id,
                        akd.api_key_name,
                        akd.registered_domain,
                        rm.requests_today,
                        rm.requests_yesterday,
                        rm.pending_requests,
                        CASE WHEN akd.quota_limit > 0 THEN 
                            ROUND(CAST(akd.total_calls_month AS DECIMAL) / CAST(akd.quota_limit AS DECIMAL) * 100, 2)
                        ELSE 0 END as usage_percentage,
                        rm.last_used,
                        CASE 
                            WHEN akd.is_active = false THEN 'inactive'
                            WHEN akd.quota_limit > 0 AND akd.total_calls_month >= akd.quota_limit THEN 'quota_exceeded'
                            WHEN rm.last_used >= CURRENT_DATE - INTERVAL '7 days' THEN 'active'
                            ELSE 'dormant'
                        END as status,
                        COALESCE(akd.total_calls_month, 0) as total_calls_month,
                        COALESCE(akd.quota_limit, 0) as quota_limit,
                        COALESCE(rm.avg_response_time_7_days, 0) as avg_response_time_7_days,
                        rm.error_rate_24h
                    FROM api_key_data akd
                    CROSS JOIN request_metrics rm
                    """);

                query.setParameter("apiKeyId", apiKeyId);
                query.setParameter("userId", userId);
                var result = query.getSingleResult();
                
                if (result instanceof Object[] row) {
                    return CompletableFuture.completedFuture(new ApiKeyMetrics(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue(),
                        ((Number) row[5]).longValue(),
                        ((Number) row[6]).doubleValue(),
                        row[7] instanceof java.sql.Timestamp ? 
                            ((java.sql.Timestamp) row[7]).toLocalDateTime() : 
                            (LocalDateTime) row[7],
                        (String) row[8],
                        ((Number) row[9]).longValue(),
                        ((Number) row[10]).longValue(),
                        ((Number) row[11]).doubleValue(),
                        ((Number) row[12]).doubleValue()
                    ));
                }
                
                return CompletableFuture.failedFuture(new IllegalStateException("Unexpected query result format"));
                
        } catch (Exception e) {
            log.error("Failed to fetch API key metrics for {}: {}", apiKeyId, e.getMessage(), e);
            // Return empty metrics as fallback
            return CompletableFuture.completedFuture(new ApiKeyMetrics(apiKeyId, "Unknown", "", 0L, 0L, 0L, 0.0, 
                LocalDateTime.now(), "error", 0L, 0L, 0.0, 0.0));
        }
    }
}