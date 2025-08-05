package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dashboard Scheduler Service
 * Handles automatic refresh of dashboard materialized views
 * 
 * Configuration:
 * - dashboard.scheduler.enabled=true (to enable scheduling)
 * - dashboard.scheduler.fixed-rate=3600000 (1 hour in milliseconds)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dashboard.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class DashboardSchedulerService {

    private final JdbcTemplate jdbcTemplate;
    
    @Value("${dashboard.scheduler.fixed-rate:3600000}") // Default: 1 hour
    private long refreshInterval;
    
    /**
     * Scheduled method to refresh dashboard views
     * Runs at fixed rate defined in application properties
     */
    @Scheduled(fixedRateString = "${dashboard.scheduler.fixed-rate:3600000}")
    public void refreshDashboardViews() {
        log.info("Starting dashboard views refresh at {}", 
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        try {
            // Execute the dashboard refresh function
            String result = jdbcTemplate.queryForObject(
                "SELECT refresh_all_dashboard_views()", 
                String.class
            );
            
            log.info("Dashboard refresh completed successfully: {}", result);
            
            // Optional: Get dashboard stats
            String stats = jdbcTemplate.queryForObject(
                "SELECT get_dashboard_stats()", 
                String.class
            );
            
            log.info("Dashboard stats: {}", stats);
            
        } catch (Exception e) {
            log.error("Dashboard refresh failed: {}", e.getMessage(), e);
            
            // Optional: Send alert notification here
            // alertService.sendDashboardRefreshAlert(e.getMessage());
        }
    }
    
    /**
     * Manual refresh method (can be called via REST endpoint)
     */
    public String manualRefresh() {
        log.info("Manual dashboard refresh triggered");
        
        try {
            String result = jdbcTemplate.queryForObject(
                "SELECT refresh_all_dashboard_views()", 
                String.class
            );
            
            log.info("Manual dashboard refresh completed: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("Manual dashboard refresh failed: {}", e.getMessage(), e);
            throw new RuntimeException("Dashboard refresh failed: " + e.getMessage());
        }
    }
    
    /**
     * Get current dashboard statistics
     */
    public String getDashboardStats() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT get_dashboard_stats()", 
                String.class
            );
        } catch (Exception e) {
            log.error("Failed to get dashboard stats: {}", e.getMessage(), e);
            return "Error retrieving dashboard stats: " + e.getMessage();
        }
    }
}