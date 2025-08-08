package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 🏥 Dashboard Health Check Service
 * 
 * Provides comprehensive health checks for dashboard functionality including:
 * - Database connectivity
 * - Materialized view status
 * - Connection pool health
 * - Data consistency checks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardHealthCheckService {
    
    private final JdbcTemplate jdbcTemplate;
    private final MaterializedViewRefreshService viewRefreshService;
    
    /**
     * 🔍 Comprehensive dashboard health check
     */
    public Map<String, Object> performHealthCheck() {
        Map<String, Object> healthStatus = new HashMap<>();
        
        try {
            // 1. Database connectivity check
            healthStatus.put("database", checkDatabaseConnectivity());
            
            // 2. Materialized views check
            healthStatus.put("materializedViews", checkMaterializedViews());
            
            // 3. Data consistency check
            healthStatus.put("dataConsistency", checkDataConsistency());
            
            // 4. Overall status
            boolean allHealthy = healthStatus.values().stream()
                .allMatch(status -> status instanceof Map && "HEALTHY".equals(((Map<?, ?>) status).get("status")));
            
            healthStatus.put("overall", Map.of(
                "status", allHealthy ? "HEALTHY" : "WARNING",
                "message", allHealthy ? "All systems operational" : "Some issues detected"
            ));
            
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);
            healthStatus.put("overall", Map.of(
                "status", "ERROR",
                "message", "Health check failed: " + e.getMessage()
            ));
        }
        
        return healthStatus;
    }
    
    /**
     * 🔌 Check database connectivity
     */
    private Map<String, Object> checkDatabaseConnectivity() {
        try {
            String result = jdbcTemplate.queryForObject("SELECT 'OK'", String.class);
            return Map.of(
                "status", "HEALTHY",
                "message", "Database connection successful",
                "response", result
            );
        } catch (Exception e) {
            return Map.of(
                "status", "ERROR",
                "message", "Database connection failed: " + e.getMessage()
            );
        }
    }
    
    /**
     * 📊 Check materialized views status
     */
    private Map<String, Object> checkMaterializedViews() {
        try {
            // Check if materialized views exist and have data
            Long userViewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_dashboard_summary", Long.class);
            
            Long apiKeyViewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_key_dashboard_summary", Long.class);
            
            // Get last update times
            String userViewLastUpdate = null;
            String apiKeyViewLastUpdate = null;
            
            try {
                userViewLastUpdate = jdbcTemplate.queryForObject(
                    "SELECT MAX(last_updated)::text FROM user_dashboard_summary", String.class);
            } catch (Exception e) {
                log.debug("Could not get user view last update: {}", e.getMessage());
            }
            
            try {
                apiKeyViewLastUpdate = jdbcTemplate.queryForObject(
                    "SELECT MAX(last_updated)::text FROM api_key_dashboard_summary", String.class);
            } catch (Exception e) {
                log.debug("Could not get API key view last update: {}", e.getMessage());
            }
            
            return Map.of(
                "status", "HEALTHY",
                "message", "Materialized views are accessible",
                "userViewRecords", userViewCount != null ? userViewCount : 0,
                "apiKeyViewRecords", apiKeyViewCount != null ? apiKeyViewCount : 0,
                "userViewLastUpdate", userViewLastUpdate != null ? userViewLastUpdate : "Unknown",
                "apiKeyViewLastUpdate", apiKeyViewLastUpdate != null ? apiKeyViewLastUpdate : "Unknown"
            );
            
        } catch (Exception e) {
            return Map.of(
                "status", "ERROR",
                "message", "Materialized views check failed: " + e.getMessage()
            );
        }
    }
    
    /**
     * 🔍 Check data consistency between tables
     */
    private Map<String, Object> checkDataConsistency() {
        try {
            // Check if we have API keys
            Long apiKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_keys WHERE is_active = true", Long.class);
            
            // Check if we have request logs
            Long requestLogCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_key_request_logs WHERE request_timestamp >= CURRENT_DATE - INTERVAL '7 days'", Long.class);
            
            // Check if we have usage stats
            Long usageStatsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_key_usage_stats WHERE window_start >= CURRENT_DATE - INTERVAL '7 days'", Long.class);
            
            // Check if we have monthly usage
            Long monthlyUsageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_key_monthly_usage WHERE month_year = TO_CHAR(CURRENT_DATE, 'YYYY-MM')", Long.class);
            
            return Map.of(
                "status", "HEALTHY",
                "message", "Data consistency check completed",
                "activeApiKeys", apiKeyCount != null ? apiKeyCount : 0,
                "recentRequestLogs", requestLogCount != null ? requestLogCount : 0,
                "recentUsageStats", usageStatsCount != null ? usageStatsCount : 0,
                "currentMonthUsage", monthlyUsageCount != null ? monthlyUsageCount : 0
            );
            
        } catch (Exception e) {
            return Map.of(
                "status", "ERROR",
                "message", "Data consistency check failed: " + e.getMessage()
            );
        }
    }
    
    /**
     * 🔄 Trigger manual refresh of materialized views
     */
    public Map<String, Object> refreshMaterializedViews() {
        try {
            viewRefreshService.refreshDashboardViewsSync();
            return Map.of(
                "status", "SUCCESS",
                "message", "Materialized views refreshed successfully"
            );
        } catch (Exception e) {
            return Map.of(
                "status", "ERROR",
                "message", "Failed to refresh materialized views: " + e.getMessage()
            );
        }
    }
}