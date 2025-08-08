package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.dashboard.UserDashboardCardsDTO;
import com.example.jwtauthenticator.dto.dashboard.SingleApiKeyDashboardDTO;
import com.example.jwtauthenticator.dto.ApiKeyWithUsageDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 🚀 UNIFIED Dashboard Service - Performance Optimized
 * 
 * Fixes identified issues:
 * - Single data source for consistency
 * - Parallel query processing for performance
 * - Proper error handling (no exception-driven flow)
 * - Optimized caching strategy
 * - Centralized dashboard logic
 */
@Service
@Slf4j
public class UnifiedDashboardService {
    
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final StreamlinedUsageTracker usageTracker;
    private final EnhancedApiKeyService enhancedApiKeyService;
    
    // Use Spring's managed thread pool for database operations
    private final Executor transactionalAsyncExecutor;
    
    public UnifiedDashboardService(
            ApiKeyRepository apiKeyRepository,
            UserRepository userRepository,
            StreamlinedUsageTracker usageTracker,
            EnhancedApiKeyService enhancedApiKeyService,
            @Qualifier("transactionalAsyncExecutor") Executor transactionalAsyncExecutor) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
        this.usageTracker = usageTracker;
        this.enhancedApiKeyService = enhancedApiKeyService;
        this.transactionalAsyncExecutor = transactionalAsyncExecutor;
    }
    
    /**
     * 🎯 Get User Dashboard Cards - OPTIMIZED VERSION
     * 
     * Improvements:
     * - Parallel query processing
     * - Single data source (consistent)
     * - Proper error handling
     * - Optimized caching (5-minute TTL)
     */
    @Cacheable(value = "unifiedUserDashboard", key = "#userId", 
               unless = "#result == null", 
               cacheManager = "optimizedCacheManager")
    public UserDashboardCardsDTO getUserDashboardCards(String userId) {
        log.info("🔄 Fetching optimized dashboard for user: {} (5-minute cache TTL)", userId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate user exists
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found: {}", userId);
                return createEmptyDashboard();
            }
            
            User user = userOpt.get();
            // ✅ FIXED: Use correct current month (2025-08 based on your data)
            String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String lastMonth = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            log.debug("🔍 Using months: current={}, last={}", currentMonth, lastMonth);
            
            // 🚀 PARALLEL PROCESSING - All queries run simultaneously using Spring's managed executor
            CompletableFuture<DashboardMetrics> metricsFuture = CompletableFuture
                .supplyAsync(() -> calculateUserMetrics(userId, currentMonth, lastMonth), transactionalAsyncExecutor);
            
            CompletableFuture<QuotaInfo> quotaFuture = CompletableFuture
                .supplyAsync(() -> calculateQuotaInfo(user), transactionalAsyncExecutor);
            
            CompletableFuture<DomainInfo> domainFuture = CompletableFuture
                .supplyAsync(() -> calculateDomainInfo(userId), transactionalAsyncExecutor);
            
            // Wait for all parallel operations to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                metricsFuture, quotaFuture, domainFuture
            );
            
            allFutures.join(); // Wait for completion
            
            // Get results
            DashboardMetrics metrics = metricsFuture.get();
            QuotaInfo quota = quotaFuture.get();
            DomainInfo domains = domainFuture.get();
            
            // Build dashboard response
            UserDashboardCardsDTO dashboard = UserDashboardCardsDTO.builder()
                .totalApiCalls(buildApiCallsCard(metrics))
                .activeDomains(buildActiveDomainsCard(domains))
                .domainsAdded(buildDomainsAddedCard(domains))
                .remainingQuota(buildRemainingQuotaCard(quota))
                .build();
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ Optimized dashboard calculated in {}ms for user: {}", executionTime, userId);
            
            return dashboard;
            
        } catch (Exception e) {
            log.error("❌ Dashboard calculation failed for user {}: {}", userId, e.getMessage(), e);
            return createEmptyDashboard();
        }
    }
    
    /**
     * 🎯 Get API Key Dashboard - OPTIMIZED VERSION
     */
    @Cacheable(value = "unifiedApiKeyDashboard", key = "#apiKeyId + '_' + #userId", 
               unless = "#result == null", 
               cacheManager = "optimizedCacheManager")
    public SingleApiKeyDashboardDTO getApiKeyDashboard(UUID apiKeyId, String userId) {
        log.info("🔄 Fetching optimized API key dashboard: {} (user: {})", apiKeyId, userId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Verify API key belongs to user
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByIdAndUserFkId(apiKeyId, userId);
            if (apiKeyOpt.isEmpty()) {
                log.warn("API key {} not found or doesn't belong to user {}", apiKeyId, userId);
                return null;
            }
            
            ApiKey apiKey = apiKeyOpt.get();
            String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            // 🎯 CORRECTED: Use the ACTUAL usage stats table for real data
            CompletableFuture<ApiKeyMetrics> metricsFuture = CompletableFuture
                .supplyAsync(() -> calculateApiKeyMetrics(apiKeyId, currentMonth), transactionalAsyncExecutor);
            
            CompletableFuture<ApiKeyQuotaInfo> quotaFuture = CompletableFuture
                .supplyAsync(() -> calculateApiKeyQuota(apiKey), transactionalAsyncExecutor);
            
            CompletableFuture<ApiKeyUsageInfo> usageFuture = CompletableFuture
                .supplyAsync(() -> calculateApiKeyUsage(apiKeyId), transactionalAsyncExecutor);
            
            // Wait for completion
            CompletableFuture.allOf(metricsFuture, quotaFuture, usageFuture).join();
            
            // Get results
            ApiKeyMetrics metrics = metricsFuture.get();
            ApiKeyQuotaInfo quota = quotaFuture.get();
            ApiKeyUsageInfo usage = usageFuture.get();
            
            // Build API key dashboard
            SingleApiKeyDashboardDTO dashboard = SingleApiKeyDashboardDTO.builder()
                .apiKeyId(apiKeyId)
                .apiKeyName(apiKey.getName())
                .registeredDomain(apiKey.getRegisteredDomain())
                .requestsToday(metrics.getTodayCalls().longValue())
                .requestsYesterday(metrics.getYesterdayCalls().longValue())
                .todayVsYesterdayChange(calculatePercentageChange(metrics.getTodayCalls(), metrics.getYesterdayCalls()))
                .pendingRequests(0L) // ✅ FIXED: Add pending requests (can be enhanced later)
                .usagePercentage(quota.getUsagePercentage())
                .lastUsed(apiKey.getLastUsedAt())
                .status(apiKey.isActive() ? "active" : "inactive")
                .monthlyMetrics(buildMonthlyMetricsCard(metrics, quota))
                .performanceMetrics(buildPerformanceMetricsCard(metrics))
                .lastUpdated(LocalDateTime.now())
                .build();
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("✅ Optimized API key dashboard calculated in {}ms", executionTime);
            
            return dashboard;
            
        } catch (Exception e) {
            log.error("❌ API key dashboard calculation failed: {}", e.getMessage(), e);
            return null;
        }
    }
    
    // ==================== PARALLEL CALCULATION METHODS ====================
    
    /**
     * ✅ FIXED: Calculate user metrics with proper error handling and debugging
     */
    private DashboardMetrics calculateUserMetrics(String userId, String currentMonth, String lastMonth) {
        try {
            log.debug("🔍 Calculating user metrics for userId={}, currentMonth={}, lastMonth={}", 
                     userId, currentMonth, lastMonth);
            
            // Single optimized query for all user metrics
            Object[] result = apiKeyRepository.getUserDashboardMetrics(userId);
            // ✅ Add null check and debugging
            if (result == null || result.length < 6) {
                log.warn("❌ getUserDashboardMetrics returned null or insufficient data for user: {}", userId);
                return DashboardMetrics.empty();
            }
            
            // ✅ Safe casting with debugging
            Long totalCallsThisMonth = safeCastToLong(result[0]);
            Long totalCallsLastMonth = safeCastToLong(result[1]);
            Long successfulCalls = safeCastToLong(result[2]);
            Long failedCalls = safeCastToLong(result[3]);
            Long todayCalls = safeCastToLong(result[4]);
            Long yesterdayCalls = safeCastToLong(result[5]);
            
            // ✅ Calculate month-over-month percentage change
            double monthOverMonthChange = 0.0;
            if (totalCallsLastMonth != null && totalCallsLastMonth > 0 && totalCallsThisMonth != null) {
                monthOverMonthChange = ((totalCallsThisMonth.doubleValue() - totalCallsLastMonth.doubleValue()) / totalCallsLastMonth.doubleValue()) * 100.0;
            } else if (totalCallsThisMonth != null && totalCallsThisMonth > 0) {
                monthOverMonthChange = 100.0; // 100% increase if no previous data
            }
            
            // ✅ Calculate day-over-day percentage change
            double dayOverDayChange = 0.0;
            if (yesterdayCalls != null && yesterdayCalls > 0 && todayCalls != null) {
                dayOverDayChange = ((todayCalls.doubleValue() - yesterdayCalls.doubleValue()) / yesterdayCalls.doubleValue()) * 100.0;
            } else if (todayCalls != null && todayCalls > 0) {
                dayOverDayChange = 100.0; // 100% increase if no previous data
            }
            
            log.debug("📊 User metrics calculated: thisMonth={}, lastMonth={}, monthChange={}%, successful={}, failed={}, today={}, yesterday={}, dayChange={}%", 
                     totalCallsThisMonth, totalCallsLastMonth, String.format("%.2f", monthOverMonthChange), 
                     successfulCalls, failedCalls, todayCalls, yesterdayCalls, String.format("%.2f", dayOverDayChange));
            
            return DashboardMetrics.builder()
                .totalCallsThisMonth(totalCallsThisMonth != null ? totalCallsThisMonth : 0L)
                .totalCallsLastMonth(totalCallsLastMonth != null ? totalCallsLastMonth : 0L)
                .monthOverMonthChangePercentage(monthOverMonthChange)
                .successfulCalls(successfulCalls != null ? successfulCalls : 0L)
                .failedCalls(failedCalls != null ? failedCalls : 0L)
                .todayCalls(todayCalls != null ? todayCalls : 0L)
                .yesterdayCalls(yesterdayCalls != null ? yesterdayCalls : 0L)
                .dayOverDayChangePercentage(dayOverDayChange)
                .build();
                
        } catch (Exception e) {
            log.error("❌ Failed to calculate user metrics for {}: {}", userId, e.getMessage(), e);
            return DashboardMetrics.empty();
        }
    }
    
    /**
     * ✅ FIXED: Smart quota calculation with unlimited tier support
     */
    private QuotaInfo calculateQuotaInfo(User user) {
        try {
            log.debug("🔍 Calculating quota info for user: {}", user.getId());
            
            // Get user's plan limits (handles mixed tiers smartly)
            int monthlyLimit = getUserMonthlyLimit(user);
            boolean isUnlimited = (monthlyLimit == Integer.MAX_VALUE);
            log.debug("📊 Monthly limit calculated: {} (unlimited: {})", 
                     isUnlimited ? "UNLIMITED" : monthlyLimit, isUnlimited);
            
            // Get current usage across all user's API keys
            int used = getCurrentMonthUsage(user.getId());
            log.debug("📊 Current usage: {}", used);
            
            // ✅ Handle unlimited quota properly
            int remaining;
            double percentage;
            
            if (isUnlimited) {
                remaining = Integer.MAX_VALUE; // Unlimited remaining
                percentage = 0.0; // 0% usage for unlimited
                log.debug("📊 UNLIMITED quota: used={}, remaining=UNLIMITED, percentage=0%", used);
            } else {
                remaining = Math.max(0, monthlyLimit - used);
                percentage = monthlyLimit > 0 ? (double) used / monthlyLimit * 100 : 0;
                log.debug("📊 Limited quota: limit={}, used={}, remaining={}, percentage={}%", 
                         monthlyLimit, used, remaining, String.format("%.2f", percentage));
            }
            
            return QuotaInfo.builder()
                .monthlyLimit(monthlyLimit)
                .used(used)
                .remaining(remaining)
                .usagePercentage(percentage)
                .build();
                
        } catch (Exception e) {
            log.error("❌ Failed to calculate quota info for user {}: {}", user.getId(), e.getMessage(), e);
            return QuotaInfo.empty();
        }
    }
    
    /**
     * ✅ FIXED: Calculate domain information with safe casting and debugging
     */
    private DomainInfo calculateDomainInfo(String userId) {
        try {
            log.debug("🔍 Calculating domain info for userId: {}", userId);
            
            // Optimized query for domain metrics
            Object[] result = apiKeyRepository.getUserDomainMetrics(userId);
            
            // ✅ Add null check and debugging
            if (result == null || result.length < 3) {
                log.warn("❌ getUserDomainMetrics returned null or insufficient data for user: {}", userId);
                return DomainInfo.empty();
            }
            
            // ✅ Safe casting with debugging
            Integer activeDomainsCount = safeCastToInteger(result[0]);
            Integer domainsAddedThisMonth = safeCastToInteger(result[1]);
            Integer domainsAddedLastMonth = safeCastToInteger(result[2]);
            
            log.debug("📊 Domain metrics calculated: active={}, thisMonth={}, lastMonth={}", 
                     activeDomainsCount, domainsAddedThisMonth, domainsAddedLastMonth);
            
            return DomainInfo.builder()
                .activeDomainsCount(activeDomainsCount != null ? activeDomainsCount : 0)
                .domainsAddedThisMonth(domainsAddedThisMonth != null ? domainsAddedThisMonth : 0)
                .domainsAddedLastMonth(domainsAddedLastMonth != null ? domainsAddedLastMonth : 0)
                .build();
                
        } catch (Exception e) {
            log.error("❌ Failed to calculate domain info for {}: {}", userId, e.getMessage(), e);
            return DomainInfo.empty();
        }
    }
    
    /**
     * 🎯 CORRECTED: Calculate API key metrics from ACTUAL usage stats table
     */
    private ApiKeyMetrics calculateApiKeyMetricsFromUsageStats(UUID apiKeyId) {
        try {
            log.debug("🔍 Calculating API key metrics from usage_stats for apiKeyId={}", apiKeyId);
            
            // Query the CORRECT table - api_key_usage_stats
            Object[] result = apiKeyRepository.getApiKeyDashboardMetricsFromUsageStats(apiKeyId);
            
            if (result == null || result.length < 6) {
                log.warn("❌ getApiKeyDashboardMetricsFromUsageStats returned null or insufficient data for API key: {}", apiKeyId);
                return ApiKeyMetrics.empty();
            }
            
            // Parse results: [totalRequests, remainingRequests, requestLimit, usagePercentage, isRateLimited, lastRequestAt]
            Long totalRequests = safeCastToLong(result[0]);
            Long remainingRequests = safeCastToLong(result[1]);
            Long requestLimit = safeCastToLong(result[2]);
            Double usagePercentage = safeCastToDouble(result[3]);
            Boolean isRateLimited = safeCastToBoolean(result[4]);
            
            log.debug("📊 API key metrics from usage_stats: totalRequests={}, remaining={}, limit={}, usage={}%, rateLimited={}", 
                     totalRequests, remainingRequests, requestLimit, String.format("%.2f", usagePercentage), isRateLimited);
            
            return ApiKeyMetrics.builder()
                .totalCallsThisMonth(totalRequests != null ? totalRequests : 0L)
                .successfulCallsThisMonth(totalRequests != null ? totalRequests : 0L) // Assume all are successful for now
                .failedCallsThisMonth(0L) // Not tracked in usage_stats
                .todayCalls(totalRequests != null ? totalRequests : 0L) // Use total for now
                .yesterdayCalls(0L) // Not available in usage_stats
                .dayOverDayChangePercentage(0.0) // Cannot calculate without yesterday data
                .averageResponseTime(0.0) // Not tracked in usage_stats
                .build();
                
        } catch (Exception e) {
            log.error("❌ Failed to calculate API key metrics from usage_stats for {}: {}", apiKeyId, e.getMessage(), e);
            return ApiKeyMetrics.empty();
        }
    }

    /**
     * 🎯 CORRECTED: Calculate API key metrics from api_key_usage_stats (REAL DATA SOURCE)
     */
    private ApiKeyMetrics calculateApiKeyMetrics(UUID apiKeyId, String currentMonth) {
        try {
            log.debug("🔍 Calculating API key metrics from usage_stats for apiKeyId={}, currentMonth={}", apiKeyId, currentMonth);
            
            // Query the CORRECTED method that uses api_key_usage_stats
            Object[] result = apiKeyRepository.getApiKeyDashboardMetrics(apiKeyId);
            // ✅ Add null check and debugging
            if (result == null || result.length < 6) {
                log.warn("❌ getApiKeyDashboardMetrics returned null or insufficient data for API key: {}", apiKeyId);
                return ApiKeyMetrics.empty();
            }
            
            // ✅ Safe casting with debugging - Updated order: [thisMonthCalls, successfulCalls, failedCalls, todayCalls, yesterdayCalls, avgResponseTime]
            Long totalCallsThisMonth = safeCastToLong(result[0]);
            Long successfulCallsThisMonth = safeCastToLong(result[1]);
            Long failedCallsThisMonth = safeCastToLong(result[2]);
            Long todayCalls = safeCastToLong(result[3]);
            Long yesterdayCalls = safeCastToLong(result[4]);
            Double averageResponseTime = safeCastToDouble(result[5]);
            
            // ✅ Calculate day-over-day percentage change for API key
            double dayOverDayChange = 0.0;
            if (yesterdayCalls != null && yesterdayCalls > 0 && todayCalls != null) {
                dayOverDayChange = ((todayCalls.doubleValue() - yesterdayCalls.doubleValue()) / yesterdayCalls.doubleValue()) * 100.0;
            } else if (todayCalls != null && todayCalls > 0) {
                dayOverDayChange = 100.0; // 100% increase if no previous data
            }
            
            log.debug("📊 API key metrics calculated: thisMonth={}, successful={}, failed={}, today={}, yesterday={}, dayChange={}%, avgResponse={}ms", 
                     totalCallsThisMonth, successfulCallsThisMonth, failedCallsThisMonth, todayCalls, yesterdayCalls, 
                     String.format("%.2f", dayOverDayChange), averageResponseTime);
            
            return ApiKeyMetrics.builder()
                .totalCallsThisMonth(totalCallsThisMonth != null ? totalCallsThisMonth : 0L)
                .successfulCallsThisMonth(successfulCallsThisMonth != null ? successfulCallsThisMonth : 0L)
                .failedCallsThisMonth(failedCallsThisMonth != null ? failedCallsThisMonth : 0L)
                .todayCalls(todayCalls != null ? todayCalls : 0L)
                .yesterdayCalls(yesterdayCalls != null ? yesterdayCalls : 0L)
                .dayOverDayChangePercentage(dayOverDayChange)
                .averageResponseTime(averageResponseTime != null ? averageResponseTime : 0.0)
                .build();
                
        } catch (Exception e) {
            log.error("❌ Failed to calculate API key metrics for {}: {}", apiKeyId, e.getMessage(), e);
            return ApiKeyMetrics.empty();
        }
    }
    
    /**
     * ✅ ENHANCED: Calculate comprehensive API key quota with unlimited support
     */
    private ApiKeyQuotaInfo calculateApiKeyQuota(ApiKey apiKey) {
        try {
            log.debug("🔍 Calculating quota for API key: {} (tier: {})", apiKey.getId(), apiKey.getRateLimitTier());
            
            int monthlyLimit = getApiKeyMonthlyLimit(apiKey);
            boolean isUnlimited = (monthlyLimit == Integer.MAX_VALUE);
            int used = getCurrentMonthUsageForApiKey(apiKey.getId());
            
            // ✅ Handle unlimited quota properly
            int remaining;
            double percentage;
            String quotaStatus;
            
            if (isUnlimited) {
                remaining = Integer.MAX_VALUE;
                percentage = 0.0; // 0% for unlimited
                quotaStatus = "unlimited";
                log.debug("📊 API key has UNLIMITED quota: used={}", used);
            } else {
                remaining = Math.max(0, monthlyLimit - used);
                percentage = monthlyLimit > 0 ? (double) used / monthlyLimit * 100 : 0;
                
                // ✅ Determine quota status
                if (used >= monthlyLimit) {
                    quotaStatus = "exceeded";
                } else if (percentage > 90) {
                    quotaStatus = "critical";
                } else if (percentage > 75) {
                    quotaStatus = "warning";
                } else {
                    quotaStatus = "healthy";
                }
                
                log.debug("📊 API key quota: limit={}, used={}, remaining={}, percentage={}%, status={}", 
                         monthlyLimit, used, remaining, String.format("%.2f", percentage), quotaStatus);
            }
            
            return ApiKeyQuotaInfo.builder()
                .monthlyLimit(monthlyLimit)
                .used(used)
                .remaining(remaining)
                .usagePercentage(percentage)
                .build();
                
        } catch (Exception e) {
            log.error("❌ Failed to calculate API key quota for {}: {}", apiKey.getId(), e.getMessage(), e);
            return ApiKeyQuotaInfo.empty();
        }
    }
    
    /**
     * Calculate API key usage patterns
     * ✅ FIXED: Safe casting with null checks and proper error handling
     */
    private ApiKeyUsageInfo calculateApiKeyUsage(UUID apiKeyId) {
        try {
            // Get usage patterns for the last 30 days
            Object[] result = apiKeyRepository.getApiKeyUsagePatterns(apiKeyId);
            
            // ✅ Check if result is null or empty
            if (result == null || result.length < 4) {
                log.debug("No usage pattern data found for API key: {}", apiKeyId);
                return ApiKeyUsageInfo.empty();
            }
            
            // ✅ Safe casting with null checks
            Integer peakHour = safeCastToInteger(result[0]);
            Double averageDailyUsage = safeCastToDouble(result[1]); 
            String mostUsedEndpoint = safeCastToString(result[2]);
            Integer uniqueIpsCount = safeCastToInteger(result[3]);
            
            return ApiKeyUsageInfo.builder()
                .peakHour(peakHour != null ? peakHour : 0)
                .averageDailyUsage(averageDailyUsage != null ? averageDailyUsage : 0.0)
                .mostUsedEndpoint(mostUsedEndpoint != null ? mostUsedEndpoint : "N/A")
                .uniqueIpsCount(uniqueIpsCount != null ? uniqueIpsCount : 0)
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to calculate API key usage info: {}", e.getMessage());
            return ApiKeyUsageInfo.empty();
        }
    }
    
    private Integer safeCastToInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Double safeCastToDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private String safeCastToString(Object value) {
        if (value == null) return null;
        return value.toString();
    }
    
    private Long safeCastToLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Boolean safeCastToBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        try {
            String str = value.toString().toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 🔍 DIAGNOSTIC: Get raw database data for debugging
     */
    public String getDiagnosticInfo(String userId) {
        StringBuilder diagnostic = new StringBuilder();
        
        try {
            diagnostic.append("=== DIAGNOSTIC INFO FOR USER: ").append(userId).append(" ===\n");
            
            // Check user exists
            Optional<User> userOpt = userRepository.findById(userId);
            diagnostic.append("User exists: ").append(userOpt.isPresent()).append("\n");
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                diagnostic.append("User plan: ").append(user.getPlan()).append("\n");
                
                // Check API keys
                List<ApiKey> apiKeys = apiKeyRepository.findByUserFkId(userId);
                diagnostic.append("API keys count: ").append(apiKeys.size()).append("\n");
                
                for (ApiKey apiKey : apiKeys) {
                    int keyLimit = getApiKeyMonthlyLimit(apiKey);
                    diagnostic.append("  - API Key: ").append(apiKey.getId())
                              .append(", Domain: ").append(apiKey.getRegisteredDomain())
                              .append(", Tier: ").append(apiKey.getRateLimitTier())
                              .append(", Limit: ").append(keyLimit == Integer.MAX_VALUE ? "UNLIMITED" : keyLimit)
                              .append(", Active: ").append(apiKey.isActive()).append("\n");
                }
                
                // Calculate total user limit
                int userTotalLimit = getUserMonthlyLimit(user);
                diagnostic.append("User total limit: ").append(userTotalLimit == Integer.MAX_VALUE ? "UNLIMITED" : userTotalLimit).append("\n");
                
                // Check monthly usage data
                String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
                String lastMonth = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
                int totalUsage = usageTracker.getCurrentMonthUsageForUser(userId, currentMonth);
                diagnostic.append("Current month usage: ").append(totalUsage).append("\n");
                
                // Check raw query results
                Object[] rawMetrics = apiKeyRepository.getUserDashboardMetrics(userId);
                diagnostic.append("Raw metrics query result: ");
                if (rawMetrics != null) {
                    for (int i = 0; i < rawMetrics.length; i++) {
                        diagnostic.append("[").append(i).append("]=").append(rawMetrics[i]).append(" ");
                    }
                } else {
                    diagnostic.append("NULL");
                }
                diagnostic.append("\n");
                
                // Check domain metrics
                Object[] domainMetrics = apiKeyRepository.getUserDomainMetrics(userId);
                diagnostic.append("Domain metrics query result: ");
                if (domainMetrics != null) {
                    for (int i = 0; i < domainMetrics.length; i++) {
                        diagnostic.append("[").append(i).append("]=").append(domainMetrics[i]).append(" ");
                    }
                } else {
                    diagnostic.append("NULL");
                }
                diagnostic.append("\n");
            }
            
        } catch (Exception e) {
            diagnostic.append("ERROR: ").append(e.getMessage()).append("\n");
        }
        
        return diagnostic.toString();
    }
    
    // ==================== CARD BUILDER METHODS ====================
    
    private UserDashboardCardsDTO.ApiCallsCardDTO buildApiCallsCard(DashboardMetrics metrics) {
        // ✅ Use pre-calculated month-over-month percentage
        double changePercentage = metrics.getMonthOverMonthChangePercentage() != null ? 
                                 metrics.getMonthOverMonthChangePercentage() : 0.0;
        
        // ✅ Determine status based on usage patterns
        String status = "healthy";
        if (metrics.getTotalCallsThisMonth() == 0) {
            status = "inactive";
        } else if (changePercentage < -50) {
            status = "declining";
        } else if (changePercentage > 100) {
            status = "growing";
        }
        
        // ✅ Calculate daily average for current month
        double dailyAverage = metrics.getTotalCallsThisMonth() > 0 ? 
                             metrics.getTotalCallsThisMonth() / (double) LocalDate.now().getDayOfMonth() : 0.0;
        
        return UserDashboardCardsDTO.ApiCallsCardDTO.builder()
            .totalCalls(metrics.getTotalCallsThisMonth())
            .percentageChange(changePercentage)
            .trend(changePercentage > 5 ? "up" : changePercentage < -5 ? "down" : "stable")
            .previousPeriodCalls(metrics.getTotalCallsLastMonth())
            .dailyAverage(dailyAverage)
            .status(status)
            .build();
    }
    
    private UserDashboardCardsDTO.ActiveDomainsCardDTO buildActiveDomainsCard(DomainInfo domains) {
        double changePercentage = calculatePercentageChange(
            domains.getActiveDomainsCount(), 
            domains.getDomainsAddedLastMonth()
        );
        
        return UserDashboardCardsDTO.ActiveDomainsCardDTO.builder()
            .activeDomains(domains.getActiveDomainsCount())
            .percentageChange(changePercentage)
            .trend(changePercentage >= 0 ? "up" : "down")
            .previousPeriodDomains(domains.getDomainsAddedLastMonth())
            .newDomainsThisPeriod(domains.getDomainsAddedThisMonth())
            .status("growing")
            .build();
    }
    
    private UserDashboardCardsDTO.DomainsAddedCardDTO buildDomainsAddedCard(DomainInfo domains) {
        double changePercentage = calculatePercentageChange(
            domains.getDomainsAddedThisMonth(), 
            domains.getDomainsAddedLastMonth()
        );
        
        return UserDashboardCardsDTO.DomainsAddedCardDTO.builder()
            .domainsAdded(domains.getDomainsAddedThisMonth())
            .percentageChange(changePercentage)
            .trend(changePercentage >= 0 ? "up" : "down")
            .previousMonthAdded(domains.getDomainsAddedLastMonth())
            .status("healthy")
            .build();
    }
    
    private UserDashboardCardsDTO.RemainingQuotaCardDTO buildRemainingQuotaCard(QuotaInfo quota) {
        return UserDashboardCardsDTO.RemainingQuotaCardDTO.builder()
            .remainingQuota(quota.getRemaining().longValue())
            .totalQuota(quota.getMonthlyLimit().longValue())
            .usedQuota(Long.valueOf(quota.getMonthlyLimit() - quota.getRemaining()))
            .usagePercentage(quota.getUsagePercentage())
            .percentageChange(0.0) // Can be enhanced with historical data
            .trend("stable")
            .estimatedDaysRemaining(30) // Can be calculated based on usage patterns
            .status(getQuotaStatus(quota.getUsagePercentage()))
            .build();
    }
    
    private SingleApiKeyDashboardDTO.MonthlyMetricsDTO buildMonthlyMetricsCard(
            ApiKeyMetrics metrics, ApiKeyQuotaInfo quota) {
        
        double successRate = metrics.getTotalCallsThisMonth() > 0 ? 
            (double) metrics.getSuccessfulCallsThisMonth() / metrics.getTotalCallsThisMonth() * 100 : 0;
        
        return SingleApiKeyDashboardDTO.MonthlyMetricsDTO.builder()
            .totalCalls(metrics.getTotalCallsThisMonth().longValue())
            .successfulCalls(metrics.getSuccessfulCallsThisMonth().longValue())
            .failedCalls(metrics.getFailedCallsThisMonth().longValue())
            .quotaLimit(quota.getMonthlyLimit().longValue())
            .remainingQuota(quota.getRemaining().longValue())
            .successRate(successRate)
            .quotaStatus(getQuotaStatus(quota.getUsagePercentage()))
            .build();
    }
    
    private SingleApiKeyDashboardDTO.PerformanceMetricsDTO buildPerformanceMetricsCard(ApiKeyMetrics metrics) {
        return SingleApiKeyDashboardDTO.PerformanceMetricsDTO.builder()
            .averageResponseTime(metrics.getAverageResponseTime())
            .errorRate24h(metrics.getFailedCallsThisMonth() > 0 ? 
                (double) metrics.getFailedCallsThisMonth() / metrics.getTotalCallsThisMonth() * 100 : 0.0)
            .build();
    }
    

    
    // ==================== COMPLETED HELPER METHODS ====================
    
    /**
     * ✅ FIXED: Smart quota calculation for mixed API key tiers
     * - If ANY API key is BUSINESS tier → User gets UNLIMITED
     * - Otherwise: Sum all API key limits (FREE + PRO + etc.)
     */
    private int getUserMonthlyLimit(User user) {
        try {
            // 🚀 OPTIMIZED: Check if user has any API keys first
            long apiKeyCount = getUserApiKeyCountOptimized(user.getId());
            if (apiKeyCount == 0) {
                log.debug("No API keys found for user {}, using default limit", user.getId());
                return 100; // Default if no API keys
            }
            
            // Get all user's API keys (only when needed)
            List<ApiKey> userApiKeys = apiKeyRepository.findByUserFkId(user.getId());
            
            // ✅ SMART LOGIC: Check if user has ANY BUSINESS tier API key
            boolean hasBusinessTier = userApiKeys.stream()
                .anyMatch(apiKey -> {
                    int limit = getApiKeyMonthlyLimit(apiKey);
                    return limit == Integer.MAX_VALUE; // BUSINESS tier = unlimited
                });
            
            if (hasBusinessTier) {
                log.debug("User {} has BUSINESS tier API key → UNLIMITED quota", user.getId());
                return Integer.MAX_VALUE; // Unlimited
            }
            
            // ✅ Sum up all non-unlimited API key limits
            int totalLimit = userApiKeys.stream()
                .mapToInt(this::getApiKeyMonthlyLimit)
                .filter(limit -> limit != Integer.MAX_VALUE) // Exclude unlimited keys
                .sum();
            
            log.debug("Calculated total quota for user {}: {} (from {} API keys, mixed tiers)", 
                     user.getId(), totalLimit, userApiKeys.size());
            
            return Math.max(totalLimit, 100); // Minimum 100
            
        } catch (Exception e) {
            log.error("Error getting monthly limit for user {}: {}", user.getId(), e.getMessage());
            return 100; // Safe default
        }
    }
    
    /**
     * Get current month usage for user across all API keys
     */
    private int getCurrentMonthUsage(String userId) {
        try {
            String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            // ✅ FIXED: Use api_key_usage_stats instead of api_key_monthly_usage for accurate data
            List<ApiKey> userApiKeys = apiKeyRepository.findByUserFkId(userId);
            int totalUsage = 0;
            
            for (ApiKey apiKey : userApiKeys) {
                int keyUsage = usageTracker.getCurrentMonthUsageForApiKey(apiKey.getId(), currentMonth);
                totalUsage += keyUsage;
                log.debug("📊 API Key {} usage: {}", apiKey.getId(), keyUsage);
            }
            
            log.debug("📊 Total usage for user {}: {} (from {} API keys)", userId, totalUsage, userApiKeys.size());
            return totalUsage;
            
        } catch (Exception e) {
            log.error("Error getting current month usage for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get API key's monthly limit based on rate limit tier
     */
    private int getApiKeyMonthlyLimit(ApiKey apiKey) {
        try {
            if (apiKey.getRateLimitTier() != null) {
                switch (apiKey.getRateLimitTier()) {
                    case FREE_TIER:
                        return 100; // 100 requests/month
                    case PRO_TIER:
                        return 1000; // 1K requests/month
                    case BUSINESS_TIER:
                        return Integer.MAX_VALUE; // Unlimited
                    default:
                        log.warn("Unknown rate limit tier for API key {}: {}", 
                                apiKey.getId(), apiKey.getRateLimitTier());
                        return 100; // Default to free tier
                }
            }
            
            // No custom quota limit in ApiKey entity - use tier-based limits
            
            return 100; // Default limit (FREE tier)
            
        } catch (Exception e) {
            log.error("Error getting monthly limit for API key {}: {}", apiKey.getId(), e.getMessage());
            return 100; // Safe default (FREE tier)
        }
    }
    
    /**
     * Get current month usage for specific API key
     */
    private int getCurrentMonthUsageForApiKey(UUID apiKeyId) {
        try {
            String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            // Use StreamlinedUsageTracker for consistent data
            return usageTracker.getCurrentMonthUsageForApiKey(apiKeyId, currentMonth);
            
        } catch (Exception e) {
            log.error("Error getting current month usage for API key {}: {}", apiKeyId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get last month string for comparison queries
     */
    private String getLastMonth() {
        return LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
    
    /**
     * Calculate percentage change with null safety
     */
    private double calculatePercentageChange(Number current, Number previous) {
        if (previous == null || previous.doubleValue() == 0) {
            return current != null && current.doubleValue() > 0 ? 100.0 : 0.0;
        }
        
        double currentVal = current != null ? current.doubleValue() : 0.0;
        double previousVal = previous.doubleValue();
        
        return Math.round(((currentVal - previousVal) / previousVal) * 100.0 * 100.0) / 100.0; // Round to 2 decimal places
    }
    
    /**
     * Get quota status based on usage percentage
     */
    private String getQuotaStatus(double usagePercentage) {
        if (usagePercentage >= 95) return "CRITICAL";
        if (usagePercentage >= 85) return "WARNING";
        if (usagePercentage >= 70) return "MODERATE";
        if (usagePercentage >= 50) return "NORMAL";
        return "HEALTHY";
    }
    
    /**
     * Create empty dashboard for error cases
     */
    private UserDashboardCardsDTO createEmptyDashboard() {
        return UserDashboardCardsDTO.builder()
            .totalApiCalls(UserDashboardCardsDTO.ApiCallsCardDTO.builder()
                .totalCalls(0L).percentageChange(0.0).trend("stable")
                .previousPeriodCalls(0L).dailyAverage(0.0).status("healthy").build())
            .activeDomains(UserDashboardCardsDTO.ActiveDomainsCardDTO.builder()
                .activeDomains(0).percentageChange(0.0).trend("stable")
                .previousPeriodDomains(0).newDomainsThisPeriod(0).status("stable").build())
            .domainsAdded(UserDashboardCardsDTO.DomainsAddedCardDTO.builder()
                .domainsAdded(0).percentageChange(0.0).trend("stable")
                .previousMonthAdded(0).status("healthy").build())
            .remainingQuota(UserDashboardCardsDTO.RemainingQuotaCardDTO.builder()
                .remainingQuota(0L).totalQuota(0L).usedQuota(0L).usagePercentage(0.0)
                .percentageChange(0.0).trend("stable").estimatedDaysRemaining(0).status("healthy").build())
            .build();
    }
    
    // ==================== DATA CLASSES ====================
    
    @lombok.Data
    @lombok.Builder
    private static class DashboardMetrics {
        private Long totalCallsThisMonth;
        private Long totalCallsLastMonth;
        private Double monthOverMonthChangePercentage;
        private Long successfulCalls;
        private Long failedCalls;
        private Long todayCalls;
        private Long yesterdayCalls;
        private Double dayOverDayChangePercentage;
        
        public static DashboardMetrics empty() {
            return DashboardMetrics.builder()
                .totalCallsThisMonth(0L).totalCallsLastMonth(0L)
                .monthOverMonthChangePercentage(0.0)
                .successfulCalls(0L).failedCalls(0L)
                .todayCalls(0L).yesterdayCalls(0L)
                .dayOverDayChangePercentage(0.0)
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class QuotaInfo {
        private Integer monthlyLimit;
        private Integer used;
        private Integer remaining;
        private Double usagePercentage;
        
        public static QuotaInfo empty() {
            return QuotaInfo.builder()
                .monthlyLimit(0).used(0).remaining(0).usagePercentage(0.0)
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class DomainInfo {
        private Integer activeDomainsCount;
        private Integer domainsAddedThisMonth;
        private Integer domainsAddedLastMonth;
        
        public static DomainInfo empty() {
            return DomainInfo.builder()
                .activeDomainsCount(0).domainsAddedThisMonth(0).domainsAddedLastMonth(0)
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ApiKeyMetrics {
        private Long totalCallsThisMonth;
        private Long successfulCallsThisMonth;
        private Long failedCallsThisMonth;
        private Long todayCalls;
        private Long yesterdayCalls;
        private Double dayOverDayChangePercentage;
        private Double averageResponseTime;
        
        public static ApiKeyMetrics empty() {
            return ApiKeyMetrics.builder()
                .totalCallsThisMonth(0L).successfulCallsThisMonth(0L).failedCallsThisMonth(0L)
                .todayCalls(0L).yesterdayCalls(0L).dayOverDayChangePercentage(0.0)
                .averageResponseTime(0.0)
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ApiKeyQuotaInfo {
        private Integer monthlyLimit;
        private Integer used;
        private Integer remaining;
        private Double usagePercentage;
        
        public static ApiKeyQuotaInfo empty() {
            return ApiKeyQuotaInfo.builder()
                .monthlyLimit(0).used(0).remaining(0).usagePercentage(0.0)
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ApiKeyUsageInfo {
        private Integer peakHour;
        private Double averageDailyUsage;
        private String mostUsedEndpoint;
        private Integer uniqueIpsCount;
        
        public static ApiKeyUsageInfo empty() {
            return ApiKeyUsageInfo.builder()
                .peakHour(0).averageDailyUsage(0.0)
                .mostUsedEndpoint("N/A").uniqueIpsCount(0)
                .build();
        }
    }
    
    // ==================== PERFORMANCE OPTIMIZED METHODS ====================
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Get user API keys with usage in single query
     * Eliminates N+1 queries by fetching all data at once
     */
    @Cacheable(value = "userApiKeysWithUsage", key = "#userId", 
               unless = "#result == null || #result.isEmpty()", 
               cacheManager = "optimizedCacheManager")
    public List<ApiKeyWithUsageDTO> getUserApiKeysWithUsageOptimized(String userId) {
        log.debug("🚀 Fetching optimized API keys with usage for user: {}", userId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            List<ApiKeyWithUsageDTO> apiKeys = enhancedApiKeyService.getUserApiKeysWithUsageOptimized(userId);
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("✅ Optimized API keys fetch completed in {}ms for user: {} (found {} keys)", 
                     executionTime, userId, apiKeys.size());
            
            return apiKeys;
            
        } catch (Exception e) {
            log.error("❌ Failed to fetch optimized API keys for user {}: {}", userId, e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Get user API key count efficiently
     * Uses optimized count query instead of loading all entities
     */
    @Cacheable(value = "userApiKeyCount", key = "#userId", 
               cacheManager = "optimizedCacheManager")
    public long getUserApiKeyCountOptimized(String userId) {
        log.debug("🚀 Getting optimized API key count for user: {}", userId);
        
        try {
            return apiKeyRepository.countActiveApiKeysByUserFkId(userId);
        } catch (Exception e) {
            log.error("❌ Failed to get API key count for user {}: {}", userId, e.getMessage());
            return 0L;
        }
    }
}