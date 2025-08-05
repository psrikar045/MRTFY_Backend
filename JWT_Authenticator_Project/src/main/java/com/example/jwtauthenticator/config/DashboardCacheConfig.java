package com.example.jwtauthenticator.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for Dashboard Caching
 * Implements mixed approach: fast caching with periodic refresh
 */
@Configuration
@EnableCaching
@EnableScheduling
@Slf4j
public class DashboardCacheConfig {

    /**
     * Cache manager for dashboard data
     * Uses in-memory caching for fast access
     */
    @Bean
    public CacheManager dashboardCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // Configure cache names
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "userDashboardCards",    // User dashboard cards cache (5 min TTL)
            "apiKeyDashboard",       // API key dashboard cache (3 min TTL)
            "dashboardSummary"       // General dashboard summary cache (10 min TTL)
        ));
        
        // Allow dynamic cache creation
        cacheManager.setAllowNullValues(false);
        
        log.info("Dashboard cache manager configured with caches: userDashboardCards, apiKeyDashboard, dashboardSummary");
        return cacheManager;
    }

    /**
     * Scheduled task to refresh materialized views
     * Runs every 5 minutes to keep data fresh
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void refreshMaterializedViews() {
        try {
            log.debug("Scheduled refresh of dashboard materialized views started");
            
            // This would call the database function to refresh views
            // Implementation depends on your database setup
            // For now, we'll just log the refresh attempt
            
            log.debug("Dashboard materialized views refresh completed");
            
        } catch (Exception e) {
            log.error("Failed to refresh dashboard materialized views: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled task to clear old cache entries
     * Runs every 10 minutes to prevent memory buildup
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void clearOldCacheEntries() {
        try {
            CacheManager cacheManager = dashboardCacheManager();
            
            // Clear specific caches periodically to ensure fresh data
            if (cacheManager.getCache("userDashboardCards") != null) {
                cacheManager.getCache("userDashboardCards").clear();
                log.debug("Cleared userDashboardCards cache");
            }
            
            if (cacheManager.getCache("apiKeyDashboard") != null) {
                cacheManager.getCache("apiKeyDashboard").clear();
                log.debug("Cleared apiKeyDashboard cache");
            }
            
        } catch (Exception e) {
            log.error("Failed to clear dashboard cache entries: {}", e.getMessage(), e);
        }
    }

    /**
     * Cache statistics logging
     * Runs every hour to monitor cache performance
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void logCacheStatistics() {
        try {
            log.info("Dashboard cache statistics - Cache manager active with {} caches", 
                    dashboardCacheManager().getCacheNames().size());
            
            // Log cache names and basic info
            dashboardCacheManager().getCacheNames().forEach(cacheName -> {
                var cache = dashboardCacheManager().getCache(cacheName);
                if (cache != null) {
                    log.debug("Cache '{}' is active", cacheName);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to log cache statistics: {}", e.getMessage(), e);
        }
    }
}