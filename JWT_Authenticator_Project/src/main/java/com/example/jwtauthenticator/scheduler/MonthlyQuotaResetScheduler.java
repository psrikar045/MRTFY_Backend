package com.example.jwtauthenticator.scheduler;
import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise-grade monthly quota reset scheduler
 * Resets all API key quotas on the 1st of every month at 00:01 UTC
 * 
 * Features:
 * - Calendar month reset (industry standard)
 * - Batch processing for performance
 * - Comprehensive error handling
 * - Detailed logging and monitoring
 * - Atomic operations to prevent data corruption
 * 
 * @author BrandSnap API Team
 * @version 1.0
 * @since Java 21
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.quota.reset.enabled", havingValue = "true", matchIfMissing = true)
public class MonthlyQuotaResetScheduler {
    
    private final ApiKeyMonthlyUsageRepository usageRepository;
    private final UserRepository userRepository;
    
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int PROGRESS_LOG_INTERVAL = 500;
    
    /**
     * Scheduled monthly reset - runs at 00:01 UTC on 1st of every month
     * Cron expression: "0 1 0 1 * ?" = second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "${app.quota.reset.cron:0 1 0 1 * ?}")
    @Transactional
    public void executeMonthlyQuotaReset() {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("🔄 STARTING Monthly Quota Reset - {}", startTime);
        
        try {
            QuotaResetResult result = performBulkQuotaReset();
            
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            
            log.info("✅ COMPLETED Monthly Quota Reset - Duration: {}ms, Success: {}, Failed: {}, Skipped: {}", 
                    durationMs, result.getSuccessCount(), result.getFailureCount(), result.getSkippedCount());
            
            // Log summary statistics
            logResetSummary(result, durationMs);
            
        } catch (Exception e) {
            log.error("❌ FAILED Monthly Quota Reset: {}", e.getMessage(), e);
            // Could add notification service here for admin alerts
            throw e; // Re-throw to trigger monitoring alerts
        }
    }
    
    /**
     * Manual reset endpoint for admin operations
     * Can be called through admin controller
     */
    @Transactional
    public QuotaResetResult performManualQuotaReset() {
        log.info("🔧 MANUAL Monthly Quota Reset initiated by admin");
        return performBulkQuotaReset();
    }
    
    /**
     * Core bulk reset logic
     * Processes all API key usage records that need monthly reset
     */
    private QuotaResetResult performBulkQuotaReset() {
        LocalDate currentDate = LocalDate.now();
        LocalDate resetDate = currentDate.withDayOfMonth(1);
        String newMonthYear = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        // Find all usage records that need reset using calendar month logic
        List<ApiKeyMonthlyUsage> usagesToReset = usageRepository.findAllNeedingReset(resetDate);
        
        log.info("📊 Found {} API key usage records requiring reset for month {}", 
                usagesToReset.size(), newMonthYear);
        
        if (usagesToReset.isEmpty()) {
            log.info("✨ No records need reset - all quotas are already current for month {}", newMonthYear);
            return new QuotaResetResult(0, 0, 0, newMonthYear);
        }
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);
        
        // Process in batches for better performance and memory management
        int batchSize = Integer.parseInt(System.getProperty("app.quota.reset.batch-size", String.valueOf(DEFAULT_BATCH_SIZE)));
        
        for (int i = 0; i < usagesToReset.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, usagesToReset.size());
            List<ApiKeyMonthlyUsage> batch = usagesToReset.subList(i, endIndex);
            
            processBatch(batch, resetDate, newMonthYear, successCount, failureCount, skippedCount);
            
            // Log progress for large datasets
            if ((i + batchSize) % PROGRESS_LOG_INTERVAL == 0 || endIndex == usagesToReset.size()) {
                log.info("📈 Progress: {}/{} records processed (Success: {}, Failed: {}, Skipped: {})", 
                        Math.min(i + batchSize, usagesToReset.size()), usagesToReset.size(),
                        successCount.get(), failureCount.get(), skippedCount.get());
            }
        }
        
        return new QuotaResetResult(successCount.get(), failureCount.get(), skippedCount.get(), newMonthYear);
    }
    
    /**
     * Process a batch of usage records
     * Uses individual transaction handling for each record to prevent batch failures
     */
    private void processBatch(List<ApiKeyMonthlyUsage> batch, LocalDate resetDate, String newMonthYear,
                             AtomicInteger successCount, AtomicInteger failureCount, AtomicInteger skippedCount) {
        
        for (ApiKeyMonthlyUsage usage : batch) {
            try {
                if (resetSingleUsageRecord(usage, resetDate, newMonthYear)) {
                    successCount.incrementAndGet();
                } else {
                    skippedCount.incrementAndGet();
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("❌ Failed to reset usage for API key {}: {}", usage.getApiKeyId(), e.getMessage());
                // Continue processing other records even if one fails
            }
        }
    }
    
    /**
     * Reset a single usage record
     * Handles the complete reset logic including user plan validation
     */
    @Transactional
    protected boolean resetSingleUsageRecord(ApiKeyMonthlyUsage usage, LocalDate resetDate, String newMonthYear) {
        // Find the user to get current plan information
        Optional<User> userOpt = userRepository.findById(usage.getUserId());
        if (userOpt.isEmpty()) {
            log.warn("⚠️ User not found for usage record: {}, skipping reset", usage.getUserId());
            return false;
        }
        
        User user = userOpt.get();
        UserPlan plan = user.getPlan();
        
        // Store old values for logging
        int oldTotalCalls = usage.getTotalCalls() != null ? usage.getTotalCalls() : 0;
        int oldQuotaLimit = usage.getQuotaLimit() != null ? usage.getQuotaLimit() : 0;
        String oldMonthYear = usage.getMonthYear();
        
        // Calculate new limits based on current user plan
        int newQuotaLimit = plan.getMonthlyApiCalls();
        int newGraceLimit = plan.getGraceLimit("api_calls");
        
        // Perform the reset using the entity's built-in method
        usage.resetForNewMonth(resetDate, newQuotaLimit, newGraceLimit);
        usage.setMonthYear(newMonthYear);
        
        // Save to database
        usageRepository.save(usage);
        
        // Log reset event with detailed information
        log.debug("✅ Reset quota for API key {}: {}/{} calls ({}) → 0/{} calls ({}) | User: {} | Plan: {}", 
                usage.getApiKeyId(), oldTotalCalls, oldQuotaLimit, oldMonthYear,
                newQuotaLimit, newMonthYear, usage.getUserId(), plan.name());
        
        return true;
    }
    
    /**
     * Log comprehensive reset summary for monitoring and audit purposes
     */
    private void logResetSummary(QuotaResetResult result, long durationMs) {
        log.info("📋 RESET SUMMARY for month {}:", result.getMonthYear());
        log.info("   ✅ Successfully reset: {} API keys", result.getSuccessCount());
        log.info("   ❌ Failed to reset: {} API keys", result.getFailureCount());
        log.info("   ⏭️ Skipped (user not found): {} API keys", result.getSkippedCount());
        log.info("   📊 Total processed: {} API keys", result.getTotalProcessed());
        log.info("   ⏱️ Processing duration: {}ms (avg: {:.2f}ms per record)", 
                durationMs, result.getTotalProcessed() > 0 ? (double) durationMs / result.getTotalProcessed() : 0);
        
        // Performance metrics
        if (result.getTotalProcessed() > 0) {
            double recordsPerSecond = (result.getTotalProcessed() * 1000.0) / durationMs;
            log.info("   🚀 Processing rate: {:.1f} records/second", recordsPerSecond);
        }
        
        // Alert on failures
        if (result.getFailureCount() > 0) {
            double failureRate = (result.getFailureCount() * 100.0) / result.getTotalProcessed();
            log.warn("⚠️ Failure rate: {:.2f}% - Review logs for specific errors", failureRate);
        }
    }
    
    /**
     * Result class for reset operations
     * Provides comprehensive information about the reset process
     */
    public static class QuotaResetResult {
        private final int successCount;
        private final int failureCount;
        private final int skippedCount;
        private final String monthYear;
        
        public QuotaResetResult(int successCount, int failureCount, int skippedCount, String monthYear) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.skippedCount = skippedCount;
            this.monthYear = monthYear;
        }
        
        // Getters
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public int getSkippedCount() { return skippedCount; }
        public String getMonthYear() { return monthYear; }
        public int getTotalProcessed() { return successCount + failureCount + skippedCount; }
        
        public boolean hasFailures() { return failureCount > 0; }
        public boolean isSuccessful() { return failureCount == 0 && getTotalProcessed() > 0; }
        public double getSuccessRate() {
            int total = getTotalProcessed();
            return total > 0 ? (successCount * 100.0) / total : 100.0;
        }
        
        @Override
        public String toString() {
            return String.format("QuotaResetResult{month='%s', success=%d, failed=%d, skipped=%d, total=%d, successRate=%.1f%%}",
                    monthYear, successCount, failureCount, skippedCount, getTotalProcessed(), getSuccessRate());
        }
    }
}