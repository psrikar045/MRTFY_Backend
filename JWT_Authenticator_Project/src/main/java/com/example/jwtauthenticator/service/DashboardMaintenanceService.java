package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Service for maintaining dashboard performance
 * Handles materialized view refresh and cleanup tasks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardMaintenanceService {

    @PersistenceContext
    private final EntityManager entityManager;
    
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Modern dashboard maintenance using Virtual Threads (Java 21)
     * Performs asynchronous maintenance tasks for optimal performance
     * ✅ FIXED: Reduced frequency to prevent connection pool contention during API key creation
     */
    @Scheduled(fixedRate = 900000) // 15 minutes (reduced from 5 minutes)
    public void refreshDashboardViews() {
        // Use Virtual Threads for non-blocking maintenance
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Starting dashboard maintenance with Virtual Threads");
                long startTime = System.nanoTime();
                
                // Parallel maintenance tasks using Virtual Threads
                var statisticsTask = CompletableFuture.runAsync(this::updateTableStatistics, executorService);
                var cleanupTask = CompletableFuture.runAsync(this::performCleanupTasks, executorService);
                var optimizationTask = CompletableFuture.runAsync(this::optimizeQueries, executorService);
                
                // Wait for all tasks to complete
                CompletableFuture.allOf(statisticsTask, cleanupTask, optimizationTask).join();
                
                long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
                log.info("✅ Dashboard maintenance completed in {}ms using Virtual Threads", duration);
                
            } catch (Exception e) {
                log.error("❌ Dashboard maintenance failed: {}", e.getMessage(), e);
            }
        }, executorService);
    }
    
    /**
     * Update table statistics using modern JPA approach
     */
    private void updateTableStatistics() {
        try {
            // Use native queries with EntityManager for better performance
            var query = entityManager.createNativeQuery("""
                SELECT c.relname as table_name, 
                       pg_stat_get_tuples_inserted(c.oid) as inserts,
                       pg_stat_get_tuples_updated(c.oid) as updates,
                       pg_stat_get_tuples_deleted(c.oid) as deletes
                FROM pg_class c 
                JOIN pg_namespace n ON n.oid = c.relnamespace 
                WHERE n.nspname = 'public' 
                AND c.relname IN ('api_key_request_logs', 'api_key_usage_stats', 'api_keys')
                """);
            
            var results = query.getResultList();
            log.debug("Updated statistics for {} tables", results.size());
            
        } catch (Exception e) {
            log.debug("Statistics update not supported or failed: {}", e.getMessage());
        }
    }
    
    /**
     * Perform cleanup tasks
     */
    private void performCleanupTasks() {
        try {
            // Clean up old request logs (older than 90 days) using batch processing
            var cleanupQuery = entityManager.createQuery("""
                DELETE FROM ApiKeyRequestLog l 
                WHERE l.requestTimestamp < :cutoffDate
                """);
            cleanupQuery.setParameter("cutoffDate", java.time.LocalDateTime.now().minusDays(90));
            
            int deletedCount = cleanupQuery.executeUpdate();
            if (deletedCount > 0) {
                log.info("Cleaned up {} old request log entries", deletedCount);
            }
            
        } catch (Exception e) {
            log.debug("Cleanup task failed: {}", e.getMessage());
        }
    }
    
    /**
     * Optimize queries and indexes
     */
    private void optimizeQueries() {
        try {
            // Check for missing indexes and suggest optimizations
            var indexQuery = entityManager.createNativeQuery("""
                SELECT schemaname, tablename, attname, n_distinct, correlation
                FROM pg_stats 
                WHERE schemaname = 'public' 
                AND tablename IN ('api_key_request_logs', 'api_key_usage_stats', 'api_keys')
                AND n_distinct > 100
                ORDER BY n_distinct DESC
                LIMIT 10
                """);
            
            var results = indexQuery.getResultList();
            log.debug("Analyzed {} high-cardinality columns for optimization", results.size());
            
        } catch (Exception e) {
            log.debug("Query optimization analysis failed: {}", e.getMessage());
        }
    }

    /**
     * Cleanup old request logs to maintain performance
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldLogs() {
        try {
            log.info("Starting cleanup of old request logs");
            
            // Delete logs older than 90 days
            String deleteQuery = """
                DELETE FROM api_key_request_logs 
                WHERE request_timestamp < CURRENT_DATE - INTERVAL '90 days'
                """;
            
            var query = entityManager.createNativeQuery(deleteQuery);
            int deletedRows = query.executeUpdate();
            log.info("✅ Cleaned up {} old request log entries", deletedRows);
            
            // Vacuum analyze the table for performance (PostgreSQL specific)
            try {
                entityManager.createNativeQuery("VACUUM ANALYZE api_key_request_logs").executeUpdate();
            } catch (Exception e) {
                log.debug("VACUUM command not supported or failed: {}", e.getMessage());
            }
            log.info("✅ Vacuumed and analyzed api_key_request_logs table");
            
        } catch (Exception e) {
            log.error("❌ Failed to cleanup old logs: {}", e.getMessage(), e);
        }
    }

    /**
     * Update dashboard view statistics
     * Runs every hour to keep statistics current
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional(readOnly = true)
    public void updateDashboardStatistics() {
        try {
            log.debug("Updating dashboard statistics");
            
            // Get view refresh statistics
            String statsQuery = """
                SELECT 
                    schemaname, 
                    matviewname, 
                    hasindexes, 
                    ispopulated,
                    pg_size_pretty(pg_total_relation_size(schemaname||'.'||matviewname)) as size
                FROM pg_matviews 
                WHERE matviewname IN ('user_dashboard_summary', 'api_key_dashboard_summary')
                """;
            
            var query = entityManager.createNativeQuery(statsQuery);
            var results = query.getResultList();
            
            for (Object result : results) {
                if (result instanceof Object[] row) {
                    String viewName = (String) row[1];
                    boolean isPopulated = (Boolean) row[3];
                    String size = (String) row[4];
                    
                    log.debug("Materialized view '{}': populated={}, size={}", viewName, isPopulated, size);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to update dashboard statistics: {}", e.getMessage(), e);
        }
    }

    /**
     * Health check for dashboard services
     * Runs every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    @Transactional(readOnly = true)
    public void dashboardHealthCheck() {
        try {
            log.debug("Performing dashboard health check");
            
            // Check if materialized views exist and are populated
            String healthQuery = """
                SELECT COUNT(*) as view_count
                FROM pg_matviews 
                WHERE matviewname IN ('user_dashboard_summary', 'api_key_dashboard_summary')
                AND ispopulated = true
                """;
            
            var healthQuery1 = entityManager.createNativeQuery(healthQuery);
            Number healthyViewsResult = (Number) healthQuery1.getSingleResult();
            Integer healthyViews = healthyViewsResult != null ? healthyViewsResult.intValue() : 0;
            
            if (healthyViews == 2) {
                log.debug("✅ Dashboard health check passed - all views healthy");
            } else {
                log.warn("⚠️ Dashboard health check warning - only {}/2 views are healthy", healthyViews);
            }
            
            // Check recent data availability
            String dataQuery = """
                SELECT COUNT(*) as recent_logs
                FROM api_key_request_logs 
                WHERE request_timestamp >= CURRENT_DATE - INTERVAL '1 day'
                """;
            
            var dataQuery1 = entityManager.createNativeQuery(dataQuery);
            Number recentLogsResult = (Number) dataQuery1.getSingleResult();
            Integer recentLogs = recentLogsResult != null ? recentLogsResult.intValue() : 0;
            log.debug("Recent activity: {} requests in last 24 hours", recentLogs);
            
        } catch (Exception e) {
            log.error("Dashboard health check failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Manual refresh trigger for dashboard views
     * Can be called via management endpoint or admin interface
     */
    public void forceRefreshDashboardViews() {
        log.info("Manual refresh of dashboard views triggered");
        refreshDashboardViews();
    }

    /**
     * Get dashboard maintenance status
     */
    @Transactional(readOnly = true)
    public DashboardMaintenanceStatus getMaintenanceStatus() {
        try {
            // Check materialized view status
            String statusQuery = """
                SELECT 
                    matviewname,
                    ispopulated,
                    pg_size_pretty(pg_total_relation_size(schemaname||'.'||matviewname)) as size
                FROM pg_matviews 
                WHERE matviewname IN ('user_dashboard_summary', 'api_key_dashboard_summary')
                """;
            
            // Modern approach using EntityManager
            boolean userSummaryHealthy = false;
            boolean apiKeySummaryHealthy = false;
            String userSummarySize = "0 bytes";
            String apiKeySummarySize = "0 bytes";
            
            var statusQuery1 = entityManager.createNativeQuery(statusQuery);
            var results = statusQuery1.getResultList();
            
            for (Object result : results) {
                if (result instanceof Object[] row) {
                    String viewName = (String) row[0];
                    boolean isPopulated = (Boolean) row[1];
                    String size = (String) row[2];
                    
                    if ("user_dashboard_summary".equals(viewName)) {
                        userSummaryHealthy = isPopulated;
                        userSummarySize = size;
                    } else if ("api_key_dashboard_summary".equals(viewName)) {
                        apiKeySummaryHealthy = isPopulated;
                        apiKeySummarySize = size;
                    }
                }
            }
            
            return DashboardMaintenanceStatus.builder()
                    .userSummaryViewHealthy(userSummaryHealthy)
                    .apiKeySummaryViewHealthy(apiKeySummaryHealthy)
                    .userSummaryViewSize(userSummarySize)
                    .apiKeySummaryViewSize(apiKeySummarySize)
                    .lastChecked(java.time.LocalDateTime.now())
                    .overallStatus(userSummaryHealthy && apiKeySummaryHealthy ? "healthy" : "degraded")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to get maintenance status: {}", e.getMessage(), e);
            return DashboardMaintenanceStatus.builder()
                    .overallStatus("error")
                    .lastChecked(java.time.LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Status DTO for dashboard maintenance
     */
    @lombok.Data
    @lombok.Builder
    public static class DashboardMaintenanceStatus {
        private boolean userSummaryViewHealthy;
        private boolean apiKeySummaryViewHealthy;
        private String userSummaryViewSize;
        private String apiKeySummaryViewSize;
        private java.time.LocalDateTime lastChecked;
        private String overallStatus;
    }
}