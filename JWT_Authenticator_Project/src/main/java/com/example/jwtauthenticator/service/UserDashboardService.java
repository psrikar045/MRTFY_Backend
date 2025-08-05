package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.dashboard.UserDashboardCardsDTO;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserDashboardSummaryRepository;
import com.example.jwtauthenticator.entity.UserDashboardSummaryView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Service for User Dashboard Cards
 * Implements mixed approach: fast materialized views + real-time fallback
 * Uses rolling 30-day averages for percentage calculations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDashboardService {

    private final UserDashboardSummaryRepository dashboardSummaryRepository;
    private final ApiKeyRequestLogRepository requestLogRepository;
    private final ApiKeyMonthlyUsageRepository monthlyUsageRepository;
    private final ApiKeyRepository apiKeyRepository;

    /**
     * Get user dashboard cards with caching for performance
     * Mixed approach: try materialized view first, fallback to real-time calculation
     */
    @Cacheable(value = "userDashboardCards", key = "#userId", unless = "#result == null")
    @Transactional(readOnly = true)
    public UserDashboardCardsDTO getUserDashboardCards(String userId) {
        log.debug("Fetching dashboard cards for user: {}", userId);

        try {
            // Try to get from view first (fast path)
            Optional<UserDashboardSummaryView> summaryOpt = 
                dashboardSummaryRepository.findByUserFkId(userId);

            if (summaryOpt.isPresent()) {
                log.debug("Using view data for user: {}", userId);
                return buildDashboardFromSummary(summaryOpt.get());
            } else {
                log.debug("View data not available, calculating real-time for user: {}", userId);
                return calculateRealTimeDashboard(userId);
            }

        } catch (Exception e) {
            log.warn("Error fetching dashboard from view for user {}: {}, falling back to real-time calculation", userId, e.getMessage());
            // Return fallback real-time calculation
            try {
                return calculateRealTimeDashboard(userId);
            } catch (Exception fallbackError) {
                log.error("Real-time calculation also failed for user {}: {}", userId, fallbackError.getMessage(), fallbackError);
                // Return empty dashboard as last resort
                return createEmptyDashboard();
            }
        }
    }

    /**
     * Build dashboard from materialized view data (fast path)
     */
    private UserDashboardCardsDTO buildDashboardFromSummary(UserDashboardSummaryView summary) {
        return UserDashboardCardsDTO.builder()
                .totalApiCalls(buildApiCallsCard(
                    summary.getTotalCalls30Days(),
                    summary.getTotalCallsPrevious30Days()
                ))
                .activeDomains(buildActiveDomainsCard(
                    summary.getActiveDomainsCount(),
                    summary.getActiveDomainsPreviousCount()
                ))
                .domainsAdded(buildDomainsAddedCard(
                    summary.getDomainsAddedThisMonth(),
                    summary.getDomainsAddedPreviousMonth()
                ))
                .remainingQuota(buildRemainingQuotaCard(
                    summary.getRemainingQuotaTotal(),
                    summary.getRemainingQuotaPreviousMonth(),
                    summary.getTotalCalls30Days()
                ))
                .lastUpdated(LocalDateTime.now())
                .successRate(summary.getSuccessRate30Days())
                .totalApiKeys(summary.getTotalApiKeys())
                .build();
    }

    /**
     * Calculate dashboard in real-time using Java 21 Virtual Threads and modern patterns
     */
    private UserDashboardCardsDTO calculateRealTimeDashboard(String userId) {
        log.debug("Calculating real-time dashboard for user: {} using Virtual Threads", userId);
        
        var now = LocalDateTime.now();
        var thirtyDaysAgo = now.minusDays(30);
        var sixtyDaysAgo = now.minusDays(60);
        var currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        var previousMonth = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        // Use Virtual Threads for parallel computation (Java 21 feature)
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            // Parallel computation using CompletableFuture with Virtual Threads
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
            
            return UserDashboardCardsDTO.builder()
                    .totalApiCalls(buildApiCallsCard(currentCalls, previousCallsTask.join()))
                    .activeDomains(buildActiveDomainsCard(activeDomainsTask.join(), previousActiveDomainsTask.join()))
                    .domainsAdded(buildDomainsAddedCard(domainsAddedThisMonthTask.join(), domainsAddedPreviousMonthTask.join()))
                    .remainingQuota(buildRemainingQuotaCard(remainingQuotaTask.join(), previousRemainingQuotaTask.join(), currentCalls))
                    .lastUpdated(LocalDateTime.now())
                    .successRate(successRateTask.join())
                    .totalApiKeys(totalApiKeysTask.join())
                    .build();
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
     * Build Remaining Quota Card
     */
    private UserDashboardCardsDTO.RemainingQuotaCardDTO buildRemainingQuotaCard(Long remainingQuota, Long previousRemaining, Long currentUsage) {
        remainingQuota = remainingQuota != null ? remainingQuota : 0L;
        previousRemaining = previousRemaining != null ? previousRemaining : 0L;
        currentUsage = currentUsage != null ? currentUsage : 0L;

        Double percentageChange = calculatePercentageChange(remainingQuota, previousRemaining);
        String trend = determineTrend(percentageChange);
        
        // Calculate total quota and usage percentage
        Long totalQuota = remainingQuota + currentUsage;
        Double usagePercentage = totalQuota > 0 ? (currentUsage.doubleValue() / totalQuota.doubleValue()) * 100.0 : 0.0;
        
        // Estimate days remaining
        Integer estimatedDays = estimateDaysRemaining(remainingQuota, currentUsage);
        String status = determineQuotaStatus(usagePercentage, remainingQuota);

        return UserDashboardCardsDTO.RemainingQuotaCardDTO.builder()
                .remainingQuota(remainingQuota)
                .percentageChange(percentageChange)
                .trend(trend)
                .totalQuota(totalQuota)
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
        // This would need a custom query in the repository
        return monthlyUsageRepository.getTotalRemainingQuotaForUser(userId, monthYear);
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
    public UserDashboardCardsDTO refreshUserDashboardCards(String userId) {
        log.info("Force refreshing dashboard cards for user: {}", userId);
        // Clear cache if using Spring Cache
        return calculateRealTimeDashboard(userId);
    }
}