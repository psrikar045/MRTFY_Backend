package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.RivoFetchRequestLog;
import com.example.jwtauthenticator.repository.RivoFetchRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 📊 RivoFetch Analytics Service
 * 
 * Provides comprehensive analytics and statistics for RivoFetch API usage.
 * This service aggregates data from RivoFetchRequestLog to provide insights
 * into user behavior, API performance, and system health.
 * 
 * Features:
 * - User-specific request statistics
 * - API key performance metrics
 * - Success/failure rate analysis
 * - Cache performance analytics
 * - Error distribution analysis
 * - Performance percentile calculations
 * 
 * @author BrandSnap API Team
 * @version 1.0
 * @since Java 21
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RivoFetchAnalyticsService {
    
    private final RivoFetchRequestLogRepository rivoFetchRepository;
    
    // ==================== USER STATISTICS ====================
    
    /**
     * 🔢 Get total request count for a user (High Performance)
     */
    public Map<String, Object> getUserTotalRequests(String userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("🔢 Getting total request count for user: {} (last {} hours)", userId, hours);
        
        // High-performance database count query
        long totalRequests = rivoFetchRepository.countByRivoFetchUserIdAndRivoFetchTimestampAfter(userId, since);
        
        return Map.of(
            "userId", userId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests
        );
    }
    
    /**
     * ✅ Get successful request count for a user (High Performance)
     */
    public Map<String, Object> getUserSuccessfulRequests(String userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("✅ Getting successful request count for user: {} (last {} hours)", userId, hours);
        
        // High-performance database count queries
        long totalRequests = rivoFetchRepository.countByRivoFetchUserIdAndRivoFetchTimestampAfter(userId, since);
        long successfulRequests = rivoFetchRepository.countByRivoFetchUserIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            userId, true, since);
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "userId", userId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests,
            "successfulRequests", successfulRequests,
            "successRate", Math.round(successRate * 100.0) / 100.0
        );
    }
    
    /**
     * ❌ Get failed request count for a user (High Performance)
     */
    public Map<String, Object> getUserFailedRequests(String userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("❌ Getting failed request count for user: {} (last {} hours)", userId, hours);
        
        // High-performance database count queries
        long totalRequests = rivoFetchRepository.countByRivoFetchUserIdAndRivoFetchTimestampAfter(userId, since);
        long failedRequests = rivoFetchRepository.countByRivoFetchUserIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            userId, false, since);
        
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "userId", userId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests,
            "failedRequests", failedRequests,
            "failureRate", Math.round(failureRate * 100.0) / 100.0
        );
    }
    
    /**
     * 📈 Get comprehensive statistics for a user
     */
    public Map<String, Object> getUserStatistics(String userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("📊 Getting RivoFetch statistics for user: {} (last {} hours)", userId, hours);
        
        // Get user's logs
        Page<RivoFetchRequestLog> userLogs = rivoFetchRepository
            .findByRivoFetchUserIdOrderByRivoFetchTimestampDesc(userId, PageRequest.of(0, 1000));
        
        List<RivoFetchRequestLog> recentLogs = userLogs.getContent().stream()
            .filter(log -> log.getRivoFetchTimestamp().isAfter(since))
            .collect(Collectors.toList());
        
        // Calculate statistics
        long totalRequests = recentLogs.size();
        long successfulRequests = recentLogs.stream()
            .mapToLong(log -> log.getRivoFetchSuccess() ? 1 : 0)
            .sum();
        long failedRequests = totalRequests - successfulRequests;
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        // Cache performance
        Map<String, Long> cacheStats = recentLogs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getRivoFetchCacheHitType() != null ? log.getRivoFetchCacheHitType() : "UNKNOWN",
                Collectors.counting()
            ));
        
        // Average response time
        double avgResponseTime = recentLogs.stream()
            .filter(log -> log.getRivoFetchTotalDurationMs() != null)
            .mapToDouble(log -> log.getRivoFetchTotalDurationMs())
            .average()
            .orElse(0.0);
        
        // Top domains
        List<Map<String, Object>> topDomains = recentLogs.stream()
            .filter(log -> log.getRivoFetchUrlDomain() != null)
            .collect(Collectors.groupingBy(
                RivoFetchRequestLog::getRivoFetchUrlDomain,
                Collectors.counting()
            ))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .map(entry -> {
                Map<String, Object> domainMap = new HashMap<>();
                domainMap.put("domain", entry.getKey());
                domainMap.put("requestCount", entry.getValue());
                return domainMap;
            })
            .collect(Collectors.toList());
        
        return Map.of(
            "userId", userId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "requestCounts", Map.of(
                "total", totalRequests,
                "successful", successfulRequests,
                "failed", failedRequests
            ),
            "rates", Map.of(
                "successRate", Math.round(successRate * 100.0) / 100.0,
                "failureRate", Math.round(failureRate * 100.0) / 100.0
            ),
            "performance", Map.of(
                "averageResponseTimeMs", Math.round(avgResponseTime * 100.0) / 100.0
            ),
            "cachePerformance", cacheStats,
            "topDomains", topDomains
        );
    }
    
    /**
     * 📊 Get success rate for a user
     */
    public Map<String, Object> getUserSuccessRate(String userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        Page<RivoFetchRequestLog> userLogs = rivoFetchRepository
            .findByRivoFetchUserIdOrderByRivoFetchTimestampDesc(userId, PageRequest.of(0, 1000));
        
        List<RivoFetchRequestLog> recentLogs = userLogs.getContent().stream()
            .filter(log -> log.getRivoFetchTimestamp().isAfter(since))
            .collect(Collectors.toList());
        
        long totalRequests = recentLogs.size();
        long successfulRequests = recentLogs.stream()
            .mapToLong(log -> log.getRivoFetchSuccess() ? 1 : 0)
            .sum();
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "userId", userId,
            "timeRange", Map.of("hours", hours, "since", since),
            "totalRequests", totalRequests,
            "successfulRequests", successfulRequests,
            "successRate", Math.round(successRate * 100.0) / 100.0
        );
    }
    
    /**
     * 📉 Get failure rate for a user
     */
    public Map<String, Object> getUserFailureRate(String userId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        Page<RivoFetchRequestLog> userLogs = rivoFetchRepository
            .findByRivoFetchUserIdOrderByRivoFetchTimestampDesc(userId, PageRequest.of(0, 1000));
        
        List<RivoFetchRequestLog> recentLogs = userLogs.getContent().stream()
            .filter(log -> log.getRivoFetchTimestamp().isAfter(since))
            .collect(Collectors.toList());
        
        long totalRequests = recentLogs.size();
        long failedRequests = recentLogs.stream()
            .mapToLong(log -> !log.getRivoFetchSuccess() ? 1 : 0)
            .sum();
        
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        // Get error breakdown
        Map<String, Long> errorBreakdown = recentLogs.stream()
            .filter(log -> !log.getRivoFetchSuccess())
            .collect(Collectors.groupingBy(
                log -> log.getRivoFetchErrorMessage() != null ? log.getRivoFetchErrorMessage() : "Unknown Error",
                Collectors.counting()
            ));
        
        return Map.of(
            "userId", userId,
            "timeRange", Map.of("hours", hours, "since", since),
            "totalRequests", totalRequests,
            "failedRequests", failedRequests,
            "failureRate", Math.round(failureRate * 100.0) / 100.0,
            "errorBreakdown", errorBreakdown
        );
    }
    
    // ==================== API KEY STATISTICS ====================
    
    /**
     * 🔢 Get total request count for an API key (High Performance)
     */
    public Map<String, Object> getApiKeyTotalRequests(UUID apiKeyId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("🔢 Getting total request count for API key: {} (last {} hours)", apiKeyId, hours);
        
        // High-performance database count query
        long totalRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchTimestampAfter(apiKeyId, since);
        
        return Map.of(
            "apiKeyId", apiKeyId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests
        );
    }
    
    /**
     * ✅ Get successful request count for an API key (High Performance)
     */
    public Map<String, Object> getApiKeySuccessfulRequests(UUID apiKeyId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("✅ Getting successful request count for API key: {} (last {} hours)", apiKeyId, hours);
        
        // High-performance database count queries
        long totalRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchTimestampAfter(apiKeyId, since);
        long successfulRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            apiKeyId, true, since);
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "apiKeyId", apiKeyId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests,
            "successfulRequests", successfulRequests,
            "successRate", Math.round(successRate * 100.0) / 100.0
        );
    }
    
    /**
     * ❌ Get failed request count for an API key (High Performance)
     */
    public Map<String, Object> getApiKeyFailedRequests(UUID apiKeyId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("❌ Getting failed request count for API key: {} (last {} hours)", apiKeyId, hours);
        
        // High-performance database count queries
        long totalRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchTimestampAfter(apiKeyId, since);
        long failedRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            apiKeyId, false, since);
        
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "apiKeyId", apiKeyId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests,
            "failedRequests", failedRequests,
            "failureRate", Math.round(failureRate * 100.0) / 100.0
        );
    }
    
    /**
     * 📈 Get comprehensive statistics for an API key
     */
    public Map<String, Object> getApiKeyStatistics(UUID apiKeyId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("📊 Getting RivoFetch statistics for API key: {} (last {} hours)", apiKeyId, hours);
        
        // Use repository methods for better performance
        long totalRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchTimestampAfter(apiKeyId, since);
        long successfulRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            apiKeyId, true, since);
        long failedRequests = rivoFetchRepository.countByRivoFetchApiKeyIdAndRivoFetchSuccessAndRivoFetchTimestampAfter(
            apiKeyId, false, since);
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        // Get cache hit rate
        Optional<Double> cacheHitRate = rivoFetchRepository.getCacheHitRateByApiKey(apiKeyId, since);
        
        // Get average response time
        Optional<Double> avgResponseTime = rivoFetchRepository.getAverageResponseTimeByApiKey(apiKeyId, since);
        
        return Map.of(
            "apiKeyId", apiKeyId,
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "requestCounts", Map.of(
                "total", totalRequests,
                "successful", successfulRequests,
                "failed", failedRequests
            ),
            "rates", Map.of(
                "successRate", Math.round(successRate * 100.0) / 100.0,
                "failureRate", Math.round(failureRate * 100.0) / 100.0,
                "cacheHitRate", cacheHitRate.map(rate -> Math.round(rate * 100.0) / 100.0).orElse(0.0)
            ),
            "performance", Map.of(
                "averageResponseTimeMs", avgResponseTime.map(time -> Math.round(time * 100.0) / 100.0).orElse(0.0)
            )
        );
    }
    
    /**
     * 🚀 Get performance metrics for an API key
     */
    public Map<String, Object> getApiKeyPerformance(UUID apiKeyId, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // Get recent logs for detailed analysis
        List<RivoFetchRequestLog> recentLogs = rivoFetchRepository
            .findByRivoFetchApiKeyIdAndRivoFetchTimestampAfterOrderByRivoFetchTimestampDesc(apiKeyId, since);
        
        // Performance percentiles
        List<Long> responseTimes = recentLogs.stream()
            .filter(log -> log.getRivoFetchTotalDurationMs() != null)
            .map(RivoFetchRequestLog::getRivoFetchTotalDurationMs)
            .sorted()
            .collect(Collectors.toList());
        
        Map<String, Object> percentiles = new HashMap<>();
        if (!responseTimes.isEmpty()) {
            percentiles.put("p50", getPercentile(responseTimes, 0.50));
            percentiles.put("p90", getPercentile(responseTimes, 0.90));
            percentiles.put("p95", getPercentile(responseTimes, 0.95));
            percentiles.put("p99", getPercentile(responseTimes, 0.99));
        }
        
        // Cache performance breakdown
        Map<String, Long> cacheBreakdown = recentLogs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getRivoFetchCacheHitType() != null ? log.getRivoFetchCacheHitType() : "UNKNOWN",
                Collectors.counting()
            ));
        
        return Map.of(
            "apiKeyId", apiKeyId,
            "timeRange", Map.of("hours", hours, "since", since),
            "totalRequests", recentLogs.size(),
            "responseTimePercentiles", percentiles,
            "cachePerformance", cacheBreakdown
        );
    }
    
    // ==================== APPLICATION-WIDE STATISTICS (NO AUTH) ====================
    
    /**
     * 🔢 Get ALL application request count from RivoFetch logs (High Performance - No Auth Required)
     */
    public Map<String, Object> getAllApplicationTotalRequests() {
        log.info("🔢 Getting ALL application total request count from RivoFetch logs");
        
        // Count ALL records in rivo_fetch_request_logs table
        long totalRequests = rivoFetchRepository.count();
        
        return Map.of(
            "scope", "all_application_requests",
            "source", "rivo_fetch_request_logs",
            "totalRequests", totalRequests,
            "retrievedAt", LocalDateTime.now()
        );
    }
    
    /**
     * ✅ Get ALL application successful request count from RivoFetch logs (High Performance - No Auth Required)
     */
    public Map<String, Object> getAllApplicationSuccessfulRequests() {
        log.info("✅ Getting ALL application successful request count from RivoFetch logs");
        
        // Count ALL records and successful records in rivo_fetch_request_logs table
        long totalRequests = rivoFetchRepository.count();
        long successfulRequests = rivoFetchRepository.countByRivoFetchSuccess(true);
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "scope", "all_application_requests",
            "source", "rivo_fetch_request_logs",
            "totalRequests", totalRequests,
            "successfulRequests", successfulRequests,
            "successRate", Math.round(successRate * 100.0) / 100.0,
            "retrievedAt", LocalDateTime.now()
        );
    }
    
    /**
     * ❌ Get ALL application failed request count from RivoFetch logs (High Performance - No Auth Required)
     */
    public Map<String, Object> getAllApplicationFailedRequests() {
        log.info("❌ Getting ALL application failed request count from RivoFetch logs");
        
        // Count ALL records and failed records in rivo_fetch_request_logs table
        long totalRequests = rivoFetchRepository.count();
        long failedRequests = rivoFetchRepository.countByRivoFetchSuccess(false);
        
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "scope", "all_application_requests",
            "source", "rivo_fetch_request_logs",
            "totalRequests", totalRequests,
            "failedRequests", failedRequests,
            "failureRate", Math.round(failureRate * 100.0) / 100.0,
            "retrievedAt", LocalDateTime.now()
        );
    }
    
    /**
     * 📊 Get ALL application statistics (Combined - High Performance - No Auth Required)
     * This method combines total, successful, and failed request counts in a single response
     */
    public Map<String, Object> getAllApplicationStatistics() {
        log.info("📊 Getting ALL application statistics from RivoFetch logs - COMBINED");
        
        // Single database queries for maximum performance
        long totalRequests = rivoFetchRepository.count();
        long successfulRequests = rivoFetchRepository.countByRivoFetchSuccess(true);
        long failedRequests = rivoFetchRepository.countByRivoFetchSuccess(false);
        
        // Calculate rates
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        // Flat structure response
        return Map.of(
            "scope", "all_application_requests",
            "source", "rivo_fetch_request_logs",
            "totalRequests", totalRequests,
            "successfulRequests", successfulRequests,
            "failedRequests", failedRequests,
            "successRate", Math.round(successRate * 100.0) / 100.0,
            "failureRate", Math.round(failureRate * 100.0) / 100.0,
            "retrievedAt", LocalDateTime.now()
        );
    }
    
    // ==================== SYSTEM STATISTICS ====================
    
    /**
     * 🔢 Get system-wide total request count (High Performance - No Auth Required)
     */
    public Map<String, Object> getSystemTotalRequests(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("🔢 Getting system-wide total request count (last {} hours)", hours);
        
        // High-performance database count query
        long totalRequests = rivoFetchRepository.countByRivoFetchTimestampAfter(since);
        
        return Map.of(
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests
        );
    }
    
    /**
     * ✅ Get system-wide successful request count (High Performance - No Auth Required)
     */
    public Map<String, Object> getSystemSuccessfulRequests(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("✅ Getting system-wide successful request count (last {} hours)", hours);
        
        // High-performance database count queries
        long totalRequests = rivoFetchRepository.countByRivoFetchTimestampAfter(since);
        long successfulRequests = rivoFetchRepository.countByRivoFetchSuccessAndRivoFetchTimestampAfter(true, since);
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests,
            "successfulRequests", successfulRequests,
            "successRate", Math.round(successRate * 100.0) / 100.0
        );
    }
    
    /**
     * ❌ Get system-wide failed request count (High Performance - No Auth Required)
     */
    public Map<String, Object> getSystemFailedRequests(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("❌ Getting system-wide failed request count (last {} hours)", hours);
        
        // High-performance database count queries
        long totalRequests = rivoFetchRepository.countByRivoFetchTimestampAfter(since);
        long failedRequests = rivoFetchRepository.countByRivoFetchSuccessAndRivoFetchTimestampAfter(false, since);
        
        double failureRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        
        return Map.of(
            "timeRange", Map.of(
                "hours", hours,
                "since", since,
                "until", LocalDateTime.now()
            ),
            "totalRequests", totalRequests,
            "failedRequests", failedRequests,
            "failureRate", Math.round(failureRate * 100.0) / 100.0
        );
    }
    
    /**
     * 🌐 Get system-wide overview
     */
    public Map<String, Object> getSystemOverview(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        log.info("📊 Getting system-wide RivoFetch overview (last {} hours)", hours);
        
        long totalRequests = rivoFetchRepository.countByRivoFetchTimestampAfter(since);
        long successfulRequests = rivoFetchRepository.countByRivoFetchSuccessAndRivoFetchTimestampAfter(true, since);
        long failedRequests = rivoFetchRepository.countByRivoFetchSuccessAndRivoFetchTimestampAfter(false, since);
        
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        
        // Get performance metrics
        List<Object[]> performanceMetrics = rivoFetchRepository.getPerformanceMetricsByCacheType(since);
        
        // Get top domains
        List<Object[]> topDomains = rivoFetchRepository.getTopDomainsByRequestCount(since, PageRequest.of(0, 10));
        
        // Get most active API keys
        List<Object[]> activeApiKeys = rivoFetchRepository.getMostActiveApiKeys(since, PageRequest.of(0, 10));
        
        return Map.of(
            "timeRange", Map.of("hours", hours, "since", since),
            "overview", Map.of(
                "totalRequests", totalRequests,
                "successfulRequests", successfulRequests,
                "failedRequests", failedRequests,
                "successRate", Math.round(successRate * 100.0) / 100.0
            ),
            "performanceMetrics", formatPerformanceMetrics(performanceMetrics),
            "topDomains", formatTopDomains(topDomains),
            "mostActiveApiKeys", formatActiveApiKeys(activeApiKeys)
        );
    }
    
    /**
     * 🚀 Get cache performance analysis
     */
    public Map<String, Object> getCachePerformance(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<Object[]> performanceMetrics = rivoFetchRepository.getPerformanceMetricsByCacheType(since);
        
        return Map.of(
            "timeRange", Map.of("hours", hours, "since", since),
            "cachePerformance", formatPerformanceMetrics(performanceMetrics)
        );
    }
    
    /**
     * 🔍 Get error analysis
     */
    public Map<String, Object> getErrorAnalysis(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<Object[]> errorDistribution = rivoFetchRepository.getErrorDistribution(since);
        
        return Map.of(
            "timeRange", Map.of("hours", hours, "since", since),
            "errorDistribution", formatErrorDistribution(errorDistribution)
        );
    }
    
    // ==================== HELPER METHODS ====================
    
    private long getPercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }
    
    private List<Map<String, Object>> formatPerformanceMetrics(List<Object[]> metrics) {
        return metrics.stream()
            .map(row -> Map.of(
                "cacheHitType", row[0] != null ? row[0] : "UNKNOWN",
                "requestCount", row[1] != null ? row[1] : 0,
                "avgTotalDuration", row[2] != null ? Math.round(((Number) row[2]).doubleValue() * 100.0) / 100.0 : 0.0,
                "avgExternalApiDuration", row[3] != null ? Math.round(((Number) row[3]).doubleValue() * 100.0) / 100.0 : 0.0,
                "successRate", row[4] != null ? row[4] : 0.0
            ))
            .collect(Collectors.toList());
    }
    
    private List<Map<String, Object>> formatTopDomains(List<Object[]> domains) {
        return domains.stream()
            .map(row -> Map.of(
                "domain", row[0] != null ? row[0] : "Unknown",
                "requestCount", row[1] != null ? row[1] : 0
            ))
            .collect(Collectors.toList());
    }
    
    private List<Map<String, Object>> formatActiveApiKeys(List<Object[]> apiKeys) {
        return apiKeys.stream()
            .map(row -> Map.of(
                "apiKeyId", row[0] != null ? row[0] : "Unknown",
                "requestCount", row[1] != null ? row[1] : 0
            ))
            .collect(Collectors.toList());
    }
    
    private List<Map<String, Object>> formatErrorDistribution(List<Object[]> errors) {
        return errors.stream()
            .map(row -> Map.of(
                "statusCode", row[0] != null ? row[0] : "Unknown",
                "errorCount", row[1] != null ? row[1] : 0,
                "errorMessage", row[2] != null ? row[2] : "Unknown Error"
            ))
            .collect(Collectors.toList());
    }
}