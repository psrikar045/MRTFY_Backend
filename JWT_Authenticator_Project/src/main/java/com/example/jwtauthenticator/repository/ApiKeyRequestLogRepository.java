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
}