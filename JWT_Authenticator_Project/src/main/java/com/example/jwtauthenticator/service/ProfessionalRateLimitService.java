package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.ApiKeyUsageStats;
import com.example.jwtauthenticator.entity.ApiKeyAddOn;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.ApiKeyUsageStatsRepository;
import com.example.jwtauthenticator.repository.ApiKeyAddOnRepository;
import com.example.jwtauthenticator.util.RateLimitWindowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Professional-grade rate limiting service with time-window based limits
 * Follows industry standards used by companies like AWS, Google, Stripe
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfessionalRateLimitService {

    private final ApiKeyUsageStatsRepository usageStatsRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyAddOnRepository addOnRepository;

    /**
     * Check if request is allowed and update usage statistics
     * 
     * @param apiKeyId The API key ID
     * @return RateLimitResult containing decision and metadata
     */
    @Transactional
    public RateLimitResult checkRateLimit(String apiKeyId) {
        try {
            // Get API key details
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findById(java.util.UUID.fromString(apiKeyId));
            if (apiKeyOpt.isEmpty()) {
                return RateLimitResult.denied("API key not found");
            }

            ApiKey apiKey = apiKeyOpt.get();
            RateLimitTier tier = apiKey.getRateLimitTier() != null ? apiKey.getRateLimitTier() : RateLimitTier.FREE_TIER;
            
            // Unlimited tier always allows requests
            if (tier.isUnlimited()) {
                updateUsageStatsForUnlimited(apiKey, tier);
                return RateLimitResult.allowed(tier, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            }

            LocalDateTime now = LocalDateTime.now();
            
            // Get or create current usage stats
            ApiKeyUsageStats usageStats = getCurrentUsageStats(apiKey, tier, now);
            
            // Check if base rate limit is exceeded
            if (usageStats.isRateLimitExceeded()) {
                // Try to use add-on requests
                List<ApiKeyAddOn> activeAddOns = addOnRepository.findActiveAddOnsForApiKey(apiKeyId, now);
                
                boolean addOnUsed = false;
                for (ApiKeyAddOn addOn : activeAddOns) {
                    if (addOn.useRequests(1)) {
                        addOnRepository.save(addOn);
                        addOnUsed = true;
                        log.info("Used add-on request for API key {}: {} remaining in add-on {}", 
                                apiKeyId, addOn.getRequestsRemaining(), addOn.getId());
                        break;
                    }
                }
                
                if (!addOnUsed) {
                    usageStats.incrementBlockedRequests();
                    usageStatsRepository.save(usageStats);
                    
                    long resetInSeconds = ChronoUnit.SECONDS.between(now, usageStats.getWindowEnd());
                    int totalAdditionalAvailable = addOnRepository.getTotalAdditionalRequestsAvailable(apiKeyId, now);
                    
                    return RateLimitResult.denied(
                        "Rate limit exceeded. Consider purchasing add-on requests.", 
                        tier, 
                        usageStats.getRequestCount(),
                        usageStats.getRemainingRequests(),
                        resetInSeconds,
                        totalAdditionalAvailable
                    );
                }
                
                // Add-on was used, continue with success flow but don't increment base stats
                return RateLimitResult.allowedWithAddOn(
                    tier,
                    usageStats.getRequestCount(),
                    usageStats.getRemainingRequests(),
                    getTotalAdditionalRequestsRemaining(UUID.fromString(apiKeyId), now)
                );
            }

            // Allow request and update stats
            usageStats.incrementRequestCount();
            usageStatsRepository.save(usageStats);

            log.debug("Rate limit check passed for API key {}: {}/{} requests used", 
                     apiKeyId, usageStats.getRequestCount(), usageStats.getRequestLimit());

            return RateLimitResult.allowed(
                tier,
                usageStats.getRequestCount(),
                usageStats.getRemainingRequests(),
                getTotalAdditionalRequestsRemaining(UUID.fromString(apiKeyId), now)
            );

        } catch (Exception e) {
            log.error("Error checking rate limit for API key {}", apiKeyId, e);
            return RateLimitResult.denied("Rate limit check failed");
        }
    }

    /**
     * Get current usage stats for the API key, creating new window if needed
     */
    private ApiKeyUsageStats getCurrentUsageStats(ApiKey apiKey, RateLimitTier tier, LocalDateTime now) {
        Optional<ApiKeyUsageStats> currentStatsOpt = 
            usageStatsRepository.findCurrentUsageStats(apiKey.getId(), now);

        if (currentStatsOpt.isPresent()) {
            ApiKeyUsageStats stats = currentStatsOpt.get();
            if (stats.isCurrentWindowValid()) {
                return stats;
            }
            // Current window expired, reset for new window
            LocalDateTime windowStart = RateLimitWindowUtil.getWindowStart(now, tier);
            LocalDateTime windowEnd = RateLimitWindowUtil.getWindowEnd(windowStart, tier);
            stats.resetForNewWindow(windowStart, windowEnd);
            return stats;
        }

        // Create new usage stats for new window
        LocalDateTime windowStart = RateLimitWindowUtil.getWindowStart(now, tier);
        LocalDateTime windowEnd = RateLimitWindowUtil.getWindowEnd(windowStart, tier);

        return ApiKeyUsageStats.builder()
                .apiKeyId(apiKey.getId())
                .userFkId(apiKey.getUserFkId())
                .rateLimitTier(tier)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .requestCount(0)
                .requestLimit(tier.getRequestLimit())
                .remainingRequests(tier.getRequestLimit())
                .totalRequestsLifetime(0L)
                .blockedRequests(0)
                .isRateLimited(false)
                .build();
    }

    /**
     * Update usage stats for unlimited tier (for statistics purposes)
     */
    private void updateUsageStatsForUnlimited(ApiKey apiKey, RateLimitTier tier) {
        LocalDateTime now = LocalDateTime.now();
        Optional<ApiKeyUsageStats> currentStatsOpt = 
            usageStatsRepository.findCurrentUsageStats(apiKey.getId(), now);

        ApiKeyUsageStats stats;
        if (currentStatsOpt.isPresent()) {
            stats = currentStatsOpt.get();
        } else {
            LocalDateTime windowStart = RateLimitWindowUtil.getWindowStart(now, tier);
            LocalDateTime windowEnd = RateLimitWindowUtil.getWindowEnd(windowStart, tier);
            
            stats = ApiKeyUsageStats.builder()
                    .apiKeyId(apiKey.getId())
                    .userFkId(apiKey.getUserFkId())
                    .rateLimitTier(tier)
                    .windowStart(windowStart)
                    .windowEnd(windowEnd)
                    .requestCount(0)
                    .requestLimit(Integer.MAX_VALUE)
                    .remainingRequests(Integer.MAX_VALUE)
                    .totalRequestsLifetime(0L)
                    .blockedRequests(0)
                    .isRateLimited(false)
                    .build();
        }

        stats.incrementRequestCount();
        usageStatsRepository.save(stats);
    }

    /**
     * Reset rate limit for an API key (admin function)
     */
    @Transactional
    public void resetRateLimit(UUID apiKeyId) {
        LocalDateTime now = LocalDateTime.now();
        Optional<ApiKeyUsageStats> currentStatsOpt = 
            usageStatsRepository.findCurrentUsageStats(apiKeyId, now);

        if (currentStatsOpt.isPresent()) {
            ApiKeyUsageStats stats = currentStatsOpt.get();
            stats.setRequestCount(0);
            stats.setRemainingRequests(stats.getRequestLimit());
            stats.setIsRateLimited(false);
            stats.setRateLimitResetAt(null);
            usageStatsRepository.save(stats);
            
            log.info("Rate limit reset for API key: {}", apiKeyId);
        }
    }

    /**
     * Get current usage statistics for an API key
     */
    public Optional<ApiKeyUsageStats> getCurrentUsage(UUID apiKeyId) {
        return usageStatsRepository.findCurrentUsageStats(apiKeyId, LocalDateTime.now());
    }

    /**
     * Get total additional requests remaining from all active add-ons
     */
    private int getTotalAdditionalRequestsRemaining(UUID apiKeyId, LocalDateTime now) {
        Integer total = addOnRepository.getTotalAdditionalRequestsAvailable(apiKeyId.toString(), now);
        return total != null ? total : 0;
    }

    /**
     * Result class for rate limit decisions with add-on support
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String reason;
        private final RateLimitTier tier;
        private final Integer requestCount;
        private final Integer remainingRequests;
        private final Long resetInSeconds;
        private final Integer additionalRequestsRemaining;
        private final boolean usedAddOn;

        private RateLimitResult(boolean allowed, String reason, RateLimitTier tier, 
                               Integer requestCount, Integer remainingRequests, Long resetInSeconds,
                               Integer additionalRequestsRemaining, boolean usedAddOn) {
            this.allowed = allowed;
            this.reason = reason;
            this.tier = tier;
            this.requestCount = requestCount;
            this.remainingRequests = remainingRequests;
            this.resetInSeconds = resetInSeconds;
            this.additionalRequestsRemaining = additionalRequestsRemaining;
            this.usedAddOn = usedAddOn;
        }

        public static RateLimitResult allowed(RateLimitTier tier, Integer requestCount, 
                                            Integer remainingRequests, Integer additionalRequestsRemaining) {
            return new RateLimitResult(true, null, tier, requestCount, remainingRequests, 
                                     null, additionalRequestsRemaining, false);
        }

        public static RateLimitResult allowedWithAddOn(RateLimitTier tier, Integer requestCount, 
                                                     Integer remainingRequests, Integer additionalRequestsRemaining) {
            return new RateLimitResult(true, "Used add-on request", tier, requestCount, remainingRequests, 
                                     null, additionalRequestsRemaining, true);
        }

        public static RateLimitResult denied(String reason) {
            return new RateLimitResult(false, reason, null, null, null, null, null, false);
        }

        public static RateLimitResult denied(String reason, RateLimitTier tier, Integer requestCount, 
                                           Integer remainingRequests, Long resetInSeconds, 
                                           Integer additionalRequestsRemaining) {
            return new RateLimitResult(false, reason, tier, requestCount, remainingRequests, 
                                     resetInSeconds, additionalRequestsRemaining, false);
        }

        // Getters
        public boolean isAllowed() { return allowed; }
        public String getReason() { return reason; }
        public RateLimitTier getTier() { return tier; }
        public Integer getRequestCount() { return requestCount; }
        public Integer getRemainingRequests() { return remainingRequests; }
        public Long getResetInSeconds() { return resetInSeconds; }
        public Integer getAdditionalRequestsRemaining() { return additionalRequestsRemaining; }
        public boolean isUsedAddOn() { return usedAddOn; }
        
        public int getTotalRequestsRemaining() {
            int base = remainingRequests != null ? remainingRequests : 0;
            int additional = additionalRequestsRemaining != null ? additionalRequestsRemaining : 0;
            return base + additional;
        }
    }
}