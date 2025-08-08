package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKeyRequestLog;
import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.ApiKeyUsageStatsRepository;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 🚀 STREAMLINED Usage Tracker for /rivofetch endpoint
 * 
 * High-performance, focused solution for tracking /rivofetch API calls.
 * Optimized for speed and accuracy with minimal overhead.
 * 
 * Key Features:
 * - Single method call for complete tracking
 * - Async processing for performance
 * - Only tracks what matters for billing
 * - Minimal logging overhead
 * - Direct database operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamlinedUsageTracker {
    
    private final ApiKeyRequestLogRepository auditRepository;
    private final ApiKeyMonthlyUsageRepository quotaRepository; // Keep for backward compatibility
    private final ApiKeyUsageStatsRepository usageStatsRepository; // NEW: Real data source
    private final ApiKeyRepository apiKeyRepository; // ✅ ADDED: For getting API key details
    private final PlatformTransactionManager transactionManager; // ✅ ADDED: For proper transaction management
    
    /**
     * 🎯 Track /rivofetch API call - MAIN METHOD (ASYNC VERSION)
     * 
     * Call this method from your controller after successful/failed /rivofetch calls.
     * It will handle all necessary tracking asynchronously.
     * 
     * ✅ CRITICAL FIX: @Transactional must be on @Async method for transaction context
     */
    @Async("transactionalAsyncExecutor")
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<Void> trackRivofetchCall(
            UUID apiKeyId,
            String userId,
            String clientIp,
            String domain,
            String userAgent,
            Integer responseStatus,
            Long responseTimeMs,
            String errorMessage) {
        
      try {
            boolean isSuccessful = responseStatus != null && responseStatus >= 200 && responseStatus < 300;
            
            log.debug("🎯 Tracking /rivofetch call: apiKey={}, user={}, status={}, success={}", 
                     apiKeyId, userId, responseStatus, isSuccessful);
            
            // STEP 1: Track audit log (business logic directly - transaction already active)
            trackAuditLogSync(apiKeyId, userId, clientIp, domain, userAgent, 
                            responseStatus, responseTimeMs, errorMessage, isSuccessful);
            
            // STEP 2: Track quota usage (business logic directly - transaction already active)
            trackQuotaUsageSync(apiKeyId, userId, isSuccessful);
            
            log.debug("✅ Async /rivofetch tracking completed: apiKey={}, status={}", apiKeyId, responseStatus);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to track /rivofetch call, rolling back transaction: apiKey={}, status={}", 
                     apiKeyId, responseStatus, e);
            throw e; // Let Spring handle the rollback
        }
    }
    
    /**
     * 🔒 Track /rivofetch API call - SYNCHRONOUS VERSION WITH PROPER CONNECTION MANAGEMENT
     * 
     * ✅ FIXED: Removed @Transactional to prevent connection leaks
     * Uses TransactionTemplate for proper transaction management and connection cleanup
     */
    public void trackRivofetchCallSync(
            UUID apiKeyId,
            String userId,
            String clientIp,
            String domain,
            String userAgent,
            Integer responseStatus,
            Long responseTimeMs,
            String errorMessage) {
        
        // ✅ FIXED: Use TransactionTemplate for proper connection management
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transactionTemplate.setTimeout(30); // 30 second timeout
        
        try {
            transactionTemplate.execute(status -> {
                try {
                    boolean isSuccessful = responseStatus != null && responseStatus >= 200 && responseStatus < 300;
                    
                    log.debug("🎯 Tracking /rivofetch call: apiKey={}, user={}, status={}, success={}", 
                             apiKeyId, userId, responseStatus, isSuccessful);
                    
                    // STEP 1: Track audit log (must succeed)
                    trackAuditLogSync(apiKeyId, userId, clientIp, domain, userAgent, 
                                    responseStatus, responseTimeMs, errorMessage, isSuccessful);
                    
                    // STEP 2: Track quota usage (must succeed)
                    trackQuotaUsageSync(apiKeyId, userId, isSuccessful);
                    
                    log.debug("✅ /rivofetch tracking completed: apiKey={}, status={}", apiKeyId, responseStatus);
                    return null;
                    
                } catch (Exception e) {
                    log.error("❌ Failed to track /rivofetch call, rolling back transaction: apiKey={}, status={}", 
                             apiKeyId, responseStatus, e);
                    status.setRollbackOnly();
                    throw new RuntimeException("Failed to track API call", e);
                }
            });
            
        } catch (Exception e) {
            log.error("❌ Transaction failed for /rivofetch tracking: apiKey={}, status={}", 
                     apiKeyId, responseStatus, e);
            // Don't rethrow - we don't want to break the API call due to tracking issues
        }
    }
    
    /**
     * 📋 Track in audit log (for real-time dashboards) - ASYNC VERSION
     * ✅ Fixed: Uses transactionalAsyncExecutor for proper transaction context
     */
    @Async("transactionalAsyncExecutor")
    private CompletableFuture<Void> trackAuditLog(UUID apiKeyId, String userId, String clientIp, 
                                                 String domain, String userAgent, Integer responseStatus, 
                                                 Long responseTimeMs, String errorMessage, boolean isSuccessful) {
     try {
            // 🔧 Direct call to transactional method - Spring @Async handles threading
               trackAuditLogSync(apiKeyId, userId, clientIp, domain, userAgent, 
                            responseStatus, responseTimeMs, errorMessage, isSuccessful);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to save audit log: apiKey={}", apiKeyId, e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 💰 Track quota usage (for billing) - ASYNC VERSION 
     * ✅ Fixed: Uses transactionalAsyncExecutor for proper transaction context
     */
    @Async("transactionalAsyncExecutor")
    private CompletableFuture<Void> trackQuotaUsage(UUID apiKeyId, String userId, boolean isSuccessful) {
        try {
            // 🔧 Direct call to transactional method - Spring @Async handles threading
            trackQuotaUsageSync(apiKeyId, userId, isSuccessful);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to update quota usage: apiKey={}", apiKeyId, e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 🆕 Create new monthly usage record with correct quota limits based on API key's rate limit tier
     */
    private ApiKeyMonthlyUsage createNewMonthlyUsage(UUID apiKeyId, String userId, String monthYear) {
        try {
            // Look up the API key to get its rate limit tier
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(apiKeyId);
            if (apiKeyOpt.isEmpty()) {
                log.warn("⚠️ API key not found: {}, using FREE tier defaults", apiKeyId);
                return ApiKeyMonthlyUsage.createForCurrentMonth(apiKeyId, userId, 100, 110);
            }
            
            ApiKey apiKey = apiKeyOpt.get();
            RateLimitTier rateLimitTier = apiKey.getRateLimitTier() != null ? apiKey.getRateLimitTier() : RateLimitTier.FREE_TIER;
            
            // Get quota limits based on the actual rate limit tier
            int quotaLimit = rateLimitTier.getRequestsPerMonth();
            int graceLimit = quotaLimit + (int) Math.ceil(quotaLimit * 0.1); // 10% grace
            
            // Handle unlimited tier
            if (rateLimitTier == RateLimitTier.BUSINESS_TIER) {
                quotaLimit = -1; // Unlimited
                graceLimit = -1; // Unlimited
            }
            
            log.debug("📊 Creating monthly usage record for API key {} with tier {} (quota: {}, grace: {})", 
                     apiKeyId, rateLimitTier.name(), quotaLimit, graceLimit);
            
            return ApiKeyMonthlyUsage.createForCurrentMonth(apiKeyId, userId, quotaLimit, graceLimit);
            
        } catch (Exception e) {
            log.error("❌ Failed to create monthly usage record for API key {}, using FREE tier defaults: {}", 
                     apiKeyId, e.getMessage());
            // Fallback to FREE tier defaults
            return ApiKeyMonthlyUsage.createForCurrentMonth(apiKeyId, userId, 100, 110);
        }
    }
    
    // ==================== THREAD-SAFE HELPER METHODS ====================
    
    /**
     * 🔒 Update counters atomically to prevent race conditions (transaction managed by parent)
     */
    private void updateCountersAtomically(UUID usageId, boolean isSuccessful) {
        int rowsUpdated;
        if (isSuccessful) {
            rowsUpdated = quotaRepository.incrementSuccessfulCalls(usageId);
        } else {
            rowsUpdated = quotaRepository.incrementFailedCalls(usageId);
        }
        
        if (rowsUpdated == 0) {
            log.warn("⚠️ Failed to update usage counters for usage record: {}", usageId);
        }
    }
    
    /**
     * 🆕 Create new monthly usage record with proper duplicate handling (transaction managed by parent)
     */
    private ApiKeyMonthlyUsage createNewMonthlyUsageWithDuplicateHandling(UUID apiKeyId, String userId, String monthYear) {
        try {
            // Create and save new record
            ApiKeyMonthlyUsage newUsage = createNewMonthlyUsage(apiKeyId, userId, monthYear);
            return quotaRepository.save(newUsage);
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Handle race condition - another thread created the record
            log.debug("🔄 Duplicate record detected for apiKey={}, monthYear={}, fetching existing record", 
                     apiKeyId, monthYear);
            
            // Try to get the record that was created by another thread
            return quotaRepository.findByApiKeyIdAndMonthYear(apiKeyId, monthYear)
                    .orElse(null); // If still null, something is seriously wrong
                    
        } catch (Exception e) {
            log.error("❌ Unexpected error creating monthly usage record: apiKey={}, monthYear={}", 
                     apiKeyId, monthYear, e);
            return null;
        }
    }
    
    /**
     * 📊 Log quota warnings if needed
     */
    private void logQuotaWarningsIfNeeded(UUID apiKeyId, ApiKeyMonthlyUsage usage) {
        try {
            if (usage.getQuotaLimit() != null && usage.getQuotaLimit() > 0) {
                double usagePercentage = usage.getQuotaUsagePercentage();
                if (usagePercentage >= 90.0) {
                    log.warn("⚠️ High quota usage: {}% for apiKey={} ({}/{})", 
                            String.format("%.1f", usagePercentage), apiKeyId, 
                            usage.getTotalCalls(), usage.getQuotaLimit());
                } else if (usagePercentage >= 80.0) {
                    log.info("📊 Quota usage at {}% for apiKey={} ({}/{})", 
                            String.format("%.1f", usagePercentage), apiKeyId, 
                            usage.getTotalCalls(), usage.getQuotaLimit());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to log quota warnings for apiKey={}: {}", apiKeyId, e.getMessage());
        }
    }
    
    // ==================== SYNCHRONOUS TRANSACTIONAL METHODS ====================
    
    /**
     * 📋 Track in audit log - SYNCHRONOUS VERSION (transaction managed by parent)
     * ✅ FIXED: Create audit log with correct path (includes context path)
     */
    private void trackAuditLogSync(UUID apiKeyId, String userId, String clientIp, 
                                  String domain, String userAgent, Integer responseStatus, 
                                  Long responseTimeMs, String errorMessage, boolean isSuccessful) {
        try {
            ApiKeyRequestLog logEntry = ApiKeyRequestLog.builder()
                    .apiKeyId(apiKeyId)
                    .userFkId(userId)
                    .clientIp(clientIp)
                    .domain(domain)
                    .userAgent(userAgent)
                    .requestMethod("POST")
                    .requestPath("/myapp/api/secure/rivofetch") // ✅ FIXED: Use correct path with context
                    .requestTimestamp(LocalDateTime.now())
                    .responseStatus(responseStatus)
                    .responseTimeMs(responseTimeMs)
                    .errorMessage(errorMessage)
                    .success(isSuccessful)
                    .isAllowedIp(true) // Assuming validated by controller
                    .isAllowedDomain(true) // Assuming validated by controller
                    .build();
            
            auditRepository.save(logEntry);
            log.debug("✅ Audit log saved: apiKey={}, status={}, path={}", apiKeyId, responseStatus, logEntry.getRequestPath());
            
        } catch (Exception e) {
            log.error("❌ Failed to save audit log: apiKey={}", apiKeyId, e);
            throw new RuntimeException("Audit logging failed", e);
        }
    }
    
    /**
     * 💰 Track quota usage - SYNCHRONOUS VERSION (transaction managed by parent)
     */
    private void trackQuotaUsageSync(UUID apiKeyId, String userId, boolean isSuccessful) {
        try {
            String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            // STEP 1: Try to get existing record with pessimistic lock
            Optional<ApiKeyMonthlyUsage> existingUsage = quotaRepository
                    .findByApiKeyIdAndMonthYearForUpdate(apiKeyId, monthYear);
            
            ApiKeyMonthlyUsage usage;
            if (existingUsage.isPresent()) {
                // Record exists - use atomic increment
                usage = existingUsage.get();
                updateCountersAtomically(usage.getId(), isSuccessful);
            } else {
                // STEP 2: Record doesn't exist - create new one with proper duplicate handling
                usage = createNewMonthlyUsageWithDuplicateHandling(apiKeyId, userId, monthYear);
                if (usage != null) {
                    // Update the newly created record
                    updateCountersAtomically(usage.getId(), isSuccessful);
                }
            }
            
            // STEP 3: Log quota warnings (only if we have a valid usage record)
            if (usage != null) {
                logQuotaWarningsIfNeeded(apiKeyId, usage);
            }
            
            log.debug("✅ Quota usage updated: apiKey={}, success={}", apiKeyId, isSuccessful);
            
        } catch (Exception e) {
            log.error("❌ Failed to update quota usage: apiKey={}", apiKeyId, e);
            throw new RuntimeException("Quota tracking failed", e);
        }
    }
    
    /**
     * 📊 Get current usage for API key (for quick checks)
     */
    public Long getCurrentMonthUsage(UUID apiKeyId) {
        try {
            String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            return quotaRepository.findByApiKeyIdAndMonthYear(apiKeyId, monthYear)
                    .map(usage -> usage.getTotalCalls().longValue())
                    .orElse(0L);
        } catch (Exception e) {
            log.error("❌ Failed to get current usage: apiKey={}", apiKeyId, e);
            return 0L;
        }
    }
    
    /**
     * 📊 Check if quota exceeded (for quick checks)
     */
    public boolean isQuotaExceeded(UUID apiKeyId) {
        try {
            String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            return quotaRepository.findByApiKeyIdAndMonthYear(apiKeyId, monthYear)
                    .map(ApiKeyMonthlyUsage::isQuotaExceeded)
                    .orElse(false);
        } catch (Exception e) {
            log.error("❌ Failed to check quota: apiKey={}", apiKeyId, e);
            return false; // Fail open
        }
    }
    
    // ==================== ADDITIONAL METHODS FOR UNIFIED DASHBOARD ====================
    
    /**
     * 📊 Get current month usage for user across all API keys
     */
    public int getCurrentMonthUsageForUser(String userId, String monthYear) {
        try {
            return quotaRepository.getTotalCallsForUser(userId, monthYear);
        } catch (Exception e) {
            log.error("❌ Failed to get current month usage for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * 🎯 CORRECTED: Get current month usage from api_key_usage_stats (REAL DATA SOURCE)
     */
    public int getCurrentMonthUsageForApiKey(UUID apiKeyId, String monthYear) {
        try {
            // Use the REAL data source - api_key_usage_stats
            LocalDateTime currentTime = LocalDateTime.now();
            Optional<ApiKeyUsageStats> currentStats = usageStatsRepository.findCurrentUsageStats(apiKeyId, currentTime);
                if (currentStats.isPresent()) {
                int usage = currentStats.get().getRequestCount() != null ? currentStats.get().getRequestCount() : 0;
                log.debug("📊 Current usage from usage_stats for API key {}: {}", apiKeyId, usage);
                return usage;
            } else {
                log.debug("📊 No usage stats found for API key {}", apiKeyId);
                return 0;
            }
        
        } catch (Exception e) {
            log.error("❌ Failed to get current month usage for API key {}: {}", apiKeyId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * 📊 Get usage statistics for dashboard
     */
    public UsageStats getUsageStats(UUID apiKeyId, String monthYear) {
        try {
            return quotaRepository.findByApiKeyIdAndMonthYear(apiKeyId, monthYear)
                    .map(usage -> UsageStats.builder()
                        .totalCalls(usage.getTotalCalls())
                        .successfulCalls(usage.getSuccessfulCalls())
                        .failedCalls(usage.getFailedCalls())
                        .quotaLimit(usage.getQuotaLimit())
                        .usagePercentage(usage.getQuotaUsagePercentage())
                        .isQuotaExceeded(usage.isQuotaExceeded())
                        .build())
                    .orElse(UsageStats.empty());
        } catch (Exception e) {
            log.error("❌ Failed to get usage stats for API key {}: {}", apiKeyId, e.getMessage());
            return UsageStats.empty();
        }
    }
    
    /**
     * 📊 Get user total usage across all API keys
     */
    public UsageStats getUserTotalUsage(String userId, String monthYear) {
        try {
            Integer totalCalls = quotaRepository.getTotalCallsForUser(userId, monthYear);
            Integer successfulCalls = quotaRepository.getSuccessfulCallsForUser(userId, monthYear);
            Integer failedCalls = quotaRepository.getFailedCallsForUser(userId, monthYear);
            
            return UsageStats.builder()
                .totalCalls(totalCalls != null ? totalCalls : 0)
                .successfulCalls(successfulCalls != null ? successfulCalls : 0)
                .failedCalls(failedCalls != null ? failedCalls : 0)
                .quotaLimit(null) // User-level quota not implemented yet
                .usagePercentage(0.0)
                .isQuotaExceeded(false)
                .build();
        } catch (Exception e) {
            log.error("❌ Failed to get user total usage for {}: {}", userId, e.getMessage());
            return UsageStats.empty();
        }
    }
    
    // ==================== USAGE STATS DATA CLASS ====================
    
    @lombok.Data
    @lombok.Builder
    public static class UsageStats {
        private Integer totalCalls;
        private Integer successfulCalls;
        private Integer failedCalls;
        private Integer quotaLimit;
        private Double usagePercentage;
        private Boolean isQuotaExceeded;
        
        public static UsageStats empty() {
            return UsageStats.builder()
                .totalCalls(0)
                .successfulCalls(0)
                .failedCalls(0)
                .quotaLimit(0)
                .usagePercentage(0.0)
                .isQuotaExceeded(false)
                .build();
        }
    }
}