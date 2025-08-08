package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Finds all API keys associated with a specific user, identified by their String 'id'.
     * @param userFkId The String 'id' of the user (from public.users.id).
     * @return A list of ApiKey entities.
     */
    List<ApiKey> findByUserFkId(String userFkId);

    /**
     * Finds a specific API key by its ID and ensures it belongs to a given user,
     * identified by their String 'id'.
     * @param id The UUID of the API key.
     * @param userFkId The String 'id' of the user (from public.users.id).
     * @return An Optional containing the ApiKey if found and belongs to the user, otherwise empty.
     */
    Optional<ApiKey> findByIdAndUserFkId(UUID id, String userFkId);

    Boolean existsByKeyHash(String keyHash);
    
    /**
     * Check if an API key with the given name already exists for a specific user.
     * @param name The name of the API key to check.
     * @param userFkId The String 'id' of the user.
     * @return true if an API key with this name exists for the user, false otherwise.
     */
    Boolean existsByNameAndUserFkId(String name, String userFkId);
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Get API keys with usage stats in single query
     * Eliminates N+1 queries by fetching all data at once
     */
    @Query(value = """
        SELECT 
            k.id, k.name, k.key_preview, k.registered_domain, k.rate_limit_tier,
            k.is_active, k.created_at, k.last_used_at,
            COALESCE(SUM(u.request_count), 0) as total_requests,
            COALESCE(SUM(u.request_count - COALESCE(u.blocked_requests, 0)), 0) as successful_requests,
            COALESCE(SUM(u.blocked_requests), 0) as blocked_requests,
            MAX(u.last_request_at) as last_request_at
        FROM api_keys k 
        LEFT JOIN api_key_usage_stats u ON k.id = u.api_key_id 
            AND u.window_start >= :fromDate
        WHERE k.user_fk_id = :userId AND k.is_active = true
        GROUP BY k.id, k.name, k.key_preview, k.registered_domain, k.rate_limit_tier,
                 k.is_active, k.created_at, k.last_used_at
        ORDER BY k.created_at DESC
        """, nativeQuery = true)
    List<Object[]> findApiKeysWithUsageByUserFkId(
        @Param("userId") String userId, 
        @Param("fromDate") LocalDateTime fromDate
    );
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Count user API keys efficiently
     */
    @Query("SELECT COUNT(k) FROM ApiKey k WHERE k.userFkId = :userId AND k.isActive = true")
    long countActiveApiKeysByUserFkId(@Param("userId") String userId);
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Find API keys by scope efficiently
     * Uses database query instead of loading all keys into memory
     */
    @Query("SELECT k FROM ApiKey k WHERE k.scopes LIKE %:scope% AND k.isActive = true")
    List<ApiKey> findByScopesContaining(@Param("scope") String scope);
    
    
    /**
     * 🚀 OPTIMIZED: Get API key usage patterns for analytics
     */
    @Query(value = """
        SELECT 
            EXTRACT(HOUR FROM arl.request_timestamp) as peak_hour,
            COUNT(*) / 30.0 as avg_daily_usage,
            arl.request_path as most_used_endpoint,
            COUNT(DISTINCT arl.client_ip) as unique_ips
        FROM api_key_request_logs arl
        WHERE arl.api_key_id = :apiKeyId 
            AND arl.request_timestamp >= CURRENT_DATE - INTERVAL '30 days'
        GROUP BY EXTRACT(HOUR FROM arl.request_timestamp), arl.request_path
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """, nativeQuery = true)
    Object[] getApiKeyUsagePatterns(@Param("apiKeyId") UUID apiKeyId);
    Optional<ApiKey> findByRegisteredDomain(String registeredDomain);
    
    /**
     * Check if a registered domain already exists (for uniqueness validation)
     * @param registeredDomain The domain to check
     * @return true if domain is already registered to an API key
     */
    Boolean existsByRegisteredDomain(String registeredDomain);
    
    /**
     * Find all API keys that allow a specific domain (for future hybrid approach)
     * Searches both registered_domain and allowed_domains fields
     * @param domain The domain to search for
     * @return List of API keys that allow this domain
     */
    @Query("SELECT a FROM ApiKey a WHERE " +
           "LOWER(a.registeredDomain) = LOWER(:domain) OR " +
           "LOWER(a.allowedDomains) LIKE LOWER(CONCAT('%', :domain, '%'))")
    List<ApiKey> findByAnyDomain(@Param("domain") String domain);
    
    /**
     * Find API keys by user with domain information (for management UI)
     * @param userFkId The user ID
     * @return List of API keys with domain info
     */
    @Query("SELECT a FROM ApiKey a WHERE a.userFkId = :userFkId ORDER BY a.createdAt DESC")
    List<ApiKey> findByUserFkIdOrderByCreatedAtDesc(@Param("userFkId") String userFkId);
    
    /**
     * Count API keys for a specific user
     * @param userFkId The user ID
     * @return Number of API keys for the user
     */
    int countByUserFkId(String userFkId);

    // ==================== UNIFIED DASHBOARD METHODS ====================
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Get user dashboard metrics with efficient JOIN
     * Uses index-optimized JOIN strategy and reduces data scanning
     */
    @Query(value = """
        WITH user_api_keys AS (
            SELECT id FROM api_keys 
            WHERE user_fk_id = :userId AND is_active = true
        ),
        current_month_stats AS (
            SELECT 
                SUM(aus.request_count) as current_requests,
                SUM(aus.request_count - COALESCE(aus.blocked_requests, 0)) as current_successful,
                SUM(COALESCE(aus.blocked_requests, 0)) as current_blocked
            FROM api_key_usage_stats aus
            INNER JOIN user_api_keys uak ON aus.api_key_id = uak.id
            WHERE aus.window_start >= DATE_TRUNC('month', CURRENT_DATE)
        ),
        last_month_stats AS (
            SELECT SUM(aus.request_count) as last_requests
            FROM api_key_usage_stats aus
            INNER JOIN user_api_keys uak ON aus.api_key_id = uak.id
            WHERE aus.window_start >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')
                AND aus.window_start < DATE_TRUNC('month', CURRENT_DATE)
        ),
        daily_stats AS (
            SELECT 
                SUM(CASE WHEN DATE(aus.last_request_at) = CURRENT_DATE THEN aus.request_count ELSE 0 END) as today_calls,
                SUM(CASE WHEN DATE(aus.last_request_at) = CURRENT_DATE - INTERVAL '1 day' THEN aus.request_count ELSE 0 END) as yesterday_calls
            FROM api_key_usage_stats aus
            INNER JOIN user_api_keys uak ON aus.api_key_id = uak.id
            WHERE aus.last_request_at >= CURRENT_DATE - INTERVAL '2 days'
        )
        SELECT 
            COALESCE(cms.current_requests, 0) as totalCallsThisMonth,
            COALESCE(lms.last_requests, 0) as totalCallsLastMonth,
            COALESCE(cms.current_successful, 0) as successfulCalls,
            COALESCE(cms.current_blocked, 0) as failedCalls,
            COALESCE(ds.today_calls, 0) as todayCalls,
            COALESCE(ds.yesterday_calls, 0) as yesterdayCalls
        FROM current_month_stats cms
        CROSS JOIN last_month_stats lms
        CROSS JOIN daily_stats ds
        """, nativeQuery = true)
    Object[] getUserDashboardMetrics(@Param("userId") String userId);
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Get API key metrics with efficient aggregation
     * Uses targeted queries instead of large JOINs for better performance
     */
    @Query(value = """
        WITH current_month_data AS (
            SELECT 
                SUM(request_count) as total_requests,
                SUM(request_count - COALESCE(blocked_requests, 0)) as successful_requests,
                SUM(COALESCE(blocked_requests, 0)) as blocked_requests
            FROM api_key_usage_stats
            WHERE api_key_id = :apiKeyId 
                AND window_start >= DATE_TRUNC('month', CURRENT_DATE)
        ),
        daily_data AS (
            SELECT 
                SUM(CASE WHEN DATE(window_start) = CURRENT_DATE THEN request_count ELSE 0 END) as today_calls,
                SUM(CASE WHEN DATE(window_start) = CURRENT_DATE - INTERVAL '1 day' THEN request_count ELSE 0 END) as yesterday_calls
            FROM api_key_usage_stats
            WHERE api_key_id = :apiKeyId 
                AND window_start >= CURRENT_DATE - INTERVAL '2 days'
        )
        SELECT 
            COALESCE(cmd.total_requests, 0) as thisMonthCalls,
            COALESCE(cmd.successful_requests, 0) as successfulCalls,
            COALESCE(cmd.blocked_requests, 0) as failedCalls,
            COALESCE(dd.today_calls, 0) as todayCalls,
            COALESCE(dd.yesterday_calls, 0) as yesterdayCalls,
            0.0 as avgResponseTime
        FROM current_month_data cmd
        CROSS JOIN daily_data dd
        """, nativeQuery = true)
    Object[] getApiKeyDashboardMetrics(@Param("apiKeyId") UUID apiKeyId);
    
    /**
     * ✅ FIXED: Get domain statistics from API keys registered domains
     * Returns: [activeDomainsCount, domainsAddedThisMonth, domainsAddedLastMonth]
     */
    @Query(value = """
        SELECT 
            COUNT(DISTINCT CASE WHEN ak.is_active = true THEN ak.registered_domain END) as activeDomainsCount,
            COUNT(DISTINCT CASE WHEN DATE_TRUNC('month', ak.created_at) = DATE_TRUNC('month', CURRENT_DATE) 
                                THEN ak.registered_domain END) as domainsAddedThisMonth,
            COUNT(DISTINCT CASE WHEN DATE_TRUNC('month', ak.created_at) = DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month') 
                                THEN ak.registered_domain END) as domainsAddedLastMonth
        FROM api_keys ak
        WHERE ak.user_fk_id = :userId
        """, nativeQuery = true)
    Object[] getUserDomainMetrics(@Param("userId") String userId);
    
    /**
     * 🎯 CORRECTED: Get API key metrics from the ACTUAL usage stats table
     * This is the REAL data source that gets updated with actual API calls
     * Returns: [totalRequests, remainingRequests, requestLimit, usagePercentage, isRateLimited, lastRequestAt]
     */
    @Query(value = """
        SELECT 
            COALESCE(SUM(aus.request_count), 0) as totalRequests,
            COALESCE(MIN(aus.remaining_requests), 0) as remainingRequests,
            COALESCE(MAX(aus.request_limit), 0) as requestLimit,
            COALESCE(AVG(CASE WHEN aus.request_limit > 0 THEN (aus.request_count * 100.0 / aus.request_limit) ELSE 0 END), 0) as usagePercentage,
            COALESCE(BOOL_OR(aus.is_rate_limited), false) as isRateLimited,
            MAX(aus.last_request_at) as lastRequestAt
        FROM api_key_usage_stats aus
        WHERE aus.api_key_id = :apiKeyId
            AND aus.window_start >= DATE_TRUNC('month', CURRENT_DATE)
        """, nativeQuery = true)
    Object[] getApiKeyDashboardMetricsFromUsageStats(@Param("apiKeyId") UUID apiKeyId);
}