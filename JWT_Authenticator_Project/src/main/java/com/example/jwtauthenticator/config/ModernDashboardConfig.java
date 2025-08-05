package com.example.jwtauthenticator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.time.Duration;

/**
 * Modern Dashboard Configuration using Java 21 features
 * Leverages Virtual Threads, modern caching, and performance optimizations
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class ModernDashboardConfig {

    /**
     * Virtual Thread Executor for Dashboard Operations (Java 21)
     * Provides massive scalability for I/O-bound dashboard operations
     */
    @Bean(name = "dashboardVirtualThreadExecutor")
    public Executor dashboardVirtualThreadExecutor() {
        log.info("🚀 Configuring Virtual Thread Executor for Dashboard (Java 21)");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Platform Thread Pool for CPU-intensive operations
     * Used for heavy computational tasks that benefit from platform threads
     */
    @Bean(name = "dashboardPlatformThreadExecutor")
    public Executor dashboardPlatformThreadExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        log.info("🔧 Configuring Platform Thread Pool with {} threads for CPU-intensive dashboard operations", processors);
        return Executors.newFixedThreadPool(processors);
    }

    // NOTE: Dashboard cache manager is now configured in CacheConfig.java
    // This avoids bean conflicts and centralizes cache configuration

    /**
     * Dashboard Performance Monitor using Java 21 features
     */
    @Bean
    public DashboardPerformanceMonitor dashboardPerformanceMonitor() {
        return new DashboardPerformanceMonitor();
    }

    /**
     * Modern Performance Monitor using Records and Pattern Matching
     */
    public static class DashboardPerformanceMonitor {
        
        /**
         * Performance metrics record (Java 21)
         */
        public record PerformanceMetrics(
            String operation,
            Duration executionTime,
            boolean success,
            String threadType,
            java.time.Instant timestamp
        ) {
            /**
             * Check if operation is slow
             */
            public boolean isSlow() {
                return executionTime.toMillis() > 1000; // > 1 second
            }
            
            /**
             * Get performance status using modern approach
             */
            public String getStatus() {
                if (!success) return "failed";
                
                long ms = executionTime.toMillis();
                if (ms < 100) return "excellent";
                if (ms < 500) return "good";
                if (ms < 1000) return "acceptable";
                return "slow";
            }
        }
        
        /**
         * Monitor dashboard operation performance
         */
        public PerformanceMetrics monitorOperation(String operation, Runnable task) {
            var start = System.nanoTime();
            var timestamp = java.time.Instant.now();
            var threadType = Thread.currentThread().isVirtual() ? "virtual" : "platform";
            boolean success = false;
            
            try {
                task.run();
                success = true;
            } catch (Exception e) {
                log.warn("Dashboard operation '{}' failed: {}", operation, e.getMessage());
            }
            
            var duration = Duration.ofNanos(System.nanoTime() - start);
            var metrics = new PerformanceMetrics(operation, duration, success, threadType, timestamp);
            
            // Log performance metrics
            if (metrics.isSlow()) {
                log.warn("🐌 Slow dashboard operation: {} took {}ms on {} thread", 
                    operation, duration.toMillis(), threadType);
            } else {
                log.debug("⚡ Dashboard operation: {} completed in {}ms on {} thread ({})", 
                    operation, duration.toMillis(), threadType, metrics.getStatus());
            }
            
            return metrics;
        }
    }

    /**
     * Modern Dashboard Properties using Records (Java 21)
     */
    public record DashboardProperties(
        Duration cacheTimeout,
        int maxConcurrentRequests,
        boolean enableVirtualThreads,
        boolean enablePerformanceMonitoring,
        Duration slowOperationThreshold
    ) {
        /**
         * Default dashboard properties
         */
        public static DashboardProperties defaults() {
            return new DashboardProperties(
                Duration.ofMinutes(5),      // Cache timeout
                1000,                       // Max concurrent requests
                true,                       // Enable Virtual Threads
                true,                       // Enable performance monitoring
                Duration.ofSeconds(1)       // Slow operation threshold
            );
        }
        
        /**
         * High-performance configuration
         */
        public static DashboardProperties highPerformance() {
            return new DashboardProperties(
                Duration.ofMinutes(10),     // Longer cache timeout
                5000,                       // Higher concurrent requests
                true,                       // Enable Virtual Threads
                false,                      // Disable monitoring for max performance
                Duration.ofMillis(500)      // Lower slow operation threshold
            );
        }
    }

    /**
     * Dashboard properties bean
     */
    @Bean
    public DashboardProperties dashboardProperties() {
        // Use high-performance configuration for production
        var properties = DashboardProperties.highPerformance();
        log.info("📊 Dashboard configured with: cache={}min, maxRequests={}, virtualThreads={}", 
            properties.cacheTimeout().toMinutes(), 
            properties.maxConcurrentRequests(), 
            properties.enableVirtualThreads());
        return properties;
    }
}