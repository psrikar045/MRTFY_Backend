package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.RivoFetchRequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 🚀 RivoFetch Request Log Repository - Dedicated repository for RivoFetch logging
 * 
 * This repository provides comprehensive data access methods for RivoFetch request logs
 * with optimized queries for analytics and monitoring.
 * 
 * Features:
 * - Performance analytics queries
 * - Domain usage analysis
 * - Cache hit rate calculations
 * - Rate limiting statistics
 * - Custom aggregation methods
 * 
 * @author BrandSnap API Team
 * @version 2.0
 * @since Java 21 - Phase 2 Implementation
 */
@Repository
public interface RivoFetchRequestLogRepository extends JpaRepository<RivoFetchRequestLog, String> {

    // ==================== BASIC QUERIES ====================
    
    /**
     * Find logs by API key ID with pagination
     */
    Page<RivoFetchRequestLog> findByRivoFetchApiKeyIdOrderByRivoFetchTimestampDesc(
            UUID apiKeyId, Pageable pageable);
    
    /**
     * Find logs by user ID with pagination
     */
    Page<RivoFetchRequestLog> findByRivoFetchUserIdOrderByRivoFetchTimestampDesc(
            String userId, Pageable pageable);
    
    /**
     * Find logs within time range
     */
    List<RivoFetchRequestLog> findByRivoFetchTimestampBetweenOrderByRivoFetchTimestampDesc(
            LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Find logs by success status within time range
     */
    List<RivoFetchRequestLog> findByRivoFetchSuccessAndRivoFetchTimestampAfter(
            Boolean success, LocalDateTime since);
    
    // ==================== ANALYTICS QUERIES ====================
    
    /**
     * Performance metrics by cache type
     */
    @Query("""
        SELECT 
            r.rivoFetchCacheHitType as cacheHitType,
            COUNT(*) as requestCount,
            AVG(r.rivoFetchTotalDurationMs) as avgTotalDuration,
            AVG(r.rivoFetchExternalApiDurationMs) as avgExternalApiDuration,
            ROUND(COUNT(CASE WHEN r.rivoFetchSuccess = true THEN 1 END) * 100.0 / COUNT(*), 2) as successRate
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchTimestamp >= :since
        GROUP BY r.rivoFetchCacheHitType
        ORDER BY requestCount DESC
        """)
    List<Object[]> getPerformanceMetricsByCacheType(@Param("since") LocalDateTime since);
    
    /**
     * Domain usage analysis
     */
    @Query("""
        SELECT 
            r.rivoFetchUrlDomain as domain,
            COUNT(*) as totalRequests,
            COUNT(DISTINCT r.rivoFetchUserId) as uniqueUsers,
            COUNT(DISTINCT r.rivoFetchApiKeyId) as uniqueApiKeys,
            AVG(r.rivoFetchTotalDurationMs) as avgDuration,
            MAX(r.rivoFetchTimestamp) as lastRequest
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchTimestamp >= :since
        GROUP BY r.rivoFetchUrlDomain
        ORDER BY totalRequests DESC
        """)
    List<Object[]> getDomainUsageAnalysis(@Param("since") LocalDateTime since);
    
    /**
     * Controller usage patterns
     */
    @Query("""
        SELECT 
            r.rivoFetchControllerName as controllerName,
            r.rivoFetchMethodName as methodName,
            r.rivoFetchRequestSource as requestSource,
            COUNT(*) as requestCount,
            AVG(r.rivoFetchTotalDurationMs) as avgDuration
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchTimestamp >= :since
        GROUP BY r.rivoFetchControllerName, r.rivoFetchMethodName, r.rivoFetchRequestSource
        ORDER BY requestCount DESC
        """)
    List<Object[]> getControllerUsagePatterns(@Param("since") LocalDateTime since);
    
    /**
     * Rate limit tier analysis
     */
    @Query("""
        SELECT 
            r.rivoFetchRateLimitTier as tier,
            COUNT(*) as totalRequests,
            COUNT(CASE WHEN r.rivoFetchSuccess = true THEN 1 END) as successfulRequests,
            AVG(r.rivoFetchTotalDurationMs) as avgDuration,
            COUNT(DISTINCT r.rivoFetchApiKeyId) as uniqueApiKeys
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchTimestamp >= :since
        GROUP BY r.rivoFetchRateLimitTier
        ORDER BY totalRequests DESC
        """)
    List<Object[]> getRateLimitTierAnalysis(@Param("since") LocalDateTime since);
    
    // ==================== COUNTING QUERIES ====================
    
    /**
     * Count requests by API key within time range
     */
    long countByRivoFetchApiKeyIdAndRivoFetchTimestampAfter(UUID apiKeyId, LocalDateTime since);
    
    /**
     * Count successful requests by API key within time range
     */
    long countByRivoFetchApiKeyIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            UUID apiKeyId, Boolean success, LocalDateTime since);
    
    /**
     * Count requests by user ID within time range
     */
    long countByRivoFetchUserIdAndRivoFetchTimestampAfter(String userId, LocalDateTime since);
    
    /**
     * Count successful requests by user ID within time range
     */
    long countByRivoFetchUserIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            String userId, Boolean success, LocalDateTime since);
    
    /**
     * Count requests by cache hit type within time range
     */
    long countByRivoFetchCacheHitTypeAndRivoFetchTimestampAfter(String cacheHitType, LocalDateTime since);
    
    /**
     * Count requests by domain within time range
     */
    long countByRivoFetchUrlDomainAndRivoFetchTimestampAfter(String domain, LocalDateTime since);
    
    /**
     * Count all requests after timestamp
     */
    long countByRivoFetchTimestampAfter(LocalDateTime since);
    
    /**
     * Count requests by success status after timestamp
     */
    long countByRivoFetchSuccessAndRivoFetchTimestampAfter(Boolean success, LocalDateTime since);
    
    /**
     * Count ALL requests by success status (for entire application history)
     */
    long countByRivoFetchSuccess(Boolean success);
    
    // ==================== AGGREGATION QUERIES ====================
    
    /**
     * Get cache hit rate for API key
     */
    @Query("""
        SELECT 
            COUNT(CASE WHEN r.rivoFetchCacheHitType != 'MISS' THEN 1 END) * 100.0 / COUNT(*) as cacheHitRate
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchApiKeyId = :apiKeyId 
        AND r.rivoFetchTimestamp >= :since
        """)
    Optional<Double> getCacheHitRateByApiKey(@Param("apiKeyId") UUID apiKeyId, @Param("since") LocalDateTime since);
    
    /**
     * Get average response time by API key
     */
    @Query("""
        SELECT AVG(r.rivoFetchTotalDurationMs) 
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchApiKeyId = :apiKeyId 
        AND r.rivoFetchTimestamp >= :since
        """)
    Optional<Double> getAverageResponseTimeByApiKey(@Param("apiKeyId") UUID apiKeyId, @Param("since") LocalDateTime since);
    
    /**
     * Get success rate by API key
     */
    @Query("""
        SELECT 
            COUNT(CASE WHEN r.rivoFetchSuccess = true THEN 1 END) * 100.0 / COUNT(*) as successRate
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchApiKeyId = :apiKeyId 
        AND r.rivoFetchTimestamp >= :since
        """)
    Optional<Double> getSuccessRateByApiKey(@Param("apiKeyId") UUID apiKeyId, @Param("since") LocalDateTime since);
    
    // ==================== TOP QUERIES ====================
    
    /**
     * Get top domains by request count
     */
    @Query("""
        SELECT r.rivoFetchUrlDomain, COUNT(*) as requestCount
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchTimestamp >= :since
        GROUP BY r.rivoFetchUrlDomain
        ORDER BY requestCount DESC
        """)
    List<Object[]> getTopDomainsByRequestCount(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Get slowest requests
     */
    @Query("""
        SELECT r
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchTimestamp >= :since
        ORDER BY r.rivoFetchTotalDurationMs DESC
        """)
    List<RivoFetchRequestLog> getSlowestRequests(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Get most active API keys
     */
    @Query("""
        SELECT r.rivoFetchApiKeyId, COUNT(*) as requestCount
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchTimestamp >= :since
        GROUP BY r.rivoFetchApiKeyId
        ORDER BY requestCount DESC
        """)
    List<Object[]> getMostActiveApiKeys(@Param("since") LocalDateTime since, Pageable pageable);
    
    // ==================== ERROR ANALYSIS ====================
    
    /**
     * Get error distribution
     */
    @Query("""
        SELECT 
            r.rivoFetchResponseStatus as statusCode,
            COUNT(*) as errorCount,
            r.rivoFetchErrorMessage as errorMessage
        FROM RivoFetchRequestLog r 
        WHERE r.rivoFetchSuccess = false 
        AND r.rivoFetchTimestamp >= :since
        GROUP BY r.rivoFetchResponseStatus, r.rivoFetchErrorMessage
        ORDER BY errorCount DESC
        """)
    List<Object[]> getErrorDistribution(@Param("since") LocalDateTime since);
    
    /**
     * Get failed requests by API key
     */
    List<RivoFetchRequestLog> findByRivoFetchApiKeyIdAndRivoFetchSuccessAndRivoFetchTimestampAfterOrderByRivoFetchTimestampDesc(
            UUID apiKeyId, Boolean success, LocalDateTime since);
    
    // ==================== PERFORMANCE QUERIES ====================
    
    /**
     * Get performance percentiles
     */
    @Query(value = """
        SELECT 
            PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY rivo_fetch_total_duration_ms) as p50,
            PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY rivo_fetch_total_duration_ms) as p90,
            PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY rivo_fetch_total_duration_ms) as p95,
            PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY rivo_fetch_total_duration_ms) as p99
        FROM rivo_fetch_request_logs 
        WHERE rivo_fetch_timestamp >= :since
        """, nativeQuery = true)
    List<Object[]> getPerformancePercentiles(@Param("since") LocalDateTime since);
    
    /**
     * Get hourly request distribution
     */
    @Query(value = """
        SELECT 
            DATE_TRUNC('hour', rivo_fetch_timestamp) as hour_bucket,
            COUNT(*) as request_count,
            COUNT(CASE WHEN rivo_fetch_success = true THEN 1 END) as successful_count,
            AVG(rivo_fetch_total_duration_ms) as avg_duration
        FROM rivo_fetch_request_logs 
        WHERE rivo_fetch_timestamp >= :since
        GROUP BY DATE_TRUNC('hour', rivo_fetch_timestamp)
        ORDER BY hour_bucket DESC
        """, nativeQuery = true)
    List<Object[]> getHourlyRequestDistribution(@Param("since") LocalDateTime since);
    
    // ==================== CLEANUP QUERIES ====================
    
    /**
     * Delete old logs (for maintenance)
     */
    void deleteByRivoFetchTimestampBefore(LocalDateTime cutoffTime);
    
    /**
     * Count logs older than cutoff time
     */
    long countByRivoFetchTimestampBefore(LocalDateTime cutoffTime);
    
    // ==================== CUSTOM FINDER METHODS ====================
    
    /**
     * Find by universal request UUID (for cross-system correlation)
     */
    Optional<RivoFetchRequestLog> findByUniversalRequestUuid(UUID uuid);
    
    /**
     * Find recent logs for API key
     */
    default List<RivoFetchRequestLog> findRecentLogsByApiKey(UUID apiKeyId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return findByRivoFetchApiKeyIdAndRivoFetchTimestampAfterOrderByRivoFetchTimestampDesc(
                apiKeyId, since);
    }
   
  boolean existsByRivoFetchLogId(String rivoFetchLogId);
    /**
     * Find recent logs for API key with timestamp after
     */
    List<RivoFetchRequestLog> findByRivoFetchApiKeyIdAndRivoFetchTimestampAfterOrderByRivoFetchTimestampDesc(
            UUID apiKeyId, LocalDateTime since);
}