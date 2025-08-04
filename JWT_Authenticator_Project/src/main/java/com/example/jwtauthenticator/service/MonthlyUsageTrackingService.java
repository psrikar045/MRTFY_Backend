package com.example.jwtauthenticator.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for tracking monthly API usage and enforcing quotas
 * Handles quota validation, usage recording, and monthly resets
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyUsageTrackingService {
    
    private final ApiKeyMonthlyUsageRepository usageRepository;
    private final UserRepository userRepository;
    
    /**
     * Record an API call for the given API key
     */
    @Transactional
    public void recordApiCall(UUID apiKeyId, String userId, boolean successful) {
        log.info("Recording API call for key: {}, user: {}, successful: {}", apiKeyId, userId, successful);
        try {
            String currentMonth = ApiKeyMonthlyUsage.getCurrentMonthYear();
            log.debug("Current month: {}", currentMonth);
            
            ApiKeyMonthlyUsage usage = getOrCreateMonthlyUsage(apiKeyId, userId, currentMonth);
            log.debug("Got/created monthly usage record: {}", usage.getId());
            
            if (successful) {
                usage.incrementSuccessfulCalls();
                log.debug("Incremented successful calls, new count: {}", usage.getSuccessfulCalls());
            } else {
                usage.incrementFailedCalls();
                log.debug("Incremented failed calls, new count: {}", usage.getFailedCalls());
            }
            
            log.debug("About to save monthly usage record...");
            ApiKeyMonthlyUsage savedUsage = usageRepository.save(usage);
            log.info("Successfully saved monthly usage record with ID: {}, total calls: {}", 
                    savedUsage.getId(), savedUsage.getTotalCalls());
                    
        } catch (Exception e) {
            log.error("Failed to record API call for key '{}': {}", apiKeyId, e.getMessage(), e);
            // Don't throw exception to avoid breaking the API call
        }
    }
    
    /**
     * Record a quota exceeded call
     */
    @Transactional
    public void recordQuotaExceededCall(UUID apiKeyId, String userId) {
        try {
            String currentMonth = ApiKeyMonthlyUsage.getCurrentMonthYear();
            
            ApiKeyMonthlyUsage usage = getOrCreateMonthlyUsage(apiKeyId, userId, currentMonth);
            usage.incrementQuotaExceededCalls();
            
            usageRepository.save(usage);
            
            log.debug("Recorded quota exceeded call for key '{}': total exceeded calls: {}", 
                    apiKeyId, usage.getQuotaExceededCalls());
                    
        } catch (Exception e) {
            log.error("Failed to record quota exceeded call for key '{}': {}", apiKeyId, e.getMessage(), e);
        }
    }
    
    /**
     * Check if API key has exceeded its quota
     */
    public boolean isQuotaExceeded(UUID apiKeyId, User user) {
        try {
            String currentMonth = ApiKeyMonthlyUsage.getCurrentMonthYear();
            
            Optional<ApiKeyMonthlyUsage> usageOpt = usageRepository.findByApiKeyIdAndMonthYear(apiKeyId, currentMonth);
            
            if (usageOpt.isEmpty()) {
                // No usage record yet, create one and return false
                createMonthlyUsageRecord(apiKeyId, user.getId(), currentMonth, user.getPlan());
                return false;
            }
            
            ApiKeyMonthlyUsage usage = usageOpt.get();
            
            // Check if we need to reset for new month
            if (usage.needsReset(LocalDate.now())) {
                resetUsageForNewMonth(usage, user.getPlan());
                return false;
            }
            
            boolean exceeded = usage.isQuotaExceeded();
            
            if (exceeded) {
                log.info("Quota exceeded for API key '{}': {}/{} calls used", 
                        apiKeyId, usage.getTotalCalls(), usage.getQuotaLimit());
            }
            
            return exceeded;
            
        } catch (Exception e) {
            log.error("Error checking quota for API key '{}': {}", apiKeyId, e.getMessage(), e);
            return false; // Default to allowing the call if there's an error
        }
    }
    
    /**
     * Check if API key has exceeded grace period
     */
    public boolean isGraceExceeded(UUID apiKeyId, User user) {
        try {
            String currentMonth = ApiKeyMonthlyUsage.getCurrentMonthYear();
            
            Optional<ApiKeyMonthlyUsage> usageOpt = usageRepository.findByApiKeyIdAndMonthYear(apiKeyId, currentMonth);
            
            if (usageOpt.isEmpty()) {
                return false;
            }
            
            ApiKeyMonthlyUsage usage = usageOpt.get();
            
            // Check if we need to reset for new month
            if (usage.needsReset(LocalDate.now())) {
                resetUsageForNewMonth(usage, user.getPlan());
                return false;
            }
            
            boolean graceExceeded = usage.isGraceExceeded();
            
            if (graceExceeded) {
                log.warn("Grace period exceeded for API key '{}': {}/{} calls used (including grace)", 
                        apiKeyId, usage.getTotalCalls(), usage.getGraceLimit());
            }
            
            return graceExceeded;
            
        } catch (Exception e) {
            log.error("Error checking grace period for API key '{}': {}", apiKeyId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get current usage for API key
     */
    public ApiKeyMonthlyUsage getCurrentUsage(UUID apiKeyId, String userId) {
        String currentMonth = ApiKeyMonthlyUsage.getCurrentMonthYear();
        
        return usageRepository.findByApiKeyIdAndMonthYear(apiKeyId, currentMonth)
                .orElseGet(() -> {
                    // Create new usage record if not exists
                    User user = userRepository.findById(userId).orElse(null);
                    UserPlan plan = user != null ? user.getPlan() : UserPlan.FREE;
                    return createMonthlyUsageRecord(apiKeyId, userId, currentMonth, plan);
                });
    }
    
    /**
     * Get remaining calls for API key
     */
    public int getRemainingCalls(UUID apiKeyId, String userId) {
        ApiKeyMonthlyUsage usage = getCurrentUsage(apiKeyId, userId);
        return usage.getRemainingCalls();
    }
    
    /**
     * Get remaining grace calls for API key
     */
    public int getRemainingGraceCalls(UUID apiKeyId, String userId) {
        ApiKeyMonthlyUsage usage = getCurrentUsage(apiKeyId, userId);
        return usage.getRemainingGraceCalls();
    }
    
    /**
     * Reset usage for new month
     */
    @Transactional
    public void resetUsageForNewMonth(ApiKeyMonthlyUsage usage, UserPlan plan) {
        LocalDate resetDate = LocalDate.now().withDayOfMonth(1);
        
        int newQuotaLimit = plan.getMonthlyApiCalls();
        int newGraceLimit = plan.getGraceLimit("api_calls");
        
        usage.resetForNewMonth(resetDate, newQuotaLimit, newGraceLimit);
        usageRepository.save(usage);
        
        log.info("Reset monthly usage for API key '{}' to new limits: quota={}, grace={}", 
                usage.getApiKeyId(), newQuotaLimit, newGraceLimit);
    }
    
    /**
     * Create or get monthly usage record
     */
    private ApiKeyMonthlyUsage getOrCreateMonthlyUsage(UUID apiKeyId, String userId, String monthYear) {
        return usageRepository.findByApiKeyIdAndMonthYear(apiKeyId, monthYear)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId).orElse(null);
                    UserPlan plan = user != null ? user.getPlan() : UserPlan.FREE;
                    return createMonthlyUsageRecord(apiKeyId, userId, monthYear, plan);
                });
    }
    
    /**
     * Create new monthly usage record
     */
    @Transactional
    public ApiKeyMonthlyUsage createMonthlyUsageRecord(UUID apiKeyId, String userId, String monthYear, UserPlan plan) {
        int quotaLimit = plan.getMonthlyApiCalls();
        int graceLimit = plan.getGraceLimit("api_calls");
        
        ApiKeyMonthlyUsage usage = ApiKeyMonthlyUsage.createForCurrentMonth(apiKeyId, userId, quotaLimit, graceLimit);
        usage.setMonthYear(monthYear);
        
        ApiKeyMonthlyUsage saved = usageRepository.save(usage);
        
        log.info("Created monthly usage record for API key '{}' with limits: quota={}, grace={}", 
                apiKeyId, quotaLimit, graceLimit);
        
        return saved;
    }
    
    /**
     * Update quota limits when user upgrades plan
     */
    @Transactional
    public void updateQuotaLimitsForPlanUpgrade(String userId, UserPlan newPlan) {
        String currentMonth = ApiKeyMonthlyUsage.getCurrentMonthYear();
        
        usageRepository.findByUserIdAndMonthYear(userId, currentMonth)
                .forEach(usage -> {
                    int newQuotaLimit = newPlan.getMonthlyApiCalls();
                    int newGraceLimit = newPlan.getGraceLimit("api_calls");
                    
                    usage.setQuotaLimit(newQuotaLimit);
                    usage.setGraceLimit(newGraceLimit);
                    
                    usageRepository.save(usage);
                    
                    log.info("Updated quota limits for API key '{}' due to plan upgrade: quota={}, grace={}", 
                            usage.getApiKeyId(), newQuotaLimit, newGraceLimit);
                });
    }
    
    /**
     * Async method to clean up old usage records
     */
    @Async
    public CompletableFuture<Void> cleanupOldUsageRecords(int monthsToKeep) {
        try {
            LocalDate cutoffDate = LocalDate.now().minusMonths(monthsToKeep);
            String cutoffMonth = ApiKeyMonthlyUsage.getMonthYear(cutoffDate);
            
            int deletedCount = usageRepository.deleteOldUsageRecords(cutoffMonth);
            
            log.info("Cleaned up {} old usage records older than {}", deletedCount, cutoffMonth);
            
        } catch (Exception e) {
            log.error("Error cleaning up old usage records: {}", e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Get usage statistics for user
     */
    public UsageStatistics getUserUsageStatistics(String userId) {
        String currentMonth = ApiKeyMonthlyUsage.getCurrentMonthYear();
        
        Optional<Object> statsOpt = usageRepository.getUserUsageStats(userId, currentMonth);
        
        // This would need to be properly mapped from the query result
        // For now, return a basic implementation
        UsageStatistics stats = new UsageStatistics();
        stats.setUserId(userId);
        stats.setMonthYear(currentMonth);
        
        return stats;
    }
    
    /**
     * Usage statistics class
     */
    public static class UsageStatistics {
        private String userId;
        private String monthYear;
        private int totalCalls;
        private int successfulCalls;
        private int failedCalls;
        private int quotaExceededCalls;
        private int activeKeys;
        private double successRate;
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getMonthYear() { return monthYear; }
        public void setMonthYear(String monthYear) { this.monthYear = monthYear; }
        
        public int getTotalCalls() { return totalCalls; }
        public void setTotalCalls(int totalCalls) { this.totalCalls = totalCalls; }
        
        public int getSuccessfulCalls() { return successfulCalls; }
        public void setSuccessfulCalls(int successfulCalls) { this.successfulCalls = successfulCalls; }
        
        public int getFailedCalls() { return failedCalls; }
        public void setFailedCalls(int failedCalls) { this.failedCalls = failedCalls; }
        
        public int getQuotaExceededCalls() { return quotaExceededCalls; }
        public void setQuotaExceededCalls(int quotaExceededCalls) { this.quotaExceededCalls = quotaExceededCalls; }
        
        public int getActiveKeys() { return activeKeys; }
        public void setActiveKeys(int activeKeys) { this.activeKeys = activeKeys; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }
}