package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKeyRequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApiKeyRequestLogRepository extends JpaRepository<ApiKeyRequestLog, UUID> {

    /**
     * Find logs by API key ID with pagination
     */
    Page<ApiKeyRequestLog> findByApiKeyIdOrderByRequestTimestampDesc(UUID apiKeyId, Pageable pageable);

    /**
     * Find logs by user ID with pagination
     */
    Page<ApiKeyRequestLog> findByUserFkIdOrderByRequestTimestampDesc(String userFkId, Pageable pageable);

    /**
     * Find logs within a time range for an API key
     */
    List<ApiKeyRequestLog> findByApiKeyIdAndRequestTimestampBetweenOrderByRequestTimestampDesc(
            UUID apiKeyId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Count requests for an API key within a time range
     */
    @Query("SELECT COUNT(l) FROM ApiKeyRequestLog l WHERE l.apiKeyId = :apiKeyId " +
           "AND l.requestTimestamp BETWEEN :startTime AND :endTime")
    long countRequestsByApiKeyAndTimeRange(@Param("apiKeyId") UUID apiKeyId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * Find logs with security violations (IP/Domain restrictions)
     */
    @Query("SELECT l FROM ApiKeyRequestLog l WHERE l.apiKeyId = :apiKeyId " +
           "AND (l.isAllowedIp = false OR l.isAllowedDomain = false) " +
           "ORDER BY l.requestTimestamp DESC")
    List<ApiKeyRequestLog> findSecurityViolationsByApiKey(@Param("apiKeyId") UUID apiKeyId);

    /**
     * Get top client IPs for an API key
     */
    @Query("SELECT l.clientIp, COUNT(l) as requestCount FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.clientIp IS NOT NULL " +
           "GROUP BY l.clientIp ORDER BY requestCount DESC")
    List<Object[]> findTopClientIpsByApiKey(@Param("apiKeyId") UUID apiKeyId, Pageable pageable);

    /**
     * Get top domains for an API key
     */
    @Query("SELECT l.domain, COUNT(l) as requestCount FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.domain IS NOT NULL " +
           "GROUP BY l.domain ORDER BY requestCount DESC")
    List<Object[]> findTopDomainsByApiKey(@Param("apiKeyId") UUID apiKeyId, Pageable pageable);

    /**
     * Get request statistics by hour for the last 24 hours
     */
    @Query("SELECT HOUR(l.requestTimestamp) as hour, COUNT(l) as requestCount " +
           "FROM ApiKeyRequestLog l WHERE l.apiKeyId = :apiKeyId " +
           "AND l.requestTimestamp >= :since " +
           "GROUP BY HOUR(l.requestTimestamp) ORDER BY hour")
    List<Object[]> findHourlyStatistics(@Param("apiKeyId") UUID apiKeyId, 
                                       @Param("since") LocalDateTime since);

    /**
     * Get geographic distribution of requests
     */
    @Query("SELECT l.countryCode, l.region, l.city, COUNT(l) as requestCount " +
           "FROM ApiKeyRequestLog l WHERE l.apiKeyId = :apiKeyId " +
           "AND l.countryCode IS NOT NULL " +
           "GROUP BY l.countryCode, l.region, l.city ORDER BY requestCount DESC")
    List<Object[]> findGeographicDistribution(@Param("apiKeyId") UUID apiKeyId, Pageable pageable);

    /**
     * Find recent error logs
     */
    @Query("SELECT l FROM ApiKeyRequestLog l WHERE l.apiKeyId = :apiKeyId " +
           "AND l.errorMessage IS NOT NULL " +
           "ORDER BY l.requestTimestamp DESC")
    List<ApiKeyRequestLog> findRecentErrors(@Param("apiKeyId") UUID apiKeyId, Pageable pageable);

    /**
     * Get average response time for an API key
     */
    @Query("SELECT AVG(l.responseTimeMs) FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.responseTimeMs IS NOT NULL " +
           "AND l.requestTimestamp >= :since")
    Double findAverageResponseTime(@Param("apiKeyId") UUID apiKeyId, 
                                  @Param("since") LocalDateTime since);

    /**
     * Delete old logs (for cleanup)
     */
    void deleteByRequestTimestampBefore(LocalDateTime cutoffDate);

    /**
     * Find logs by client IP
     */
    List<ApiKeyRequestLog> findByClientIpOrderByRequestTimestampDesc(String clientIp, Pageable pageable);

    /**
     * Find logs by domain
     */
    List<ApiKeyRequestLog> findByDomainOrderByRequestTimestampDesc(String domain, Pageable pageable);
    
    // Additional methods needed by RequestLoggingService
    
    /**
     * Find request logs by API key and timestamp range (paginated)
     */
    org.springframework.data.domain.Page<ApiKeyRequestLog> findByApiKeyIdAndRequestTimestampBetweenOrderByRequestTimestampDesc(
        UUID apiKeyId, LocalDateTime from, LocalDateTime to, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find top N recent request logs for an API key
     */
    @Query("SELECT l FROM ApiKeyRequestLog l WHERE l.apiKeyId = :apiKeyId " +
           "ORDER BY l.requestTimestamp DESC")
    List<ApiKeyRequestLog> findTopNByApiKeyIdOrderByRequestTimestampDesc(
        @Param("apiKeyId") UUID apiKeyId, org.springframework.data.domain.Pageable pageable);
    
    default List<ApiKeyRequestLog> findTopNByApiKeyIdOrderByRequestTimestampDesc(UUID apiKeyId, int limit) {
        return findTopNByApiKeyIdOrderByRequestTimestampDesc(apiKeyId, 
            org.springframework.data.domain.PageRequest.of(0, limit));
    }
    
    /**
     * Find request logs by user and timestamp range (paginated)
     */
    org.springframework.data.domain.Page<ApiKeyRequestLog> findByUserFkIdAndRequestTimestampBetweenOrderByRequestTimestampDesc(
        String userFkId, LocalDateTime from, LocalDateTime to, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find request logs by API key, success status, and timestamp range
     */
    List<ApiKeyRequestLog> findByApiKeyIdAndSuccessAndRequestTimestampBetween(
        UUID apiKeyId, boolean success, LocalDateTime from, LocalDateTime to);
    
    /**
     * Find request logs by client IP and timestamp range
     */
    List<ApiKeyRequestLog> findByClientIpAndRequestTimestampBetween(
        String clientIp, LocalDateTime from, LocalDateTime to);
    
    /**
     * Find request logs by domain and timestamp range
     */
    List<ApiKeyRequestLog> findByDomainAndRequestTimestampBetween(
        String domain, LocalDateTime from, LocalDateTime to);
    
    /**
     * Count requests by API key after timestamp
     */
    long countByApiKeyIdAndRequestTimestampAfter(UUID apiKeyId, LocalDateTime timestamp);
    
    /**
     * Count requests by API key, success status, and after timestamp
     */
    long countByApiKeyIdAndSuccessAndRequestTimestampAfter(
        UUID apiKeyId, boolean success, LocalDateTime timestamp);
    
    /**
     * Find most active client IPs for an API key
     */
    @Query("SELECT l.clientIp, COUNT(l) as requestCount FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.requestTimestamp BETWEEN :from AND :to " +
           "GROUP BY l.clientIp ORDER BY requestCount DESC")
    List<Object[]> findMostActiveClientIps(@Param("apiKeyId") UUID apiKeyId, 
                                          @Param("from") LocalDateTime from, 
                                          @Param("to") LocalDateTime to, 
                                          org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find most accessed endpoints for an API key
     */
    @Query("SELECT l.requestPath, COUNT(l) as requestCount FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.requestTimestamp BETWEEN :from AND :to " +
           "GROUP BY l.requestPath ORDER BY requestCount DESC")
    List<Object[]> findMostAccessedEndpoints(@Param("apiKeyId") UUID apiKeyId, 
                                           @Param("from") LocalDateTime from, 
                                           @Param("to") LocalDateTime to, 
                                           org.springframework.data.domain.Pageable pageable);
    
    /**
     * Delete logs older than cutoff date
     */
    @Query("DELETE FROM ApiKeyRequestLog l WHERE l.requestTimestamp < :cutoff")
    int deleteLogsOlderThan(@Param("cutoff") LocalDateTime cutoff);
    
    /**
     * Find last request timestamp for an API key
     */
    @Query("SELECT MAX(l.requestTimestamp) FROM ApiKeyRequestLog l WHERE l.apiKeyId = :apiKeyId")
    LocalDateTime findLastRequestTimestamp(@Param("apiKeyId") UUID apiKeyId);
    
    /**
     * Find most active client IP for an API key since timestamp
     */
    @Query("SELECT l.clientIp FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.requestTimestamp >= :since " +
           "GROUP BY l.clientIp ORDER BY COUNT(l) DESC")
    String findMostActiveClientIp(@Param("apiKeyId") UUID apiKeyId, @Param("since") LocalDateTime since);

    // Additional methods for dashboard functionality

    /**
     * Count requests by user and time range
     */
    long countByUserFkIdAndRequestTimestampBetween(String userFkId, LocalDateTime from, LocalDateTime to);

    /**
     * Count distinct domains by user and time range
     */
    @Query("SELECT COUNT(DISTINCT l.domain) FROM ApiKeyRequestLog l " +
           "WHERE l.userFkId = :userFkId AND l.domain IS NOT NULL " +
           "AND l.requestTimestamp BETWEEN :from AND :to")
    Integer countDistinctDomainsByUserAndTimeRange(@Param("userFkId") String userFkId, 
                                                  @Param("from") LocalDateTime from, 
                                                  @Param("to") LocalDateTime to);

    /**
     * Count new domains for user in specific month
     */
    @Query("SELECT COUNT(DISTINCT l.domain) FROM ApiKeyRequestLog l " +
           "WHERE l.userFkId = :userFkId AND l.domain IS NOT NULL " +
           "AND TO_CHAR(l.requestTimestamp, 'YYYY-MM') = :monthYear " +
           "AND NOT EXISTS (" +
           "    SELECT 1 FROM ApiKeyRequestLog l2 " +
           "    WHERE l2.domain = l.domain AND l2.userFkId = :userFkId " +
           "    AND l2.requestTimestamp < DATE_TRUNC('month', TO_DATE(:monthYear, 'YYYY-MM'))" +
           ")")
    Integer countNewDomainsForUserInMonth(@Param("userFkId") String userFkId, @Param("monthYear") String monthYear);

    /**
     * Get success rate for user in time range
     */
    @Query("SELECT CASE WHEN COUNT(l) > 0 THEN " +
           "(CAST(COUNT(CASE WHEN l.success = true THEN 1 END) AS DOUBLE) / CAST(COUNT(l) AS DOUBLE)) * 100.0 " +
           "ELSE 0.0 END " +
           "FROM ApiKeyRequestLog l " +
           "WHERE l.userFkId = :userFkId AND l.requestTimestamp BETWEEN :from AND :to")
    Double getSuccessRateForUser(@Param("userFkId") String userFkId, 
                                @Param("from") LocalDateTime from, 
                                @Param("to") LocalDateTime to);

    /**
     * Count pending requests for API key (rate limited + failed requests that might retry)
     */
    @Query("SELECT COUNT(l) FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.requestTimestamp >= :since " +
           "AND (l.responseStatus = 429 OR (l.success = false AND l.responseStatus IN (500, 502, 503, 504)))")
    Long countPendingRequestsForApiKey(@Param("apiKeyId") UUID apiKeyId, @Param("since") LocalDateTime since);

    /**
     * Count requests by API key and time range
     */
    Long countByApiKeyIdAndRequestTimestampBetween(UUID apiKeyId, LocalDateTime start, LocalDateTime end);

    /**
     * Count requests by API key and time range with success filter
     */
    @Query("SELECT COUNT(l) FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.requestTimestamp BETWEEN :start AND :end " +
           "AND l.success = :success")
    Long countByApiKeyIdAndRequestTimestampBetweenAndSuccess(@Param("apiKeyId") UUID apiKeyId, 
                                                           @Param("start") LocalDateTime start, 
                                                           @Param("end") LocalDateTime end,
                                                           @Param("success") Boolean success);

    /**
     * Get average response time for API key in time range
     */
    @Query("SELECT AVG(l.responseTimeMs) FROM ApiKeyRequestLog l " +
           "WHERE l.apiKeyId = :apiKeyId AND l.requestTimestamp BETWEEN :start AND :end " +
           "AND l.responseTimeMs IS NOT NULL")
    Double getAverageResponseTimeByApiKeyIdAndRequestTimestampBetween(@Param("apiKeyId") UUID apiKeyId, 
                                                                     @Param("start") LocalDateTime start, 
                                                                     @Param("end") LocalDateTime end);
}