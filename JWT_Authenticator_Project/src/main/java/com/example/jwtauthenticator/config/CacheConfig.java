package com.example.jwtauthenticator.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
     * Primary cache manager using Caffeine for general application caching
     * High-performance cache with automatic eviction and statistics
     */
    @Bean
    @Primary
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
        
        log.info("✅ Primary Caffeine cache manager configured with high-performance caching");
        return cacheManager;
    }

    /**
     * Dashboard-specific cache manager using ConcurrentMap for dashboard data
     * Optimized for dashboard queries with shorter TTL
     */
    @Bean("dashboardCacheManager")
    public CacheManager dashboardCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // Configure cache names for dashboard
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "userDashboardCards",    // User dashboard cards cache (5 min TTL)
            "apiKeyDashboard",       // API key dashboard cache (3 min TTL)
            "dashboardSummary"       // General dashboard summary cache (10 min TTL)
        ));
        
        // Allow dynamic cache creation
        cacheManager.setAllowNullValues(false);
        
        log.info("✅ Dashboard cache manager configured for dashboard-specific caching");
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats();
    }

    /**
     * Scheduled task to clear old dashboard cache entries
     * Runs every 10 minutes to prevent memory buildup
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void clearOldDashboardCacheEntries() {
        try {
            CacheManager dashboardCacheManager = dashboardCacheManager();
            
            // Clear specific caches periodically to ensure fresh data
            if (dashboardCacheManager.getCache("userDashboardCards") != null) {
                dashboardCacheManager.getCache("userDashboardCards").clear();
                log.debug("🧹 Cleared userDashboardCards cache");
            }
            
            if (dashboardCacheManager.getCache("apiKeyDashboard") != null) {
                dashboardCacheManager.getCache("apiKeyDashboard").clear();
                log.debug("🧹 Cleared apiKeyDashboard cache");
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to clear dashboard cache entries: {}", e.getMessage(), e);
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
            log.info("  - Primary cache manager: {} caches", cacheManager().getCacheNames().size());
            log.info("  - Dashboard cache manager: {} caches", dashboardCacheManager().getCacheNames().size());
            
        } catch (Exception e) {
            log.error("❌ Failed to log cache statistics: {}", e.getMessage(), e);
        }
    }
}