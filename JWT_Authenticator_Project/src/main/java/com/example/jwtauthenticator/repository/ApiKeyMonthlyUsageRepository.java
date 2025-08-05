package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing monthly API usage tracking
 */
@Repository
public interface ApiKeyMonthlyUsageRepository extends JpaRepository<ApiKeyMonthlyUsage, UUID> {
    
    /**
     * Find usage record for specific API key and month
     */
    Optional<ApiKeyMonthlyUsage> findByApiKeyIdAndMonthYear(UUID apiKeyId, String monthYear);
    
    /**
     * Find all usage records for a specific API key
     */
    List<ApiKeyMonthlyUsage> findByApiKeyIdOrderByMonthYearDesc(UUID apiKeyId);
    
    /**
     * Find all usage records for a specific user
     */
    List<ApiKeyMonthlyUsage> findByUserIdOrderByMonthYearDesc(String userId);
    
    /**
     * Find usage records for a specific user and month
     */
    List<ApiKeyMonthlyUsage> findByUserIdAndMonthYear(String userId, String monthYear);
    
    /**
     * Find usage records that need reset (older than specified date)
     */
    @Query("SELECT u FROM ApiKeyMonthlyUsage u WHERE u.lastResetDate < :resetDate")
    List<ApiKeyMonthlyUsage> findUsageRecordsNeedingReset(@Param("resetDate") LocalDate resetDate);
    
    /**
     * Get total API calls for user in current month
     */
    @Query("SELECT COALESCE(SUM(u.totalCalls), 0) FROM ApiKeyMonthlyUsage u WHERE u.userId = :userId AND u.monthYear = :monthYear")
    Integer getTotalCallsForUserInMonth(@Param("userId") String userId, @Param("monthYear") String monthYear);
    
    /**
     * Get total API calls for API key in current month
     */
    @Query("SELECT COALESCE(u.totalCalls, 0) FROM ApiKeyMonthlyUsage u WHERE u.apiKeyId = :apiKeyId AND u.monthYear = :monthYear")
    Integer getTotalCallsForApiKeyInMonth(@Param("apiKeyId") UUID apiKeyId, @Param("monthYear") String monthYear);
    
    /**
     * Find API keys that have exceeded their quota
     */
    @Query("SELECT u FROM ApiKeyMonthlyUsage u WHERE u.totalCalls >= u.quotaLimit AND u.quotaLimit > 0 AND u.monthYear = :monthYear")
    List<ApiKeyMonthlyUsage> findQuotaExceededKeys(@Param("monthYear") String monthYear);
    
    /**
     * Find API keys approaching their quota (>80% usage)
     */
    @Query("SELECT u FROM ApiKeyMonthlyUsage u WHERE u.totalCalls >= (u.quotaLimit * 0.8) AND u.quotaLimit > 0 AND u.monthYear = :monthYear")
    List<ApiKeyMonthlyUsage> findKeysApproachingQuota(@Param("monthYear") String monthYear);
    
    /**
     * Get usage statistics for a user across all API keys
     */
    @Query("SELECT new map(" +
           "COALESCE(SUM(u.totalCalls), 0) as totalCalls, " +
           "COALESCE(SUM(u.successfulCalls), 0) as successfulCalls, " +
           "COALESCE(SUM(u.failedCalls), 0) as failedCalls, " +
           "COALESCE(SUM(u.quotaExceededCalls), 0) as quotaExceededCalls, " +
           "COUNT(u) as activeKeys) " +
           "FROM ApiKeyMonthlyUsage u WHERE u.userId = :userId AND u.monthYear = :monthYear")
    Optional<Object> getUserUsageStats(@Param("userId") String userId, @Param("monthYear") String monthYear);
    
    /**
     * Delete old usage records (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM ApiKeyMonthlyUsage u WHERE u.monthYear < :cutoffMonth")
    int deleteOldUsageRecords(@Param("cutoffMonth") String cutoffMonth);
    
    /**
     * Reset usage counters for new month
     */
    @Modifying
    @Query("UPDATE ApiKeyMonthlyUsage u SET " +
           "u.totalCalls = 0, " +
           "u.successfulCalls = 0, " +
           "u.failedCalls = 0, " +
           "u.quotaExceededCalls = 0, " +
           "u.lastResetDate = :resetDate, " +
           "u.monthYear = :newMonthYear, " +
           "u.firstCallAt = null, " +
           "u.lastCallAt = null " +
           "WHERE u.apiKeyId = :apiKeyId AND u.monthYear = :oldMonthYear")
    int resetUsageForNewMonth(@Param("apiKeyId") UUID apiKeyId, 
                             @Param("oldMonthYear") String oldMonthYear,
                             @Param("newMonthYear") String newMonthYear,
                             @Param("resetDate") LocalDate resetDate);
    
    /**
     * Find usage records by month year pattern (for reporting)
     */
    @Query("SELECT u FROM ApiKeyMonthlyUsage u WHERE u.monthYear LIKE :monthPattern ORDER BY u.monthYear DESC")
    List<ApiKeyMonthlyUsage> findByMonthPattern(@Param("monthPattern") String monthPattern);
    
    /**
     * Get monthly usage trend for user
     */
    @Query("SELECT u FROM ApiKeyMonthlyUsage u WHERE u.userId = :userId ORDER BY u.monthYear DESC LIMIT :months")
    List<ApiKeyMonthlyUsage> getUserUsageTrend(@Param("userId") String userId, @Param("months") int months);
    
    /**
     * Check if API key has any usage in current month
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM ApiKeyMonthlyUsage u WHERE u.apiKeyId = :apiKeyId AND u.monthYear = :monthYear AND u.totalCalls > 0")
    boolean hasUsageInMonth(@Param("apiKeyId") UUID apiKeyId, @Param("monthYear") String monthYear);
    
    /**
     * Get average daily usage for API key
     */
    @Query("SELECT COALESCE(AVG(u.totalCalls), 0) FROM ApiKeyMonthlyUsage u WHERE u.apiKeyId = :apiKeyId")
    Double getAverageMonthlyUsage(@Param("apiKeyId") UUID apiKeyId);

    /**
     * Get total remaining quota for user in specific month
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN u.quotaLimit > 0 THEN GREATEST(0, u.quotaLimit - u.totalCalls) ELSE 0 END), 0) " +
           "FROM ApiKeyMonthlyUsage u WHERE u.userId = :userId AND u.monthYear = :monthYear")
    Long getTotalRemainingQuotaForUser(@Param("userId") String userId, @Param("monthYear") String monthYear);

    /**
     * Get total quota limit for user in specific month
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN u.quotaLimit > 0 THEN u.quotaLimit ELSE 1000 END), 1000) " +
           "FROM ApiKeyMonthlyUsage u WHERE u.userId = :userId AND u.monthYear = :monthYear")
    Long getTotalQuotaLimitForUser(@Param("userId") String userId, @Param("monthYear") String monthYear);

    /**
     * Get total used quota for user in specific month
     */
    @Query("SELECT COALESCE(SUM(u.totalCalls), 0) " +
           "FROM ApiKeyMonthlyUsage u WHERE u.userId = :userId AND u.monthYear = :monthYear")
    Long getTotalUsedQuotaForUser(@Param("userId") String userId, @Param("monthYear") String monthYear);
}