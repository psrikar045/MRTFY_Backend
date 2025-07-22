package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKeyRateLimit;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyRateLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Hybrid Rate Limiting Service that combines in-memory caching with database persistence.
 * 
 * FEATURES:
 * - In-memory cache for fast rate limit checks
 * - Database persistence for distributed systems and restarts
 * - Automatic cleanup of expired records
 * - Thread-safe operations
 * - Multiple rate limit tiers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    
    private final ApiKeyRateLimitRepository rateLimitRepository;
    
    // In-memory cache for fast access: apiKeyHash -> RateLimitInfo
    private final ConcurrentHashMap<String, RateLimitInfo> memoryCache = new ConcurrentHashMap<>();
    
    // Cache expiration time (5 minutes)
    private static final long CACHE_EXPIRY_MINUTES = 5;
    
    /**
     * Inner class to represent rate limit information in memory cache.
     */
    private static class RateLimitInfo {
        private int requestCount;
        private LocalDateTime windowStart;
        private LocalDateTime windowEnd;
        private LocalDateTime lastAccess;
        private RateLimitTier tier;
        
        public RateLimitInfo(RateLimitTier tier) {
            this.tier = tier;
            this.requestCount = 0;
            this.windowStart = LocalDateTime.now();
            this.windowEnd = windowStart.plus(tier.getWindowDuration());
            this.lastAccess = LocalDateTime.now();
        }
        
        public synchronized boolean isAllowed() {
            LocalDateTime now = LocalDateTime.now();
            
            // Check if window has expired
            if (now.isAfter(windowEnd)) {
                // Reset for new window
                resetWindow(now);
            }
            
            // Update last access
            lastAccess = now;
            
            // Check if unlimited
            if (tier.isUnlimited()) {
                requestCount++;
                return true;
            }
            
            // Check if within limit
            if (requestCount < tier.getRequestsPerWindow()) {
                requestCount++;
                return true;
            }
            
            return false; // Rate limit exceeded
        }
        
        private void resetWindow(LocalDateTime now) {
            this.requestCount = 0;
            this.windowStart = now;
            this.windowEnd = now.plus(tier.getWindowDuration());
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().minusMinutes(CACHE_EXPIRY_MINUTES).isAfter(lastAccess);
        }
    }
    
    /**
     * Check if a request is allowed for the given API key.
     * 
     * @param apiKeyHash The hashed API key
     * @param rateLimitTier The rate limit tier for this key
     * @return true if request is allowed, false if rate limited
     */
    public boolean isAllowed(String apiKeyHash, RateLimitTier rateLimitTier) {
        // First check memory cache (fast path)
        RateLimitInfo cachedInfo = memoryCache.get(apiKeyHash);
        
        if (cachedInfo != null && !cachedInfo.isExpired()) {
            boolean allowed = cachedInfo.isAllowed();
            
            // Asynchronously update database (don't block the request)
            updateDatabaseAsync(apiKeyHash, cachedInfo);
            
            return allowed;
        }
        
        // Cache miss or expired - check/create database record (slower path)
        return checkDatabaseRateLimit(apiKeyHash, rateLimitTier);
    }
    
    /**
     * Check rate limit against database and update cache.
     */
    @Transactional
    private boolean checkDatabaseRateLimit(String apiKeyHash, RateLimitTier rateLimitTier) {
        LocalDateTime now = LocalDateTime.now();
        
        // Find active rate limit window
        Optional<ApiKeyRateLimit> activeRateLimit = rateLimitRepository.findActiveRateLimit(apiKeyHash, now);
        
        ApiKeyRateLimit rateLimit;
        
        if (activeRateLimit.isPresent()) {
            rateLimit = activeRateLimit.get();
            
            // Check if limit exceeded
            if (!rateLimitTier.isUnlimited() && 
                rateLimit.getRequestCount() >= rateLimitTier.getRequestsPerWindow()) {
                log.debug("Rate limit exceeded for API key hash: {}", apiKeyHash);
                return false;
            }
            
            // Increment count
            rateLimit.incrementRequestCount();
        } else {
            // Create new rate limit window
            rateLimit = ApiKeyRateLimit.builder()
                .apiKeyHash(apiKeyHash)
                .requestCount(1)
                .windowStart(now)
                .windowEnd(now.plus(rateLimitTier.getWindowDuration()))
                .build();
        }
        
        // Save to database
        rateLimitRepository.save(rateLimit);
        
        // Update memory cache
        RateLimitInfo cacheInfo = new RateLimitInfo(rateLimitTier);
        cacheInfo.requestCount = rateLimit.getRequestCount();
        cacheInfo.windowStart = rateLimit.getWindowStart();
        cacheInfo.windowEnd = rateLimit.getWindowEnd();
        memoryCache.put(apiKeyHash, cacheInfo);
        
        log.debug("Rate limit check passed for API key hash: {} (count: {}/{})", 
                 apiKeyHash, rateLimit.getRequestCount(), rateLimitTier.getRequestsPerWindow());
        
        return true;
    }
    
    /**
     * Asynchronously update database with current cache state.
     */
    private void updateDatabaseAsync(String apiKeyHash, RateLimitInfo cacheInfo) {
        // This could be enhanced with @Async annotation for true async processing
        try {
            Optional<ApiKeyRateLimit> dbRecord = rateLimitRepository.findActiveRateLimit(
                apiKeyHash, LocalDateTime.now());
                
            if (dbRecord.isPresent()) {
                ApiKeyRateLimit rateLimit = dbRecord.get();
                rateLimit.setRequestCount(cacheInfo.requestCount);
                rateLimitRepository.save(rateLimit);
            }
        } catch (Exception e) {
            log.error("Failed to update database rate limit for key hash: {}", apiKeyHash, e);
        }
    }
    
    /**
     * Get current rate limit status for an API key.
     */
    public RateLimitStatus getRateLimitStatus(String apiKeyHash, RateLimitTier rateLimitTier) {
        // Check memory cache first
        RateLimitInfo cachedInfo = memoryCache.get(apiKeyHash);
        if (cachedInfo != null && !cachedInfo.isExpired()) {
            return new RateLimitStatus(
                cachedInfo.requestCount,
                rateLimitTier.getRequestsPerWindow(),
                cachedInfo.windowEnd,
                rateLimitTier
            );
        }
        
        // Check database
        LocalDateTime now = LocalDateTime.now();
        Optional<ApiKeyRateLimit> activeRateLimit = rateLimitRepository.findActiveRateLimit(apiKeyHash, now);
        
        if (activeRateLimit.isPresent()) {
            ApiKeyRateLimit rateLimit = activeRateLimit.get();
            return new RateLimitStatus(
                rateLimit.getRequestCount(),
                rateLimitTier.getRequestsPerWindow(),
                rateLimit.getWindowEnd(),
                rateLimitTier
            );
        }
        
        // No active rate limit found
        return new RateLimitStatus(0, rateLimitTier.getRequestsPerWindow(), 
                                 now.plus(rateLimitTier.getWindowDuration()), rateLimitTier);
    }
    
    /**
     * Clear rate limit for an API key (admin function).
     */
    @Transactional
    public void clearRateLimit(String apiKeyHash) {
        memoryCache.remove(apiKeyHash);
        
        // Remove active database records
        LocalDateTime now = LocalDateTime.now();
        Optional<ApiKeyRateLimit> activeRateLimit = rateLimitRepository.findActiveRateLimit(apiKeyHash, now);
        activeRateLimit.ifPresent(rateLimitRepository::delete);
        
        log.info("Rate limit cleared for API key hash: {}", apiKeyHash);
    }
    
    /**
     * Scheduled cleanup of expired cache entries and database records.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupExpiredRecords() {
        // Clean memory cache
        memoryCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        
        // Clean database records (older than 24 hours)
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        int deletedCount = rateLimitRepository.deleteExpiredRateLimits(cutoff);
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} expired rate limit records", deletedCount);
        }
    }
    
    /**
     * Get total request count for an API key in the last 24 hours.
     */
    public long getTotalRequestCount(String apiKeyHash) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(24);
        return rateLimitRepository.getTotalRequestCount(apiKeyHash, start, end);
    }
    
    /**
     * Rate limit status DTO.
     */
    public static class RateLimitStatus {
        private final int currentRequests;
        private final int maxRequests;
        private final LocalDateTime windowEnd;
        private final RateLimitTier tier;
        
        public RateLimitStatus(int currentRequests, int maxRequests, LocalDateTime windowEnd, RateLimitTier tier) {
            this.currentRequests = currentRequests;
            this.maxRequests = maxRequests;
            this.windowEnd = windowEnd;
            this.tier = tier;
        }
        
        // Getters
        public int getCurrentRequests() { return currentRequests; }
        public int getMaxRequests() { return maxRequests; }
        public LocalDateTime getWindowEnd() { return windowEnd; }
        public RateLimitTier getTier() { return tier; }
        public int getRemainingRequests() { 
            return tier.isUnlimited() ? -1 : Math.max(0, maxRequests - currentRequests); 
        }
        public boolean isLimited() { return currentRequests >= maxRequests && !tier.isUnlimited(); }
    }
}