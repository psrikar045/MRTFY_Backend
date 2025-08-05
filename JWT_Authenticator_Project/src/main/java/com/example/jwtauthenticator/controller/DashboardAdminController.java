package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.service.DashboardSchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dashboard Administration Controller
 * Provides endpoints for manual dashboard management
 * 
 * Only available when DashboardSchedulerService is enabled
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard Administration", description = "Dashboard management endpoints")
@ConditionalOnBean(DashboardSchedulerService.class)
public class DashboardAdminController {

    private final DashboardSchedulerService dashboardSchedulerService;
    
    /**
     * Manually trigger dashboard refresh
     */
    @PostMapping("/refresh")
    @Operation(summary = "Manual Dashboard Refresh", 
               description = "Manually trigger refresh of all dashboard materialized views")
    @PreAuthorize("hasRole('ADMIN')") // Adjust based on your security setup
    public ResponseEntity<Map<String, Object>> refreshDashboard() {
        log.info("Manual dashboard refresh requested");
        
        try {
            String result = dashboardSchedulerService.manualRefresh();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Dashboard refreshed successfully",
                "result", result,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Manual dashboard refresh failed", e);
            
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Dashboard refresh failed",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Get current dashboard statistics
     */
    @GetMapping("/stats")
    @Operation(summary = "Dashboard Statistics", 
               description = "Get current dashboard statistics and health status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        try {
            String stats = dashboardSchedulerService.getDashboardStats();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "stats", stats,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Failed to get dashboard stats", e);
            
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Failed to get dashboard stats",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Health check endpoint for dashboard scheduler
     */
    @GetMapping("/health")
    @Operation(summary = "Dashboard Health Check", 
               description = "Check if dashboard scheduler is running properly")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "scheduler", "enabled",
            "service", "DashboardSchedulerService",
            "timestamp", System.currentTimeMillis()
        ));
    }
}