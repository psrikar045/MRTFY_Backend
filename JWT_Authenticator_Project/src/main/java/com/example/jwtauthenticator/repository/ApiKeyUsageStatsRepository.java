package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.enums.RateLimitTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyUsageStatsRepository extends JpaRepository<ApiKeyUsageStats, UUID> {

    /**
     * Find current usage stats for an API key within the current time window
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.apiKeyId = :apiKeyId " +
           "AND aus.windowStart <= :currentTime AND aus.windowEnd > :currentTime")
    Optional<ApiKeyUsageStats> findCurrentUsageStats(@Param("apiKeyId") UUID apiKeyId, 
                                                     @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find all usage stats for an API key (for historical analysis)
     */
    List<ApiKeyUsageStats> findByApiKeyIdOrderByWindowStartDesc(UUID apiKeyId);

    /**
     * Find usage stats for a user across all API keys
     */
    List<ApiKeyUsageStats> findByUserFkIdOrderByWindowStartDesc(String userFkId);

    /**
     * Find usage stats by rate limit tier
     */
    List<ApiKeyUsageStats> findByRateLimitTier(RateLimitTier rateLimitTier);

    /**
     * Find currently rate-limited API keys
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.isRateLimited = true " +
           "AND aus.rateLimitResetAt > :currentTime")
    List<ApiKeyUsageStats> findCurrentlyRateLimitedKeys(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Get total requests for an API key in the last N hours
     */
    @Query("SELECT COALESCE(SUM(aus.requestCount), 0) FROM ApiKeyUsageStats aus " +
           "WHERE aus.apiKeyId = :apiKeyId AND aus.windowStart >= :since")
    Long getTotalRequestsSince(@Param("apiKeyId") UUID apiKeyId, @Param("since") LocalDateTime since);

    /**
     * Get usage statistics for a time period
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.apiKeyId = :apiKeyId " +
           "AND aus.windowStart >= :startTime AND aus.windowEnd <= :endTime " +
           "ORDER BY aus.windowStart ASC")
    List<ApiKeyUsageStats> getUsageStatsBetween(@Param("apiKeyId") UUID apiKeyId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * Get top API keys by usage
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.windowStart >= :since " +
           "ORDER BY aus.totalRequestsLifetime DESC")
    List<ApiKeyUsageStats> getTopApiKeysByUsage(@Param("since") LocalDateTime since);

    /**
     * Get system-wide statistics
     */
    @Query("SELECT " +
           "COUNT(DISTINCT aus.apiKeyId), " +
           "COALESCE(SUM(aus.requestCount), 0), " +
           "COALESCE(SUM(aus.blockedRequests), 0), " +
           "COALESCE(AVG(CAST(aus.requestCount AS double)), 0.0) " +
           "FROM ApiKeyUsageStats aus WHERE aus.windowStart >= :since")
    Object[] getSystemWideStats(@Param("since") LocalDateTime since);

    /**
     * Clean up old usage stats (for maintenance)
     */
    @Modifying
    @Query("DELETE FROM ApiKeyUsageStats aus WHERE aus.windowEnd < :cutoffTime")
    void deleteOldUsageStats(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find peak usage periods
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.requestCount >= :threshold " +
           "ORDER BY aus.requestCount DESC")
    List<ApiKeyUsageStats> findPeakUsagePeriods(@Param("threshold") Integer threshold);
    
    /**
     * Find usage stats by API key ID and window start (for current window lookup)
     */
    Optional<ApiKeyUsageStats> findByApiKeyIdAndWindowStart(UUID apiKeyId, LocalDateTime windowStart);
    
    /**
     * Find usage stats by API key ID within date range
     */
    List<ApiKeyUsageStats> findByApiKeyIdAndWindowStartBetween(UUID apiKeyId, LocalDateTime from, LocalDateTime to);
    
    /**
     * Find usage stats by API key ID within date range (paginated and ordered)
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.apiKeyId = :apiKeyId " +
           "AND aus.windowStart >= :from AND aus.windowStart <= :to " +
           "ORDER BY aus.windowStart DESC")
    org.springframework.data.domain.Page<ApiKeyUsageStats> findByApiKeyIdAndWindowStartBetweenOrderByWindowStartDesc(
        @Param("apiKeyId") UUID apiKeyId, 
        @Param("from") LocalDateTime from, 
        @Param("to") LocalDateTime to, 
        org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find usage stats by user ID within date range
     */
    List<ApiKeyUsageStats> findByUserFkIdAndWindowStartBetween(String userFkId, LocalDateTime from, LocalDateTime to);
    
    /**
     * Get total requests in a period for an API key
     */
    @Query("SELECT COALESCE(SUM(aus.requestCount), 0) FROM ApiKeyUsageStats aus " +
           "WHERE aus.apiKeyId = :apiKeyId AND aus.windowStart >= :from AND aus.windowStart <= :to")
    Long getTotalRequestsInPeriod(@Param("apiKeyId") UUID apiKeyId, 
                                 @Param("from") LocalDateTime from, 
                                 @Param("to") LocalDateTime to);
    
    /**
     * Find API keys approaching their rate limits
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.windowStart = :windowStart " +
           "AND aus.rateLimitTier != com.example.jwtauthenticator.enums.RateLimitTier.BUSINESS_TIER " +
           "AND aus.requestLimit > 0 " +
           "AND (CAST(aus.requestCount AS double) / CAST(aus.requestLimit AS double)) >= :threshold")
    List<ApiKeyUsageStats> findApiKeysApproachingLimits(@Param("windowStart") LocalDateTime windowStart, 
                                                        @Param("threshold") double threshold);
    
    /**
     * Delete old statistics records
     */
    @Modifying
    @Query("DELETE FROM ApiKeyUsageStats aus WHERE aus.windowEnd < :cutoff")
    int deleteStatsOlderThan(@Param("cutoff") LocalDateTime cutoff);
    
//     /**
//      * Delete old usage statistics (alias for deleteStatsOlderThan)
//      */
//     @Modifying
//     @Query("DELETE FROM ApiKeyUsageStats aus WHERE aus.windowEnd < :cutoffTime")
//     void deleteOldUsageStats(@Param("cutoffTime") LocalDateTime cutoffTime);
    
//     /**
//      * Get system-wide statistics
//      */
//     @Query("SELECT COUNT(DISTINCT aus.apiKeyId), " +
//            "COALESCE(SUM(aus.requestCount), 0), " +
//            "COALESCE(SUM(aus.blockedRequests), 0), " +
//            "COALESCE(AVG(aus.requestCount), 0.0) " +
//            "FROM ApiKeyUsageStats aus WHERE aus.windowStart >= :since")
//     Object[] getSystemWideStats(@Param("since") LocalDateTime since);
    
//     /**
//      * Find currently rate-limited API keys
//      */
//     @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.isRateLimited = true " +
//            "AND (aus.rateLimitResetAt IS NULL OR aus.rateLimitResetAt > :currentTime)")
//     List<ApiKeyUsageStats> findCurrentlyRateLimitedKeys(@Param("currentTime") LocalDateTime currentTime);
    
//     /**
//      * Get top API keys by usage
//      */
//     @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.windowStart >= :since " +
//            "ORDER BY aus.requestCount DESC")
//     List<ApiKeyUsageStats> getTopApiKeysByUsage(@Param("since") LocalDateTime since);
}