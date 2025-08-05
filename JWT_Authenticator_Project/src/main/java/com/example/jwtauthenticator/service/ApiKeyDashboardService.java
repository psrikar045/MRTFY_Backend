package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.dashboard.SingleApiKeyDashboardDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.repository.ApiKeyDashboardSummaryRepository;
import com.example.jwtauthenticator.entity.ApiKeyDashboardSummaryView;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.ApiKeyUsageStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Single API Key Dashboard
 * Provides detailed metrics for individual API keys
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyDashboardService {

    private final ApiKeyDashboardSummaryRepository dashboardSummaryRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyRequestLogRepository requestLogRepository;
    private final ApiKeyMonthlyUsageRepository monthlyUsageRepository;
    private final ApiKeyUsageStatsRepository usageStatsRepository;

    /**
     * Get dashboard metrics for a specific API key
     * Mixed approach: materialized view + real-time data
     */
    @Cacheable(value = "apiKeyDashboard", key = "#apiKeyId + '_' + #userId", unless = "#result == null", cacheManager = "dashboardCacheManager")
    public SingleApiKeyDashboardDTO getApiKeyDashboard(UUID apiKeyId, String userId) {
        log.debug("Fetching dashboard for API key: {} (user: {})", apiKeyId, userId);

        // Verify API key belongs to user
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByIdAndUserFkId(apiKeyId, userId);
        if (apiKeyOpt.isEmpty()) {
            log.warn("API key {} not found or doesn't belong to user {}", apiKeyId, userId);
            return null;
        }

        try {
            // Try materialized view first (fast path)
            Optional<ApiKeyDashboardSummaryView> summaryOpt = 
                dashboardSummaryRepository.findByApiKeyIdAndUserFkId(apiKeyId, userId);

            if (summaryOpt.isPresent()) {
                log.debug("Using materialized view data for API key: {}", apiKeyId);
                return buildDashboardFromSummary(summaryOpt.get(), apiKeyOpt.get());
            } else {
                log.debug("Materialized view data not available, calculating real-time for API key: {}", apiKeyId);
                return calculateRealTimeDashboard(apiKeyOpt.get());
            }

        } catch (Exception e) {
            log.error("Error fetching dashboard for API key {}: {}", apiKeyId, e.getMessage(), e);
            // Fallback to real-time calculation
            return calculateRealTimeDashboard(apiKeyOpt.get());
        }
    }

    /**
     * Build dashboard from materialized view (fast path)
     */
    private SingleApiKeyDashboardDTO buildDashboardFromSummary(
            ApiKeyDashboardSummaryView summary, 
            ApiKey apiKey) {

        // Calculate today vs yesterday change
        Double todayVsYesterdayChange = calculatePercentageChange(
            summary.getRequestsToday(), 
            summary.getRequestsYesterday()
        );

        // Build monthly metrics
        SingleApiKeyDashboardDTO.MonthlyMetricsDTO monthlyMetrics = buildMonthlyMetrics(
            summary.getTotalCallsMonth(),
            summary.getSuccessfulCallsMonth(),
            summary.getFailedCallsMonth(),
            summary.getQuotaLimit(),
            summary.getUsagePercentage()
        );

        // Build performance metrics
        SingleApiKeyDashboardDTO.PerformanceMetricsDTO performanceMetrics = buildPerformanceMetrics(
            summary.getAvgResponseTime7Days(),
            summary.getErrorRate24h(),
            summary.getLastUsed()
        );

        // Build rate limit info
        SingleApiKeyDashboardDTO.RateLimitInfoDTO rateLimitInfo = buildRateLimitInfo(
            summary.getRateLimitTier(),
            apiKey.getId()
        );

        return SingleApiKeyDashboardDTO.builder()
                .apiKeyId(summary.getApiKeyId())
                .apiKeyName(summary.getApiKeyName())
                .registeredDomain(summary.getRegisteredDomain())
                .requestsToday(summary.getRequestsToday())
                .requestsYesterday(summary.getRequestsYesterday())
                .todayVsYesterdayChange(todayVsYesterdayChange)
                .pendingRequests(summary.getPendingRequests())
                .usagePercentage(summary.getUsagePercentage())
                .lastUsed(summary.getLastUsed())
                .status(summary.getStatus())
                .monthlyMetrics(monthlyMetrics)
                .performanceMetrics(performanceMetrics)
                .rateLimitInfo(rateLimitInfo)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Calculate dashboard in real-time (fallback path)
     */
    private SingleApiKeyDashboardDTO calculateRealTimeDashboard(ApiKey apiKey) {
        UUID apiKeyId = apiKey.getId();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        String currentMonth = today.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // Calculate today's and yesterday's requests using existing methods
        Long requestsToday = requestLogRepository.countByApiKeyIdAndRequestTimestampBetween(
            apiKeyId, today.atStartOfDay(), now);
        Long requestsYesterday = requestLogRepository.countByApiKeyIdAndRequestTimestampBetween(
            apiKeyId, yesterday.atStartOfDay(), yesterday.atTime(23, 59, 59));

        Double todayVsYesterdayChange = calculatePercentageChange(requestsToday, requestsYesterday);

        // Calculate pending requests (rate limited + failed requests in last hour)
        Long pendingRequests = requestLogRepository.countPendingRequestsForApiKey(apiKeyId, now.minusHours(1));

        // Get monthly usage data
        var monthlyUsageOpt = monthlyUsageRepository.findByApiKeyIdAndMonthYear(apiKeyId, currentMonth);
        
        Long totalCallsMonth = 0L;
        Long successfulCallsMonth = 0L;
        Long failedCallsMonth = 0L;
        Long quotaLimit = 0L;
        Double usagePercentage = 0.0;

        if (monthlyUsageOpt.isPresent()) {
            var usage = monthlyUsageOpt.get();
            totalCallsMonth = usage.getTotalCalls().longValue();
            successfulCallsMonth = usage.getSuccessfulCalls().longValue();
            failedCallsMonth = usage.getFailedCalls().longValue();
            quotaLimit = usage.getQuotaLimit() != null ? usage.getQuotaLimit().longValue() : 0L;
            usagePercentage = usage.getQuotaUsagePercentage();
        }

        // Get last used timestamp
        LocalDateTime lastUsed = requestLogRepository.findLastRequestTimestamp(apiKeyId);

        // Determine status
        String status = determineApiKeyStatus(apiKey, totalCallsMonth, quotaLimit, lastUsed);

        // Build sub-DTOs
        SingleApiKeyDashboardDTO.MonthlyMetricsDTO monthlyMetrics = buildMonthlyMetrics(
            totalCallsMonth, successfulCallsMonth, failedCallsMonth, quotaLimit, usagePercentage);

        SingleApiKeyDashboardDTO.PerformanceMetricsDTO performanceMetrics = buildPerformanceMetricsRealTime(apiKeyId);

        SingleApiKeyDashboardDTO.RateLimitInfoDTO rateLimitInfo = buildRateLimitInfo(
            apiKey.getRateLimitTier().name(), apiKeyId);

        return SingleApiKeyDashboardDTO.builder()
                .apiKeyId(apiKeyId)
                .apiKeyName(apiKey.getName())
                .registeredDomain(apiKey.getRegisteredDomain())
                .requestsToday(requestsToday)
                .requestsYesterday(requestsYesterday)
                .todayVsYesterdayChange(todayVsYesterdayChange)
                .pendingRequests(pendingRequests)
                .usagePercentage(usagePercentage)
                .lastUsed(lastUsed)
                .status(status)
                .monthlyMetrics(monthlyMetrics)
                .performanceMetrics(performanceMetrics)
                .rateLimitInfo(rateLimitInfo)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Build monthly metrics DTO
     */
    private SingleApiKeyDashboardDTO.MonthlyMetricsDTO buildMonthlyMetrics(
            Long totalCalls, Long successfulCalls, Long failedCalls, Long quotaLimit, Double usagePercentage) {
        
        totalCalls = totalCalls != null ? totalCalls : 0L;
        successfulCalls = successfulCalls != null ? successfulCalls : 0L;
        failedCalls = failedCalls != null ? failedCalls : 0L;
        quotaLimit = quotaLimit != null ? quotaLimit : 0L;
        usagePercentage = usagePercentage != null ? usagePercentage : 0.0;

        Long remainingQuota = quotaLimit > 0 ? Math.max(0, quotaLimit - totalCalls) : -1L;
        Double successRate = totalCalls > 0 ? (successfulCalls.doubleValue() / totalCalls.doubleValue()) * 100.0 : 0.0;
        
        Integer estimatedDays = estimateDaysToQuotaExhaustion(remainingQuota, totalCalls);
        String quotaStatus = determineQuotaStatus(usagePercentage, remainingQuota);

        return SingleApiKeyDashboardDTO.MonthlyMetricsDTO.builder()
                .totalCalls(totalCalls)
                .successfulCalls(successfulCalls)
                .failedCalls(failedCalls)
                .quotaLimit(quotaLimit)
                .remainingQuota(remainingQuota)
                .successRate(Math.round(successRate * 100.0) / 100.0)
                .estimatedDaysToQuotaExhaustion(estimatedDays)
                .quotaStatus(quotaStatus)
                .build();
    }

    /**
     * Build performance metrics DTO from summary
     */
    private SingleApiKeyDashboardDTO.PerformanceMetricsDTO buildPerformanceMetrics(
            Double avgResponseTime, Double errorRate24h, LocalDateTime lastUsed) {
        
        avgResponseTime = avgResponseTime != null ? avgResponseTime : 0.0;
        errorRate24h = errorRate24h != null ? errorRate24h : 0.0;
        
        Double uptime = calculateUptime(errorRate24h);
        String performanceStatus = determinePerformanceStatus(avgResponseTime, errorRate24h);

        return SingleApiKeyDashboardDTO.PerformanceMetricsDTO.builder()
                .averageResponseTime(Math.round(avgResponseTime * 100.0) / 100.0)
                .errorRate24h(Math.round(errorRate24h * 100.0) / 100.0)
                .uptime(Math.round(uptime * 100.0) / 100.0)
                .performanceStatus(performanceStatus)
                .lastError(null) // Would need additional query
                .consecutiveSuccessfulCalls(0L) // Would need additional query
                .build();
    }

    /**
     * Build performance metrics in real-time
     */
    private SingleApiKeyDashboardDTO.PerformanceMetricsDTO buildPerformanceMetricsRealTime(UUID apiKeyId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime oneDayAgo = now.minusDays(1);

        // Get average response time (last 7 days)
        Double avgResponseTime = requestLogRepository.getAverageResponseTimeByApiKeyIdAndRequestTimestampBetween(apiKeyId, sevenDaysAgo, now);
        avgResponseTime = avgResponseTime != null ? avgResponseTime : 0.0;

        // Calculate error rate (last 24 hours)
        long totalRequests24h = requestLogRepository.countByApiKeyIdAndRequestTimestampBetween(apiKeyId, oneDayAgo, now);
        long failedRequests24h = requestLogRepository.countByApiKeyIdAndRequestTimestampBetweenAndSuccess(apiKeyId, oneDayAgo, now, false);
        
        Double errorRate24h = totalRequests24h > 0 ? (failedRequests24h * 100.0) / totalRequests24h : 0.0;
        Double uptime = calculateUptime(errorRate24h);
        String performanceStatus = determinePerformanceStatus(avgResponseTime, errorRate24h);

        return SingleApiKeyDashboardDTO.PerformanceMetricsDTO.builder()
                .averageResponseTime(Math.round(avgResponseTime * 100.0) / 100.0)
                .errorRate24h(Math.round(errorRate24h * 100.0) / 100.0)
                .uptime(Math.round(uptime * 100.0) / 100.0)
                .performanceStatus(performanceStatus)
                .lastError(null) // Could be implemented with additional query
                .consecutiveSuccessfulCalls(0L) // Could be implemented with additional query
                .build();
    }

    /**
     * Build rate limit info DTO
     */
    private SingleApiKeyDashboardDTO.RateLimitInfoDTO buildRateLimitInfo(String rateLimitTier, UUID apiKeyId) {
        // Get current usage stats if available
        var currentUsage = usageStatsRepository.findCurrentUsageStats(apiKeyId, LocalDateTime.now());
        
        Integer currentWindowRequests = 0;
        Integer windowLimit = getRateLimitForTier(rateLimitTier);
        LocalDateTime windowResetTime = LocalDateTime.now().plusHours(1); // Default
        String rateLimitStatus = "normal";
        Double rateLimitUtilization = 0.0;

        if (currentUsage.isPresent()) {
            var usage = currentUsage.get();
            currentWindowRequests = usage.getRequestCount();
            windowLimit = usage.getRequestLimit();
            windowResetTime = usage.getWindowEnd();
            rateLimitUtilization = usage.getUsagePercentage();
            rateLimitStatus = determineRateLimitStatus(rateLimitUtilization, usage.isRateLimitExceeded());
        }

        return SingleApiKeyDashboardDTO.RateLimitInfoDTO.builder()
                .tier(rateLimitTier)
                .currentWindowRequests(currentWindowRequests)
                .windowLimit(windowLimit)
                .windowResetTime(windowResetTime)
                .rateLimitStatus(rateLimitStatus)
                .rateLimitUtilization(Math.round(rateLimitUtilization * 100.0) / 100.0)
                .build();
    }

    // Helper methods

    private Double calculatePercentageChange(Long current, Long previous) {
        if (previous == null || previous == 0) {
            return current != null && current > 0 ? 100.0 : 0.0;
        }
        if (current == null) current = 0L;
        return ((current.doubleValue() - previous.doubleValue()) / previous.doubleValue()) * 100.0;
    }

    private String determineApiKeyStatus(ApiKey apiKey, Long totalCalls, Long quotaLimit, LocalDateTime lastUsed) {
        if (!apiKey.getIsActive()) return "inactive";
        if (quotaLimit > 0 && totalCalls >= quotaLimit) return "quota_exceeded";
        if (lastUsed != null && lastUsed.isAfter(LocalDateTime.now().minusDays(7))) return "active";
        return "dormant";
    }

    private Integer estimateDaysToQuotaExhaustion(Long remainingQuota, Long totalCalls) {
        if (remainingQuota == -1) return -1; // Unlimited
        if (remainingQuota == 0) return 0;
        if (totalCalls == 0) return Integer.MAX_VALUE;
        
        // Estimate based on current month usage
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        double dailyAverage = totalCalls.doubleValue() / dayOfMonth;
        
        if (dailyAverage <= 0) return Integer.MAX_VALUE;
        return (int) Math.ceil(remainingQuota / dailyAverage);
    }

    private String determineQuotaStatus(Double usagePercentage, Long remainingQuota) {
        if (remainingQuota == -1) return "unlimited";
        if (remainingQuota == 0) return "exceeded";
        if (usagePercentage > 90) return "critical";
        if (usagePercentage > 75) return "warning";
        return "healthy";
    }

    private Double calculateUptime(Double errorRate) {
        return Math.max(0.0, 100.0 - errorRate);
    }

    private String determinePerformanceStatus(Double avgResponseTime, Double errorRate) {
        if (errorRate > 10 || avgResponseTime > 2000) return "poor";
        if (errorRate > 5 || avgResponseTime > 1000) return "fair";
        if (errorRate < 1 && avgResponseTime < 500) return "excellent";
        return "good";
    }

    private String determineRateLimitStatus(Double utilization, boolean isRateLimited) {
        if (isRateLimited) return "rate_limited";
        if (utilization > 80) return "approaching_limit";
        return "normal";
    }

    private Integer getRateLimitForTier(String tier) {
        return switch (tier) {
            case "FREE_TIER" -> 1000;
            case "PRO_TIER" -> 10000;
            case "BUSINESS_TIER" -> 100000;
            case "UNLIMITED" -> -1;
            default -> 1000;
        };
    }

    /**
     * Force refresh dashboard data for API key
     */
    @org.springframework.cache.annotation.CacheEvict(value = "apiKeyDashboard", key = "#apiKeyId + '_' + #userId", cacheManager = "dashboardCacheManager")
    public SingleApiKeyDashboardDTO refreshApiKeyDashboard(UUID apiKeyId, String userId) {
        log.info("Force refreshing dashboard for API key: {} (user: {})", apiKeyId, userId);
        
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByIdAndUserFkId(apiKeyId, userId);
        if (apiKeyOpt.isEmpty()) {
            return null;
        }
        
        return calculateRealTimeDashboard(apiKeyOpt.get());
    }
}