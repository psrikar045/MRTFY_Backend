package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.UserDashboardSummaryView;
import org.springframework.data.jpa.repository.JpaRepository;
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
    Optional<UserDashboardSummaryView> findByUserFkId(String userFkId);

    /**
     * Check if summary exists for user (for cache validation)
     */
    boolean existsByUserFkId(String userFkId);

    /**
     * Get raw dashboard data with custom query if needed
     */
    @Query(value = """
        SELECT 
            user_fk_id,
            total_calls_30_days,
            total_calls_previous_30_days,
            active_domains_count,
            active_domains_previous_count,
            domains_added_this_month,
            domains_added_previous_month,
            remaining_quota_total,
            remaining_quota_previous_month,
            total_api_keys,
            last_activity,
            success_rate_30_days
        FROM user_dashboard_summary 
        WHERE user_fk_id = :userFkId
        """, nativeQuery = true)
    Optional<Object[]> findRawDashboardData(@Param("userFkId") String userFkId);

}