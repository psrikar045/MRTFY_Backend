package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKeyRequestLog;
import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.ApiKeyUsageStatsRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * 🎯 JWT Usage Tracking Service for /forward endpoint
 * 
 * Tracks JWT user requests in the same tables as API key requests
 * to ensure consistent tracking behavior with /rivofeetch endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForwardJwtUsageTrackingService {
    
    private final ApiKeyRequestLogRepository requestLogRepository;
    private final ApiKeyMonthlyUsageRepository monthlyUsageRepository;
    private final ApiKeyUsageStatsRepository usageStatsRepository;
    private final RivoFetchLoggingService rivoFetchLoggingService;
    private final UserRepository userRepository;
    private final RequestContextExtractorService requestContextExtractor;
    
    /**
     * Track JWT usage in same tables as API key requests for consistency
     * ✅ CRITICAL FIX: Also update monthly usage table like /rivofeetch does
     */
    @Transactional
    public void trackJwtUsage(String userId, UserPlan plan, String url, HttpServletRequest request, 
                             int responseStatus, long responseTimeMs, String errorMessage) {
        try {
            // Get user details
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("User not found for JWT tracking: {}", userId);
                return;
            }
            
            User user = userOpt.get();
            boolean isSuccessful = responseStatus >= 200 && responseStatus < 300;
            
            // STEP 1: Track in API key request log table (for consistency with /rivofeetch)
            ApiKeyRequestLog logEntry = ApiKeyRequestLog.builder()
                .apiKeyId(null) // No API key for JWT requests
                .userFkId(userId)
                .clientIp(requestContextExtractor.extractClientIp(request))
                .domain(requestContextExtractor.extractDomain(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestMethod(request.getMethod())
                .requestPath("/forward") // JWT requests go through /forward endpoint
                .queryString("target=" + url) // Store target URL in query string
                .requestTimestamp(LocalDateTime.now())
                .responseStatus(responseStatus)
                .responseTimeMs(responseTimeMs)
                .errorMessage(errorMessage)
                .success(isSuccessful)
                .isAllowedIp(true) // JWT users don't have IP restrictions
                .isAllowedDomain(true) // JWT users don't have domain restrictions
                .build();
                
            requestLogRepository.save(logEntry);
            
            // STEP 2: ✅ CRITICAL FIX: Track monthly usage like API key requests do
            trackJwtMonthlyUsage(userId, plan, isSuccessful);
            
            // STEP 3: ✅ CRITICAL FIX: Track JWT usage stats (like API key usage stats)
            trackJwtUsageStats(userId, plan, isSuccessful);
            
            // STEP 4: Also log in RivoFetch tables for consistency with /rivofeetch
            if (isSuccessful) {
                rivoFetchLoggingService.logSuccessfulPublicRivoFetchAsync(
                    request, null, System.currentTimeMillis() - responseTimeMs, 
                    String.format("{\"jwt_user\": true, \"plan\": \"%s\"}", plan.getDisplayName()), 
                    "JWT_REQUEST", url
                );
            } else {
                rivoFetchLoggingService.logFailedPublicRivoFetchAsync(
                    request, System.currentTimeMillis() - responseTimeMs, 
                    errorMessage != null ? errorMessage : "JWT request failed", responseStatus, url
                );
            }
            
            log.debug("✅ JWT usage tracked for user: {} (plan: {}, status: {}, url: {})", 
                     user.getUsername(), plan.getDisplayName(), responseStatus, url);
            
        } catch (Exception e) {
            log.error("❌ Failed to track JWT usage for user {}: {}", userId, e.getMessage(), e);
            // Don't throw - we don't want to break the API call due to tracking issues
        }
    }
    
    /**
     * ✅ CRITICAL FIX: Track JWT monthly usage using special JWT API key
     * This ensures JWT users have their usage counted in the same monthly usage table
     */
    private void trackJwtMonthlyUsage(String userId, UserPlan plan, boolean isSuccessful) {
        try {
            String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            
            // Create a special "JWT API key" UUID for tracking JWT users
            // Use deterministic UUID based on userId to ensure consistency
            UUID jwtApiKeyId = UUID.nameUUIDFromBytes(("JWT-" + userId).getBytes());
            
            // Try to find existing JWT usage record
            Optional<ApiKeyMonthlyUsage> existingUsage = monthlyUsageRepository
                .findByApiKeyIdAndMonthYear(jwtApiKeyId, monthYear);
            
            ApiKeyMonthlyUsage usage;
            if (existingUsage.isPresent()) {
                // Update existing record
                usage = existingUsage.get();
            } else {
                // Create new JWT usage record
                usage = ApiKeyMonthlyUsage.builder()
                    .apiKeyId(jwtApiKeyId) // Special JWT API key ID
                    .userId(userId)
                    .monthYear(monthYear)
                    .totalCalls(0)
                    .successfulCalls(0)
                    .failedCalls(0)
                    .quotaExceededCalls(0)
                    .lastResetDate(LocalDate.now().withDayOfMonth(1))
                    .quotaLimit(plan.getMonthlyApiCalls()) // Use plan limits
                    .graceLimit(plan.getMonthlyApiCalls() + 50) // Add 50 grace calls
                    .build();
            }
            
            // Update counters
            if (isSuccessful) {
                usage.incrementSuccessfulCalls();
            } else {
                usage.incrementFailedCalls();
            }
            
            // Save the usage record
            monthlyUsageRepository.save(usage);
            
            log.debug("✅ JWT monthly usage updated: userId={}, plan={}, totalCalls={}, quota={}", 
                     userId, plan.getDisplayName(), usage.getTotalCalls(), usage.getQuotaLimit());
            
        } catch (Exception e) {
            log.error("❌ Failed to track JWT monthly usage for user {}: {}", userId, e.getMessage());
            // Don't throw - we don't want to break the API call
        }
    }
    
    /**
     * ✅ CRITICAL FIX: Track JWT usage stats using special JWT API key
     * This ensures JWT users are tracked in api_key_usage_stats just like API key users
     */
    private void trackJwtUsageStats(String userId, UserPlan plan, boolean isSuccessful) {
        try {
            // Create a special "JWT API key" UUID for tracking JWT users (consistent with monthly usage)
            UUID jwtApiKeyId = UUID.nameUUIDFromBytes(("JWT-" + userId).getBytes());
            
            // Map UserPlan to RateLimitTier
            RateLimitTier rateLimitTier = mapUserPlanToRateLimitTier(plan);
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.withMinute(0).withSecond(0).withNano(0); // Hour window
            LocalDateTime windowEnd = windowStart.plusHours(1);
            
            // Try to find existing JWT usage stats for current window
            Optional<ApiKeyUsageStats> existingStats = usageStatsRepository
                .findCurrentUsageStats(jwtApiKeyId, now);
                
            ApiKeyUsageStats stats;
            if (existingStats.isPresent()) {
                ApiKeyUsageStats existing = existingStats.get();
                if (existing.isCurrentWindowValid()) {
                    // Update existing record
                    stats = existing;
                } else {
                    // Window expired, reset for new window
                    existing.resetForNewWindow(windowStart, windowEnd);
                    stats = existing;
                }
            } else {
                // Create new JWT usage stats record
                stats = ApiKeyUsageStats.builder()
                    .apiKeyId(jwtApiKeyId) // Special JWT API key ID
                    .userFkId(userId)
                    .rateLimitTier(rateLimitTier)
                    .windowStart(windowStart)
                    .windowEnd(windowEnd)
                    .requestCount(0)
                    .requestLimit((int) rateLimitTier.getRequestsPerHour())
                    .remainingRequests((int) rateLimitTier.getRequestsPerHour())
                    .totalRequestsLifetime(0L)
                    .blockedRequests(0)
                    .peakRequestsPerMinute(0)
                    .isRateLimited(false)
                    .build();
            }
            
            // Update counters
            stats.incrementRequestCount();
            
            // Save the usage stats record
            usageStatsRepository.save(stats);
            
            log.debug("✅ JWT usage stats updated: userId={}, plan={}, requestCount={}, remaining={}", 
                     userId, plan.getDisplayName(), stats.getRequestCount(), stats.getRemainingRequests());
            
        } catch (Exception e) {
            log.error("❌ Failed to track JWT usage stats for user {}: {}", userId, e.getMessage());
            // Don't throw - we don't want to break the API call
        }
    }
    
    /**
     * Map UserPlan to RateLimitTier for JWT usage stats
     */
    private RateLimitTier mapUserPlanToRateLimitTier(UserPlan plan) {
        switch (plan) {
            case FREE:
                return RateLimitTier.FREE_TIER;
            case PRO:
                return RateLimitTier.PRO_TIER;
            case BUSINESS:
                return RateLimitTier.BUSINESS_TIER;
            default:
                return RateLimitTier.FREE_TIER;
        }
    }

    /**
     * Track successful JWT request
     */
    public void trackSuccessfulJwtRequest(String userId, UserPlan plan, String url, 
                                        HttpServletRequest request, long responseTimeMs) {
        trackJwtUsage(userId, plan, url, request, 200, responseTimeMs, null);
    }
    
    /**
     * Track failed JWT request
     */
    public void trackFailedJwtRequest(String userId, UserPlan plan, String url, 
                                    HttpServletRequest request, int responseStatus, 
                                    long responseTimeMs, String errorMessage) {
        trackJwtUsage(userId, plan, url, request, responseStatus, responseTimeMs, errorMessage);
    }
}