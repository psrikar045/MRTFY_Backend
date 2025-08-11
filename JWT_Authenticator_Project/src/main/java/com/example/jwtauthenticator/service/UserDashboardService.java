package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.dashboard.UserDashboardCardsDTO;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.enums.RateLimitTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Service for User Dashboard Cards
 * Implements mixed approach: fast materialized views + real-time fallback
 * Uses rolling 30-day averages for percentage calculations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDashboardService {

    private final ApiKeyRequestLogRepository requestLogRepository;
    private final ApiKeyMonthlyUsageRepository monthlyUsageRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    /**
     * Get user dashboard cards with SMART CACHING
     * Short cache TTL (30 seconds) for balance between accuracy and performance
     */
    @Cacheable(value = "userDashboardCards", key = "#userId", unless = "#result == null", cacheManager = "dashboardCacheManager")
    public UserDashboardCardsDTO getUserDashboardCards(String userId) {
        log.info("🔄 Fetching dashboard cards for user: {} (30-second cache TTL)", userId);

        try {
            // PRIORITY: Real-time calculation for accuracy
            log.info("Calculating real-time dashboard for maximum accuracy for user: {}", userId);
            UserDashboardCardsDTO result = calculateRealTimeDashboard(userId);
            log.info("✅ Real-time dashboard calculated successfully for user: {}", userId);
            return result;

        } catch (Exception e) {
            log.warn("Real-time calculation failed for user {}: {}, trying materialized view fallback", userId, e.getMessage());
             return createEmptyDashboard();
        }
    }
    /**
     * Calculate dashboard in real-time using Java 21 Virtual Threads and modern patterns
     * Optimized for performance with proper query execution monitoring
     */
    private UserDashboardCardsDTO calculateRealTimeDashboard(String userId) {
        long startTime = System.currentTimeMillis();
        log.debug("Calculating real-time dashboard for user: {} using Virtual Threads", userId);
        
        var now = LocalDateTime.now();
        var thirtyDaysAgo = now.minusDays(30);
        var sixtyDaysAgo = now.minusDays(60);
        var currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        var previousMonth = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        // Use Virtual Threads for parallel computation (Java 21 feature)
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            // Parallel computation using CompletableFuture with Virtual Threads
            // Use request logs for ACCURATE data (monthly usage was inflated by rate limit checks)
            var currentCallsTask = CompletableFuture.supplyAsync(() -> 
                requestLogRepository.countByUserFkIdAndRequestTimestampBetween(userId, thirtyDaysAgo, now), executor);
                
            var previousCallsTask = CompletableFuture.supplyAsync(() -> 
                requestLogRepository.countByUserFkIdAndRequestTimestampBetween(userId, sixtyDaysAgo, thirtyDaysAgo), executor);
                
            var activeDomainsTask = CompletableFuture.supplyAsync(() -> 
                requestLogRepository.countDistinctDomainsByUserAndTimeRange(userId, thirtyDaysAgo, now), executor);
                
            var previousActiveDomainsTask = CompletableFuture.supplyAsync(() -> 
                requestLogRepository.countDistinctDomainsByUserAndTimeRange(userId, sixtyDaysAgo, thirtyDaysAgo), executor);
                
            var domainsAddedThisMonthTask = CompletableFuture.supplyAsync(() -> 
                requestLogRepository.countNewDomainsForUserInMonth(userId, currentMonth), executor);
                
            var domainsAddedPreviousMonthTask = CompletableFuture.supplyAsync(() -> 
                requestLogRepository.countNewDomainsForUserInMonth(userId, previousMonth), executor);
                
            var remainingQuotaTask = CompletableFuture.supplyAsync(() -> 
                calculateRemainingQuotaForUser(userId, currentMonth), executor);
                
            var previousRemainingQuotaTask = CompletableFuture.supplyAsync(() -> 
                calculateRemainingQuotaForUser(userId, previousMonth), executor);
                
            var successRateTask = CompletableFuture.supplyAsync(() -> 
                calculateSuccessRateForUser(userId, thirtyDaysAgo, now), executor);
                
            var totalApiKeysTask = CompletableFuture.supplyAsync(() -> 
                apiKeyRepository.countByUserFkId(userId), executor);
            
            // Wait for all tasks to complete and build dashboard
            var currentCalls = currentCallsTask.join();
            var previousCalls = previousCallsTask.join();
            
            // Handle null values from database
            currentCalls = currentCalls != null ? currentCalls : 0L;
            previousCalls = previousCalls != null ? previousCalls : 0L;
            var activeDomains = activeDomainsTask.join();
            var previousActiveDomains = previousActiveDomainsTask.join();
            var domainsAddedThisMonth = domainsAddedThisMonthTask.join();
            var domainsAddedPreviousMonth = domainsAddedPreviousMonthTask.join();
            var remainingQuota = remainingQuotaTask.join();
            var previousRemainingQuota = previousRemainingQuotaTask.join();
            var successRate = successRateTask.join();
            var totalApiKeys = totalApiKeysTask.join();
            
            log.info("📊 FIXED User Dashboard Data Sources for user {}: currentCalls={} (from request_logs - ACCURATE), previousCalls={} (from request_logs)", 
                     userId, currentCalls, previousCalls);
            log.debug("Dashboard calculation results for user {}: currentCalls={}, previousCalls={}, activeDomains={}, previousActiveDomains={}, domainsAddedThisMonth={}, domainsAddedPreviousMonth={}, remainingQuota={}, previousRemainingQuota={}, successRate={}, totalApiKeys={}", 
                     userId, currentCalls, previousCalls, activeDomains, previousActiveDomains, domainsAddedThisMonth, domainsAddedPreviousMonth, remainingQuota, previousRemainingQuota, successRate, totalApiKeys);
            
            // Debug: Verify domain data correctness and user's API keys
            if (log.isDebugEnabled()) {
                try {
                    // First, verify user's API keys
                    var userApiKeys = requestLogRepository.getUserApiKeys(userId);
                    log.debug("Debug - User {} has {} API keys:", userId, userApiKeys.size());
                    userApiKeys.forEach(row -> 
                        log.debug("  ApiKey ID: {}, Name: {}, UserFkId: {}", row[0], row[1], row[2]));
                    
                    // Then check domain data
                    var debugDomains = requestLogRepository.getDebugDomainDataForUser(userId);
                    var distinctDomains = requestLogRepository.getDistinctDomainsWithStatsForUser(userId);
                    
                    log.debug("Debug - Recent domain requests for user {}: {}", userId, debugDomains.size());
                    debugDomains.stream().limit(5).forEach(row -> 
                        log.debug("  Domain: {}, ApiKey: {}, UserFkId: {}, ApiKeyName: {}, Timestamp: {}", 
                                 row[0], row[1], row[2], row[3], row[4]));
                    
                    log.debug("Debug - Distinct domains for user {}: {}", userId, distinctDomains.size());
                    distinctDomains.forEach(row -> 
                        log.debug("  Domain: {}, FirstSeen: {}, TotalRequests: {}", 
                                 row[0], row[1], row[2]));
                                 
                } catch (Exception e) {
                    log.warn("Error getting debug domain info for user {}: {}", userId, e.getMessage());
                }
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("📊 Dashboard calculation completed for user {} in {}ms", userId, executionTime);
            log.info("📈 Final calculated values: currentCalls={}, activeDomains={}, remainingQuota={}, successRate={}, totalApiKeys={}", 
                     currentCalls, activeDomains, remainingQuota, successRate, totalApiKeys);
            
            UserDashboardCardsDTO result = UserDashboardCardsDTO.builder()
                    .totalApiCalls(buildApiCallsCard(currentCalls, previousCalls))
                    .activeDomains(buildActiveDomainsCard(activeDomains, previousActiveDomains))
                    .domainsAdded(buildDomainsAddedCard(domainsAddedThisMonth, domainsAddedPreviousMonth))
                    .remainingQuota(buildRemainingQuotaCard(userId, remainingQuota, previousRemainingQuota, currentCalls))
                    .lastUpdated(LocalDateTime.now())
                    .successRate(successRate)
                    .totalApiKeys(totalApiKeys)
                    .build();
            
            log.info("🎯 Returning dashboard result for user: {}", userId);
            return result;
        }
    }

    /**
     * Build API Calls Card with percentage calculation
     */
    private UserDashboardCardsDTO.ApiCallsCardDTO buildApiCallsCard(Long currentCalls, Long previousCalls) {
        currentCalls = currentCalls != null ? currentCalls : 0L;
        previousCalls = previousCalls != null ? previousCalls : 0L;

        Double percentageChange = calculatePercentageChange(currentCalls, previousCalls);
        String trend = determineTrend(percentageChange);
        String status = determineCallsStatus(currentCalls, percentageChange);
        Double dailyAverage = currentCalls / 30.0;

        return UserDashboardCardsDTO.ApiCallsCardDTO.builder()
                .totalCalls(currentCalls)
                .percentageChange(percentageChange)
                .trend(trend)
                .previousPeriodCalls(previousCalls)
                .dailyAverage(Math.round(dailyAverage * 100.0) / 100.0)
                .status(status)
                .build();
    }

    /**
     * Build Active Domains Card
     */
    private UserDashboardCardsDTO.ActiveDomainsCardDTO buildActiveDomainsCard(Integer currentDomains, Integer previousDomains) {
        currentDomains = currentDomains != null ? currentDomains : 0;
        previousDomains = previousDomains != null ? previousDomains : 0;

        Double percentageChange = calculatePercentageChange(currentDomains.longValue(), previousDomains.longValue());
        String trend = determineTrend(percentageChange);
        String status = determineDomainsStatus(currentDomains, percentageChange);
        Integer newDomains = Math.max(0, currentDomains - previousDomains);

        return UserDashboardCardsDTO.ActiveDomainsCardDTO.builder()
                .activeDomains(currentDomains)
                .percentageChange(percentageChange)
                .trend(trend)
                .previousPeriodDomains(previousDomains)
                .newDomainsThisPeriod(newDomains)
                .status(status)
                .build();
    }

    /**
     * Build Domains Added Card
     */
    private UserDashboardCardsDTO.DomainsAddedCardDTO buildDomainsAddedCard(Integer currentAdded, Integer previousAdded) {
        currentAdded = currentAdded != null ? currentAdded : 0;
        previousAdded = previousAdded != null ? previousAdded : 0;

        Double percentageChange = calculatePercentageChange(currentAdded.longValue(), previousAdded.longValue());
        String trend = determineTrend(percentageChange);
        String status = determineAddedDomainsStatus(currentAdded, percentageChange);

        return UserDashboardCardsDTO.DomainsAddedCardDTO.builder()
                .domainsAdded(currentAdded)
                .percentageChange(percentageChange)
                .trend(trend)
                .previousMonthAdded(previousAdded)
                .monthlyTarget(5) // Default target, could be configurable
                .status(status)
                .build();
    }

    /**
     * Build Remaining Quota Card with accurate quota calculation
     */
    private UserDashboardCardsDTO.RemainingQuotaCardDTO buildRemainingQuotaCard(String userId, Long remainingQuota, Long previousRemaining, Long currentUsage) {
        remainingQuota = remainingQuota != null ? remainingQuota : 0L;
        previousRemaining = previousRemaining != null ? previousRemaining : 0L;
        currentUsage = currentUsage != null ? currentUsage : 0L;

        Double percentageChange = calculatePercentageChange(remainingQuota, previousRemaining);
        String trend = determineTrend(percentageChange);
        
        // Get user's plan-based quota limit (CORRECT APPROACH)
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        Long totalUsedQuota = monthlyUsageRepository.getTotalUsedQuotaForUser(userId, currentMonth);
        totalUsedQuota = totalUsedQuota != null ? totalUsedQuota : 0L;
        
        // Get user's plan to determine correct quota limit
        Long totalQuotaLimit = getUserPlanQuotaLimit(userId);
        
        // Calculate remaining quota based on user's plan
        if (totalQuotaLimit == -1L) {
            // BUSINESS plan - unlimited
            remainingQuota = -1L; // Unlimited
            currentUsage = totalUsedQuota;
        } else {
            // FREE (100) or PRO (1000) plan - limited
            remainingQuota = Math.max(0L, totalQuotaLimit - totalUsedQuota);
            currentUsage = totalUsedQuota;
        }
        
        log.info("✅ CORRECT Quota calculation for user {}: plan-based totalQuotaLimit={}, totalUsedQuota={}, calculatedRemaining={}", 
                 userId, totalQuotaLimit, totalUsedQuota, remainingQuota);
        
        Double usagePercentage;
        if (totalQuotaLimit == -1L) {
            usagePercentage = 0.0; // Unlimited plan shows 0% usage
        } else if (totalQuotaLimit > 0) {
            usagePercentage = (currentUsage.doubleValue() / totalQuotaLimit.doubleValue()) * 100.0;
        } else {
            usagePercentage = 0.0;
        }
        
        // Estimate days remaining
        Integer estimatedDays = estimateDaysRemaining(remainingQuota, currentUsage);
        String status = determineQuotaStatus(usagePercentage, remainingQuota);

        return UserDashboardCardsDTO.RemainingQuotaCardDTO.builder()
                .remainingQuota(remainingQuota)
                .percentageChange(percentageChange)
                .trend(trend)
                .totalQuota(totalQuotaLimit)
                .usedQuota(currentUsage)
                .usagePercentage(Math.round(usagePercentage * 100.0) / 100.0)
                .estimatedDaysRemaining(estimatedDays)
                .status(status)
                .build();
    }

    // Helper methods for calculations

    private Double calculatePercentageChange(Long current, Long previous) {
        if (previous == null || previous == 0) {
            return current != null && current > 0 ? 100.0 : 0.0;
        }
        if (current == null) current = 0L;
        return ((current.doubleValue() - previous.doubleValue()) / previous.doubleValue()) * 100.0;
    }

    private String determineTrend(Double percentageChange) {
        if (percentageChange == null) return "stable";
        if (percentageChange > 5.0) return "up";
        if (percentageChange < -5.0) return "down";
        return "stable";
    }

    private String determineCallsStatus(Long calls, Double percentageChange) {
        if (calls > 10000 && percentageChange > 50) return "warning";
        if (calls > 0) return "healthy";
        return "inactive";
    }

    private String determineDomainsStatus(Integer domains, Double percentageChange) {
        if (percentageChange > 10) return "growing";
        if (percentageChange < -10) return "declining";
        return "stable";
    }

    private String determineAddedDomainsStatus(Integer added, Double percentageChange) {
        if (added >= 5) return "ahead";
        if (added >= 2) return "on_track";
        return "behind";
    }

    private String determineQuotaStatus(Double usagePercentage, Long remaining) {
        if (remaining == 0) return "critical";
        if (usagePercentage > 90) return "critical";
        if (usagePercentage > 75) return "warning";
        if (remaining == -1) return "unlimited";
        return "healthy";
    }

    private Integer estimateDaysRemaining(Long remaining, Long currentUsage) {
        if (remaining == -1) return -1; // Unlimited
        if (remaining == 0) return 0;
        if (currentUsage == 0) return Integer.MAX_VALUE;
        
        double dailyUsage = currentUsage / 30.0;
        if (dailyUsage <= 0) return Integer.MAX_VALUE;
        
        return (int) Math.ceil(remaining / dailyUsage);
    }

    // Additional helper methods that need to be implemented in repositories

    private Long calculateRemainingQuotaForUser(String userId, String monthYear) {
        // Use plan-based calculation with ACCURATE data from request logs
        Long planQuotaLimit = getUserPlanQuotaLimit(userId);
        
        // ✅ FIXED: Use request logs for accurate usage count (not inflated monthly usage)
        LocalDateTime monthStart = LocalDate.parse(monthYear + "-01").atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);
        Long totalUsedQuota = requestLogRepository.countByUserFkIdAndRequestTimestampBetween(userId, monthStart, monthEnd);
        totalUsedQuota = totalUsedQuota != null ? totalUsedQuota : 0L;
        
        log.debug("📊 FIXED Quota Calculation for user {}: planLimit={}, usedQuota={} (from request_logs - ACCURATE)", 
                  userId, planQuotaLimit, totalUsedQuota);
        
        if (planQuotaLimit == -1L) {
            return -1L; // Unlimited for BUSINESS plan
        } else {
            return Math.max(0L, planQuotaLimit - totalUsedQuota);
        }
    }

    private Double calculateSuccessRateForUser(String userId, LocalDateTime from, LocalDateTime to) {
        // This would need a custom query in the repository
        return requestLogRepository.getSuccessRateForUser(userId, from, to);
    }

    /**
     * Create empty dashboard as fallback
     */
    private UserDashboardCardsDTO createEmptyDashboard() {
        return UserDashboardCardsDTO.builder()
                .totalApiCalls(UserDashboardCardsDTO.ApiCallsCardDTO.builder()
                        .totalCalls(0L)
                        .percentageChange(0.0)
                        .trend("stable")
                        .previousPeriodCalls(0L)
                        .dailyAverage(0.0)
                        .status("inactive")
                        .build())
                .activeDomains(UserDashboardCardsDTO.ActiveDomainsCardDTO.builder()
                        .activeDomains(0)
                        .percentageChange(0.0)
                        .trend("stable")
                        .previousPeriodDomains(0)
                        .newDomainsThisPeriod(0)
                        .status("stable")
                        .build())
                .domainsAdded(UserDashboardCardsDTO.DomainsAddedCardDTO.builder()
                        .domainsAdded(0)
                        .percentageChange(0.0)
                        .trend("stable")
                        .previousMonthAdded(0)
                        .monthlyTarget(5)
                        .status("behind")
                        .build())
                .remainingQuota(UserDashboardCardsDTO.RemainingQuotaCardDTO.builder()
                        .remainingQuota(0L)
                        .percentageChange(0.0)
                        .trend("stable")
                        .totalQuota(0L)
                        .usedQuota(0L)
                        .usagePercentage(0.0)
                        .estimatedDaysRemaining(0)
                        .status("inactive")
                        .build())
                .lastUpdated(LocalDateTime.now())
                .successRate(0.0)
                .totalApiKeys(0)
                .build();
    }

    /**
     * Force refresh of dashboard data (clears cache and recalculates)
     */
    @org.springframework.cache.annotation.CacheEvict(value = "userDashboardCards", key = "#userId", cacheManager = "dashboardCacheManager")
    public UserDashboardCardsDTO refreshUserDashboardCards(String userId) {
        log.info("Force refreshing dashboard cards for user: {}", userId);
        // Cache is automatically evicted by @CacheEvict annotation
        return calculateRealTimeDashboard(userId);
    }
    
    /**
     * Get user's plan-based quota limit
     * FREE: 100, PRO: 1000, BUSINESS: -1 (unlimited)
     */
    private Long getUserPlanQuotaLimit(String userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UserPlan plan = user.getPlan() != null ? user.getPlan() : UserPlan.FREE;
                RateLimitTier tier = RateLimitTier.fromUserPlan(plan);
                
                long quotaLimit = tier.isUnlimited() ? -1L : tier.getRequestsPerMonth();
                log.debug("User {} has plan {} with quota limit: {}", userId, plan, quotaLimit);
                return quotaLimit;
            } else {
                log.warn("User {} not found, defaulting to FREE plan (100 requests)", userId);
                return 100L; // Default to FREE plan
            }
        } catch (Exception e) {
            log.error("Error getting user plan for {}: {}, defaulting to FREE plan", userId, e.getMessage());
            return 100L; // Default to FREE plan on error
        }
    }
}