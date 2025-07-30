package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.ApiKeyStatisticsDTO;
import com.example.jwtauthenticator.dto.SystemStatisticsDTO;
import com.example.jwtauthenticator.dto.UsageAnalyticsDTO;
import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.entity.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyUsageStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for providing comprehensive API key usage statistics and analytics
 * Professional-grade analytics similar to AWS CloudWatch, Google Analytics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyStatisticsService {

    private final ApiKeyUsageStatsRepository usageStatsRepository;

    /**
     * Get comprehensive statistics for a specific API key
     */
    public ApiKeyStatisticsDTO getApiKeyStatistics(String apiKeyId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // Get current usage
        var currentUsage = usageStatsRepository.findCurrentUsageStats(apiKeyId, LocalDateTime.now());
        
        // Get historical data
        List<ApiKeyUsageStats> historicalStats = usageStatsRepository.getUsageStatsBetween(
            apiKeyId, since, LocalDateTime.now());
        
        // Calculate metrics
        long totalRequests = historicalStats.stream()
            .mapToLong(stats -> stats.getRequestCount().longValue())
            .sum();
            
        long totalBlocked = historicalStats.stream()
            .mapToLong(stats -> stats.getBlockedRequests().longValue())
            .sum();
            
        double averageRequestsPerHour = historicalStats.isEmpty() ? 0 : 
            (double) totalRequests / historicalStats.size();
            
        int peakRequestsInHour = historicalStats.stream()
            .mapToInt(ApiKeyUsageStats::getRequestCount)
            .max()
            .orElse(0);
            
        // Calculate success rate
        double successRate = totalRequests == 0 ? 100.0 : 
            ((double) (totalRequests - totalBlocked) / totalRequests) * 100.0;

        return ApiKeyStatisticsDTO.builder()
            .apiKeyId(apiKeyId)
            .periodHours(hours)
            .currentUsage(currentUsage.orElse(null))
            .totalRequests(totalRequests)
            .totalBlockedRequests(totalBlocked)
            .successRate(successRate)
            .averageRequestsPerHour(averageRequestsPerHour)
            .peakRequestsInHour(peakRequestsInHour)
            .historicalData(historicalStats)
            .generatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Get usage analytics with trends and patterns
     */
    public UsageAnalyticsDTO getUsageAnalytics(String apiKeyId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<ApiKeyUsageStats> stats = usageStatsRepository.getUsageStatsBetween(
            apiKeyId, since, LocalDateTime.now());

        // Group by day for trend analysis
        Map<LocalDateTime, List<ApiKeyUsageStats>> dailyStats = stats.stream()
            .collect(Collectors.groupingBy(
                stat -> stat.getWindowStart().truncatedTo(ChronoUnit.DAYS)
            ));

        // Calculate daily totals
        Map<LocalDateTime, Long> dailyRequestCounts = dailyStats.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .mapToLong(stat -> stat.getRequestCount().longValue())
                    .sum()
            ));

        // Find peak usage day
        var peakDay = dailyRequestCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);

        // Calculate trend (simple linear trend)
        double trend = calculateTrend(dailyRequestCounts);

        // Usage patterns
        Map<Integer, Long> hourlyPattern = stats.stream()
            .collect(Collectors.groupingBy(
                stat -> stat.getWindowStart().getHour(),
                Collectors.summingLong(stat -> stat.getRequestCount().longValue())
            ));

        return UsageAnalyticsDTO.builder()
            .apiKeyId(apiKeyId)
            .periodDays(days)
            .dailyRequestCounts(dailyRequestCounts)
            .hourlyUsagePattern(hourlyPattern)
            .peakUsageDay(peakDay != null ? peakDay.getKey() : null)
            .peakUsageCount(peakDay != null ? peakDay.getValue() : 0L)
            .usageTrend(trend)
            .totalDaysAnalyzed(dailyStats.size())
            .generatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Get system-wide statistics across all API keys
     */
    public SystemStatisticsDTO getSystemStatistics(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // Get raw system stats
        Object[] rawStats = usageStatsRepository.getSystemWideStats(since);
        
        // Get tier distribution
        Map<RateLimitTier, Long> tierDistribution = usageStatsRepository.findAll().stream()
            .collect(Collectors.groupingBy(
                ApiKeyUsageStats::getRateLimitTier,
                Collectors.counting()
            ));

        // Get currently rate-limited keys
        List<ApiKeyUsageStats> rateLimitedKeys = 
            usageStatsRepository.findCurrentlyRateLimitedKeys(LocalDateTime.now());

        // Get top API keys by usage
        List<ApiKeyUsageStats> topApiKeys = 
            usageStatsRepository.getTopApiKeysByUsage(since);

        return SystemStatisticsDTO.builder()
            .periodHours(hours)
            .totalActiveApiKeys(rawStats.length > 0 ? ((Number) rawStats[0]).longValue() : 0L)
            .totalRequests(rawStats.length > 1 ? ((Number) rawStats[1]).longValue() : 0L)
            .totalBlockedRequests(rawStats.length > 2 ? ((Number) rawStats[2]).longValue() : 0L)
            .averageRequestsPerKey(rawStats.length > 3 ? ((Number) rawStats[3]).doubleValue() : 0.0)
            .currentlyRateLimitedKeys(rateLimitedKeys.size())
            .tierDistribution(tierDistribution)
            .topApiKeysByUsage(topApiKeys.stream().limit(10).collect(Collectors.toList()))
            .generatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Get real-time usage statistics for monitoring dashboards
     */
    public Map<String, Object> getRealTimeStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastHour = now.minusHours(1);
        
        // Current active requests
        long activeRequests = usageStatsRepository.findAll().stream()
            .filter(stats -> stats.getLastRequestAt() != null && 
                           stats.getLastRequestAt().isAfter(now.minusMinutes(5)))
            .count();

        // Requests in last hour
        long requestsLastHour = usageStatsRepository.findAll().stream()
            .filter(stats -> stats.getWindowStart().isAfter(lastHour))
            .mapToLong(stats -> stats.getRequestCount().longValue())
            .sum();

        // Rate limited keys
        long rateLimitedKeys = usageStatsRepository.findCurrentlyRateLimitedKeys(now).size();

        return Map.of(
            "activeRequests", activeRequests,
            "requestsLastHour", requestsLastHour,
            "rateLimitedKeys", rateLimitedKeys,
            "timestamp", now
        );
    }

    /**
     * Calculate simple linear trend from daily data
     */
    private double calculateTrend(Map<LocalDateTime, Long> dailyData) {
        if (dailyData.size() < 2) return 0.0;

        List<Map.Entry<LocalDateTime, Long>> sortedData = dailyData.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toList());

        // Simple linear regression slope calculation
        int n = sortedData.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i; // Day index
            double y = sortedData.get(i).getValue(); // Request count
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // Calculate slope (trend)
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }

    /**
     * Clean up old statistics (maintenance function)
     */
    public void cleanupOldStatistics(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        usageStatsRepository.deleteOldUsageStats(cutoff);
        log.info("Cleaned up usage statistics older than {} days", daysToKeep);
    }
}