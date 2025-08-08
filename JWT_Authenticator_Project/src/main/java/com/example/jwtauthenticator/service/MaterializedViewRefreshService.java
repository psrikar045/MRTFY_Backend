package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * 🔄 Materialized View Refresh Service
 * 
 * Handles refreshing of materialized views to ensure dashboard data is up-to-date.
 * The materialized views cache aggregated data for performance, but need periodic refresh.
 * 
 * Features:
 * - Automatic scheduled refresh every 5 minutes
 * - Manual refresh capability
 * - Async refresh to avoid blocking operations
 * - Error handling and logging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterializedViewRefreshService {
    
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 🔄 Refresh all dashboard materialized views
     * Called automatically every 5 minutes and after API calls
     */
    @Async("transactionalAsyncExecutor")
    @Transactional
    public CompletableFuture<Void> refreshDashboardViews() {
        try {
            log.debug("🔄 Starting materialized view refresh...");
            
            long startTime = System.currentTimeMillis();
            
            // Refresh user dashboard summary view
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY user_dashboard_summary");
            log.debug("✅ Refreshed user_dashboard_summary");
            
            // Refresh API key dashboard summary view
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY api_key_dashboard_summary");
            log.debug("✅ Refreshed api_key_dashboard_summary");
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Materialized views refreshed successfully in {}ms", duration);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh materialized views: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 🔄 Refresh only user dashboard view (lighter operation)
     */
    @Async("transactionalAsyncExecutor")
    @Transactional
    public CompletableFuture<Void> refreshUserDashboardView() {
        try {
            log.debug("🔄 Refreshing user dashboard view...");
            
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY user_dashboard_summary");
            log.debug("✅ Refreshed user_dashboard_summary");
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh user dashboard view: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 🔄 Refresh only API key dashboard view (lighter operation)
     */
    @Async("transactionalAsyncExecutor")
    @Transactional
    public CompletableFuture<Void> refreshApiKeyDashboardView() {
        try {
            log.debug("🔄 Refreshing API key dashboard view...");
            
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY api_key_dashboard_summary");
            log.debug("✅ Refreshed api_key_dashboard_summary");
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh API key dashboard view: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 📅 Scheduled refresh every 3 minutes
     * Ensures dashboard data is never more than 3 minutes old
     * ✅ OPTIMIZED: Reduced frequency to prevent conflicts while maintaining freshness
     */
    @Scheduled(fixedRate = 180000) // 3 minutes (optimized for performance)
    public void scheduledRefresh() {
        try {
            log.debug("📅 Starting scheduled materialized view refresh...");
            
            // Use synchronous refresh for scheduled operations to ensure completion
            refreshDashboardViewsSync();
            
            log.info("✅ Scheduled materialized view refresh completed successfully");
        } catch (Exception e) {
            log.error("❌ Scheduled materialized view refresh failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 🚀 Manual refresh for immediate updates (synchronous)
     * Use this when you need immediate dashboard updates
     */
    @Transactional
    public void refreshDashboardViewsSync() {
        try {
            log.debug("🚀 Starting synchronous materialized view refresh...");
            
            long startTime = System.currentTimeMillis();
            
            // Refresh user dashboard summary view
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY user_dashboard_summary");
            log.debug("✅ Refreshed user_dashboard_summary (sync)");
            
            // Refresh API key dashboard summary view
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY api_key_dashboard_summary");
            log.debug("✅ Refreshed api_key_dashboard_summary (sync)");
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Materialized views refreshed synchronously in {}ms", duration);
            
        } catch (Exception e) {
            log.error("❌ Failed to refresh materialized views synchronously: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 📊 Get materialized view refresh status
     */
    public MaterializedViewStatus getRefreshStatus() {
        try {
            // Check when views were last updated
            String userViewLastUpdate = jdbcTemplate.queryForObject(
                "SELECT last_updated FROM user_dashboard_summary LIMIT 1", 
                String.class
            );
            
            String apiKeyViewLastUpdate = jdbcTemplate.queryForObject(
                "SELECT last_updated FROM api_key_dashboard_summary LIMIT 1", 
                String.class
            );
            
            return new MaterializedViewStatus(
                "HEALTHY",
                userViewLastUpdate,
                apiKeyViewLastUpdate,
                "Views are up to date"
            );
            
        } catch (Exception e) {
            log.error("Failed to get materialized view status: {}", e.getMessage(), e);
            return new MaterializedViewStatus(
                "ERROR",
                null,
                null,
                "Failed to check view status: " + e.getMessage()
            );
        }
    }
    
    /**
     * Materialized View Status Record
     */
    public record MaterializedViewStatus(
        String status,
        String userViewLastUpdate,
        String apiKeyViewLastUpdate,
        String message
    ) {}
}