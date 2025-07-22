package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKeyRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing API key rate limiting data.
 */
@Repository
public interface ApiKeyRateLimitRepository extends JpaRepository<ApiKeyRateLimit, UUID> {
    
    /**
     * Find active rate limit window for a specific API key.
     * @param apiKeyHash The hashed API key
     * @param now Current timestamp
     * @return Optional containing active rate limit if found
     */
    @Query("SELECT r FROM ApiKeyRateLimit r WHERE r.apiKeyHash = :apiKeyHash " +
           "AND r.windowStart <= :now AND r.windowEnd > :now")
    Optional<ApiKeyRateLimit> findActiveRateLimit(@Param("apiKeyHash") String apiKeyHash, 
                                                 @Param("now") LocalDateTime now);
    
    /**
     * Find the most recent rate limit for an API key (regardless of status).
     * @param apiKeyHash The hashed API key
     * @return Optional containing the most recent rate limit
     */
    @Query("SELECT r FROM ApiKeyRateLimit r WHERE r.apiKeyHash = :apiKeyHash " +
           "ORDER BY r.windowStart DESC LIMIT 1")
    Optional<ApiKeyRateLimit> findMostRecentRateLimit(@Param("apiKeyHash") String apiKeyHash);
    
    /**
     * Delete expired rate limit records for cleanup.
     * @param before Timestamp before which records should be deleted
     * @return Number of deleted records
     */
    @Modifying
    @Query("DELETE FROM ApiKeyRateLimit r WHERE r.windowEnd < :before")
    int deleteExpiredRateLimits(@Param("before") LocalDateTime before);
    
    /**
     * Count total requests for an API key in a specific time range.
     * @param apiKeyHash The hashed API key
     * @param start Start of time range
     * @param end End of time range
     * @return Total request count
     */
    @Query("SELECT COALESCE(SUM(r.requestCount), 0) FROM ApiKeyRateLimit r " +
           "WHERE r.apiKeyHash = :apiKeyHash AND r.windowStart >= :start AND r.windowEnd <= :end")
    Long getTotalRequestCount(@Param("apiKeyHash") String apiKeyHash, 
                             @Param("start") LocalDateTime start, 
                             @Param("end") LocalDateTime end);
    
    /**
     * Find all rate limits for a specific API key (for analytics).
     * @param apiKeyHash The hashed API key
     * @return List of rate limits
     */
    @Query("SELECT r FROM ApiKeyRateLimit r WHERE r.apiKeyHash = :apiKeyHash " +
           "ORDER BY r.windowStart DESC")
    java.util.List<ApiKeyRateLimit> findAllByApiKeyHashOrderByWindowStartDesc(@Param("apiKeyHash") String apiKeyHash);
}