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
}