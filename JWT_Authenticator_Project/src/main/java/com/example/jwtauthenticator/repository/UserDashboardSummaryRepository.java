package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.UserDashboardSummaryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for accessing user_dashboard_summary materialized view
 * This provides fast access to pre-calculated dashboard metrics
 */
@Repository
public interface UserDashboardSummaryRepository extends JpaRepository<UserDashboardSummaryView, String> {

    /**
     * Find dashboard summary for a specific user
     */
    Optional<UserDashboardSummaryView> findByUserId(String userId);

    /**
     * Check if summary exists for user (for cache validation)
     */
    boolean existsByUserId(String userId);

    /**
     * Get raw dashboard data with custom query if needed
     */
    @Query(value = """
        SELECT 
            user_id,
            total_calls_30_days,
            total_calls_previous_30_days,
            active_domains,
            success_rate,
            remaining_quota,
            total_calls_current_month,
            total_quota_current_month,
            quota_usage_percentage,
            calls_percentage_change,
            activity_status,
            quota_status,
            total_api_keys,
            last_activity,
            last_updated
        FROM user_dashboard_summary 
        WHERE user_id = :userFkId
        """, nativeQuery = true)
    Optional<Object[]> findRawDashboardData(@Param("userFkId") String userFkId);

    /**
     * Refresh the materialized view to get latest data
     */
    @Modifying
    @Query(value = "REFRESH MATERIALIZED VIEW user_dashboard_summary", nativeQuery = true)
    void refreshMaterializedView();

    /**
     * Check when the materialized view was last updated
     */
    @Query(value = "SELECT last_updated FROM user_dashboard_summary WHERE user_id = :userFkId", nativeQuery = true)
    Optional<Object> getLastUpdatedTime(@Param("userFkId") String userFkId);

}