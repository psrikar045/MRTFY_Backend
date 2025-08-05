package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKeyDashboardSummaryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for accessing api_key_dashboard_summary materialized view
 * This provides fast access to pre-calculated API key metrics
 */
@Repository
public interface ApiKeyDashboardSummaryRepository extends JpaRepository<ApiKeyDashboardSummaryView, UUID> {

    /**
     * Find dashboard summary for a specific API key
     */
    Optional<ApiKeyDashboardSummaryView> findByApiKeyId(UUID apiKeyId);

    /**
     * Find dashboard summary for API key belonging to specific user
     */
    Optional<ApiKeyDashboardSummaryView> findByApiKeyIdAndUserFkId(UUID apiKeyId, String userFkId);

    /**
     * Find all API key summaries for a user
     */
    List<ApiKeyDashboardSummaryView> findByUserFkIdOrderByLastUsedDesc(String userFkId);

    /**
     * Find API keys by status for a user
     */
    List<ApiKeyDashboardSummaryView> findByUserFkIdAndStatusOrderByLastUsedDesc(String userFkId, String status);

    /**
     * Count API keys by status for a user
     */
    long countByUserFkIdAndStatus(String userFkId, String status);

    /**
     * Get raw dashboard data with custom query if needed
     */
    @Query(value = """
        SELECT 
            api_key_id,
            user_fk_id,
            api_key_name,
            registered_domain,
            requests_today,
            requests_yesterday,
            pending_requests,
            usage_percentage,
            last_used,
            status,
            total_calls_month,
            quota_limit,
            successful_calls_month,
            failed_calls_month,
            rate_limit_tier,
            avg_response_time_7_days,
            error_rate_24h
        FROM api_key_dashboard_summary 
        WHERE api_key_id = :apiKeyId AND user_fk_id = :userFkId
        """, nativeQuery = true)
    Optional<Object[]> findRawDashboardData(@Param("apiKeyId") UUID apiKeyId, @Param("userFkId") String userFkId);

}