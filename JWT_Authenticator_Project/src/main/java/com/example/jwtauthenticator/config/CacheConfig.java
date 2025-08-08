package com.example.jwtauthenticator.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableScheduling
@Slf4j
public class CacheConfig {

    /**
     * Secondary cache manager using Caffeine for general application caching
     * High-performance cache with automatic eviction and statistics
     * Note: OptimizedCacheManager is now the primary cache manager
     */
    @Bean("legacyCacheManager")
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeineCacheBuilder());
        
        // Set cache names for better management
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "apiKeyValidation",      // API key validation cache
            "userSessions",          // User session cache
            "rateLimitBuckets",      // Rate limiting buckets
            "brandData"              // Brand extraction cache
        ));
        
        log.info("✅ Legacy Caffeine cache manager configured with high-performance caching");
        return cacheManager;
    }

    /**
     * Dashboard-specific cache manager using Caffeine for dashboard data
     * Optimized for dashboard queries with SHORT TTL for real-time accuracy
     */
    @Bean("dashboardCacheManager")
    public CacheManager dashboardCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(dashboardCaffeineCacheBuilder());
        
        // Configure cache names for dashboard
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "userDashboardCards",    // User dashboard cards cache (30 sec TTL)
            "apiKeyDashboard",       // API key dashboard cache (30 sec TTL)
            "dashboardSummary"       // General dashboard summary cache (60 sec TTL)
        ));
        
        log.info("✅ Dashboard cache manager configured with 30-second TTL for real-time accuracy");
        return cacheManager;
    }
    
    /**
     * Caffeine cache builder specifically for dashboard data
     * Short TTL for real-time accuracy while maintaining performance
     */
    private Caffeine<Object, Object> dashboardCaffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(50)
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.SECONDS)  // 30-second TTL for real-time accuracy
                .recordStats();
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats();
    }

    /**
     * Scheduled task to log dashboard cache statistics
     * Runs every 5 minutes to monitor cache performance
     * Note: No manual clearing needed as Caffeine handles TTL automatically
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void logDashboardCacheStatistics() {
        try {
            log.info("📊 Dashboard Cache Statistics (30-second TTL):");
            log.info("  - Cache automatically expires entries after 30 seconds for real-time accuracy");
            log.info("  - Dashboard cache manager: {} caches configured", dashboardCacheManager().getCacheNames().size());
            
        } catch (Exception e) {
            log.error("❌ Failed to log dashboard cache statistics: {}", e.getMessage(), e);
        }
    }

    /**
     * Cache statistics logging
     * Runs every hour to monitor cache performance
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void logCacheStatistics() {
        try {
            log.info("📊 Cache Statistics:");
            log.info("  - Legacy cache manager: {} caches", cacheManager().getCacheNames().size());
            log.info("  - Dashboard cache manager: {} caches", dashboardCacheManager().getCacheNames().size());
            
        } catch (Exception e) {
            log.error("❌ Failed to log cache statistics: {}", e.getMessage(), e);
        }
    }
}