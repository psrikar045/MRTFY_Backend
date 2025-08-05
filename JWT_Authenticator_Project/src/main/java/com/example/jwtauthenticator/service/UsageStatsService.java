package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyUsageStatsRepository;
import com.example.jwtauthenticator.util.RateLimitWindowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing API key usage statistics.
 * Integrates with the existing API key system to provide comprehensive usage tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsageStatsService {

    private final ApiKeyUsageStatsRepository statsRepository;

    /**
     * Record API key usage asynchronously to avoid blocking requests.
     */
    @Async
    @Transactional
    public void recordApiKeyUsage(UUID apiKeyId, String userFkId, String endpoint, 
                                 String method, String clientIp, RateLimitTier tier) {
        log.info("Recording API key usage for key: {}, endpoint: {}, method: {}", apiKeyId, endpoint, method);
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = RateLimitWindowUtil.getWindowStart(now, tier);
            LocalDateTime windowEnd = RateLimitWindowUtil.getWindowEnd(windowStart, tier);

            // Find existing stats for this time window
            Optional<ApiKeyUsageStats> existingStats = statsRepository
                .findByApiKeyIdAndWindowStart(apiKeyId, windowStart);

            if (existingStats.isPresent()) {
                // Update existing stats
                ApiKeyUsageStats stats = existingStats.get();
                stats.incrementRequestCount();
                stats.setLastRequestAt(now);
                
                // Update peak requests per minute if needed
                updatePeakRequestsPerMinute(stats, now);
                
                ApiKeyUsageStats savedStats = statsRepository.save(stats);
                log.info("Updated usage stats for API key: {}, current count: {}, ID: {}", 
                         apiKeyId, savedStats.getRequestCount(), savedStats.getId());
            } else {
                // Create new stats window
                ApiKeyUsageStats newStats = ApiKeyUsageStats.builder()
                    .apiKeyId(apiKeyId)
                    .userFkId(userFkId)
                    .rateLimitTier(tier)
                    .windowStart(windowStart)
                    .windowEnd(windowEnd)
                    .requestCount(1)
                    .requestLimit(tier.getRequestLimit())
                    .remainingRequests(tier.getRequestLimit() - 1)
                    .firstRequestAt(now)
                    .lastRequestAt(now)
                    .totalRequestsLifetime(1L)
                    .peakRequestsPerMinute(1)
                    .build();
                    
                ApiKeyUsageStats savedNewStats = statsRepository.save(newStats);
                log.info("Created new usage stats window for API key: {}, ID: {}, window: {} to {}", 
                         apiKeyId, savedNewStats.getId(), windowStart, windowEnd);
            }
        } catch (Exception e) {
            log.error("Error recording API key usage for key: {}, error: {}", apiKeyId, e.getMessage(), e);
        }
    }

    /**
     * Get current usage statistics for an API key.
     */
    public Optional<ApiKeyUsageStats> getCurrentUsageStats(UUID apiKeyId, RateLimitTier tier) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = RateLimitWindowUtil.getWindowStart(now, tier);
        
        return statsRepository.findByApiKeyIdAndWindowStart(apiKeyId, windowStart);
    }

    /**
     * Get current usage statistics for an API key (legacy method - uses daily window).
     * @deprecated Use getCurrentUsageStats(UUID, RateLimitTier) instead
     */
    @Deprecated
    public Optional<ApiKeyUsageStats> getCurrentUsageStats(UUID apiKeyId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.truncatedTo(ChronoUnit.DAYS);
        
        return statsRepository.findByApiKeyIdAndWindowStart(apiKeyId, windowStart);
    }

    /**
     * Get usage statistics for an API key within a date range.
     */
    public List<ApiKeyUsageStats> getUsageStatsForApiKey(UUID apiKeyId, LocalDateTime from, LocalDateTime to) {
        return statsRepository.findByApiKeyIdAndWindowStartBetween(apiKeyId, from, to);
    }

    /**
     * Get paginated usage statistics for an API key.
     */
    public Page<ApiKeyUsageStats> getUsageStatsForApiKey(UUID apiKeyId, LocalDateTime from, 
                                                        LocalDateTime to, Pageable pageable) {
        return statsRepository.findByApiKeyIdAndWindowStartBetweenOrderByWindowStartDesc(
            apiKeyId, from, to, pageable);
    }

    /**
     * Get usage statistics for a user across all their API keys.
     */
    public List<ApiKeyUsageStats> getUsageStatsForUser(String userFkId, LocalDateTime from, LocalDateTime to) {
        return statsRepository.findByUserFkIdAndWindowStartBetween(userFkId, from, to);
    }

    /**
     * Get total requests made by an API key in the last 24 hours.
     */
    public long getTotalRequestsLast24Hours(UUID apiKeyId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusHours(24);
        
        return statsRepository.getTotalRequestsInPeriod(apiKeyId, yesterday, now);
    }

    /**
     * Get total requests made by an API key today.
     */
    public long getTotalRequestsToday(UUID apiKeyId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        
        return statsRepository.getTotalRequestsInPeriod(apiKeyId, startOfDay, now);
    }

    /**
     * Check if an API key is approaching its rate limit.
     */
    public boolean isApproachingRateLimit(UUID apiKeyId, double threshold) {
        Optional<ApiKeyUsageStats> currentStats = getCurrentUsageStats(apiKeyId);
        
        if (currentStats.isEmpty()) {
            return false;
        }
        
        ApiKeyUsageStats stats = currentStats.get();
        if (stats.getRateLimitTier().isUnlimited()) {
            return false;
        }
        
        double usagePercentage = stats.getUsagePercentage();
        return usagePercentage >= (threshold * 100);
    }

    /**
     * Get API keys that are approaching their rate limits.
     * Note: This method returns results for all tiers, so it uses current time for filtering
     */
    public List<ApiKeyUsageStats> getApiKeysApproachingLimits(double threshold) {
        LocalDateTime now = LocalDateTime.now();
        
        return statsRepository.findApiKeysApproachingLimits(now, threshold);
    }

    /**
     * Clean up old usage statistics (older than specified days).
     */
    @Transactional
    public int cleanupOldStats(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        int deletedCount = statsRepository.deleteStatsOlderThan(cutoff);
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} old usage statistics records", deletedCount);
        }
        
        return deletedCount;
    }

    /**
     * Get usage summary for an API key with specific tier.
     */
    public UsageSummary getUsageSummary(UUID apiKeyId, RateLimitTier tier) {
        LocalDateTime now = LocalDateTime.now();
        
        // Current window stats
        Optional<ApiKeyUsageStats> currentStats = getCurrentUsageStats(apiKeyId, tier);
        
        // Today's stats
        long requestsToday = getTotalRequestsToday(apiKeyId);
        
        // Last 24 hours stats
        long requestsLast24Hours = getTotalRequestsLast24Hours(apiKeyId);
        
        // Last 7 days stats
        LocalDateTime weekAgo = now.minusDays(7);
        long requestsLast7Days = statsRepository.getTotalRequestsInPeriod(apiKeyId, weekAgo, now);
        
        // Last 30 days stats
        LocalDateTime monthAgo = now.minusDays(30);
        long requestsLast30Days = statsRepository.getTotalRequestsInPeriod(apiKeyId, monthAgo, now);
        
        return UsageSummary.builder()
            .apiKeyId(apiKeyId)
            .currentWindowRequests(currentStats.map(ApiKeyUsageStats::getRequestCount).orElse(0))
            .currentWindowLimit(currentStats.map(ApiKeyUsageStats::getRequestLimit).orElse(0))
            .rateLimitTier(tier)
            .requestsToday(requestsToday)
            .requestsLast24Hours(requestsLast24Hours)
            .requestsLast7Days(requestsLast7Days)
            .requestsLast30Days(requestsLast30Days)
            .lastRequestAt(currentStats.map(ApiKeyUsageStats::getLastRequestAt).orElse(null))
            .isRateLimited(currentStats.map(ApiKeyUsageStats::getIsRateLimited).orElse(false))
            .rateLimitResetAt(currentStats.map(ApiKeyUsageStats::getRateLimitResetAt).orElse(null))
            .build();
    }

    /**
     * Get usage summary for an API key (legacy method - uses daily window).
     * @deprecated Use getUsageSummary(UUID, RateLimitTier) instead
     */
    // @Deprecated
    // public UsageSummary getUsageSummary(UUID apiKeyId) {
    //     return getUsageSummary(apiKeyId, RateLimitTier.FREE_TIER); // Default to FREE_TIER
    // }

    /**
     * Update peak requests per minute for a stats window.
     */
    private void updatePeakRequestsPerMinute(ApiKeyUsageStats stats, LocalDateTime now) {
        // Simple implementation: count requests in current minute
        // In a production system, you might want more sophisticated tracking
        LocalDateTime minuteStart = now.withSecond(0).withNano(0);
        
        // For now, just increment if we're in a new minute
        if (stats.getLastRequestAt() == null || 
            !stats.getLastRequestAt().withSecond(0).withNano(0).equals(minuteStart)) {
            // Reset minute counter (simplified)
            stats.setPeakRequestsPerMinute(Math.max(stats.getPeakRequestsPerMinute(), 1));
        }
    }



    /**
     * Usage summary DTO.
     */
    @lombok.Data
    @lombok.Builder
    public static class UsageSummary {
        private UUID apiKeyId;
        private int currentWindowRequests;
        private int currentWindowLimit;
        private RateLimitTier rateLimitTier;
        private long requestsToday;
        private long requestsLast24Hours;
        private long requestsLast7Days;
        private long requestsLast30Days;
        private LocalDateTime lastRequestAt;
        private boolean isRateLimited;
        private LocalDateTime rateLimitResetAt;
        
        public double getCurrentWindowUsagePercentage() {
            if (currentWindowLimit == 0) return 0.0;
            return (double) currentWindowRequests / currentWindowLimit * 100.0;
        }
        
        public int getRemainingRequestsThisWindow() {
            return Math.max(0, currentWindowLimit - currentWindowRequests);
        }
        
        public String getWindowType() {
            return RateLimitWindowUtil.getWindowDescription(rateLimitTier);
        }
        
        // Legacy methods for backward compatibility
        @Deprecated
        public double getCurrentHourUsagePercentage() {
            return getCurrentWindowUsagePercentage();
        }
        
        @Deprecated
        public int getRemainingRequestsThisHour() {
            return getRemainingRequestsThisWindow();
        }
        
        @Deprecated
        public int getCurrentHourRequests() {
            return currentWindowRequests;
        }
        
        @Deprecated
        public int getCurrentHourLimit() {
            return currentWindowLimit;
        }
    }
}