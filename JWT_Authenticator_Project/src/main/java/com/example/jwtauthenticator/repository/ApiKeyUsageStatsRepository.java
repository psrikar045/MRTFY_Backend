package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.entity.RateLimitTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyUsageStatsRepository extends JpaRepository<ApiKeyUsageStats, String> {

    /**
     * Find current usage stats for an API key within the current time window
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.apiKeyId = :apiKeyId " +
           "AND aus.windowStart <= :currentTime AND aus.windowEnd > :currentTime")
    Optional<ApiKeyUsageStats> findCurrentUsageStats(@Param("apiKeyId") String apiKeyId, 
                                                     @Param("currentTime") LocalDateTime currentTime);

    /**
     * Find all usage stats for an API key (for historical analysis)
     */
    List<ApiKeyUsageStats> findByApiKeyIdOrderByWindowStartDesc(String apiKeyId);

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
    Long getTotalRequestsSince(@Param("apiKeyId") String apiKeyId, @Param("since") LocalDateTime since);

    /**
     * Get usage statistics for a time period
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.apiKeyId = :apiKeyId " +
           "AND aus.windowStart >= :startTime AND aus.windowEnd <= :endTime " +
           "ORDER BY aus.windowStart ASC")
    List<ApiKeyUsageStats> getUsageStatsBetween(@Param("apiKeyId") String apiKeyId,
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
           "COUNT(DISTINCT aus.apiKeyId) as activeKeys, " +
           "SUM(aus.requestCount) as totalRequests, " +
           "SUM(aus.blockedRequests) as totalBlocked, " +
           "AVG(aus.requestCount) as avgRequestsPerKey " +
           "FROM ApiKeyUsageStats aus WHERE aus.windowStart >= :since")
    Object[] getSystemWideStats(@Param("since") LocalDateTime since);

    /**
     * Clean up old usage stats (for maintenance)
     */
    @Query("DELETE FROM ApiKeyUsageStats aus WHERE aus.windowEnd < :cutoffTime")
    void deleteOldUsageStats(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find peak usage periods
     */
    @Query("SELECT aus FROM ApiKeyUsageStats aus WHERE aus.requestCount >= :threshold " +
           "ORDER BY aus.requestCount DESC")
    List<ApiKeyUsageStats> findPeakUsagePeriods(@Param("threshold") Integer threshold);
}