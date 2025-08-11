package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.scheduler.MonthlyQuotaResetScheduler;
import com.example.jwtauthenticator.scheduler.MonthlyQuotaResetScheduler.QuotaResetResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin endpoints for quota management
 * Provides manual control over monthly quota reset operations
 * 
 * Security: Requires ADMIN role for access to all endpoints
 * 
 * @author BrandSnap API Team
 * @version 1.0
 * @since Java 21
 */
@RestController
@RequestMapping("/api/admin/quota")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Quota Management", description = "Administrative quota reset operations")
@SecurityRequirement(name = "bearerAuth")
public class AdminQuotaController {
    
    private final MonthlyQuotaResetScheduler resetScheduler;
    private final ApiKeyMonthlyUsageRepository usageRepository;
    
    /**
     * Manually trigger monthly quota reset
     * Use with caution - this will reset ALL API key quotas immediately
     */
    @PostMapping("/reset/manual")
    @Operation(
        summary = "Manually trigger monthly quota reset", 
        description = "Triggers immediate quota reset for all API keys. Use with caution as this affects all users.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Reset completed successfully",
                content = @Content(schema = @Schema(implementation = QuotaResetResult.class))),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required"),
            @ApiResponse(responseCode = "500", description = "Internal server error during reset")
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QuotaResetResult> triggerManualReset() {
        log.warn("🔧 Manual quota reset triggered by admin user");
        
        try {
            QuotaResetResult result = resetScheduler.performManualQuotaReset();
            
            if (result.hasFailures()) {
                log.warn("⚠️ Manual reset completed with {} failures out of {} total records", 
                        result.getFailureCount(), result.getTotalProcessed());
            } else {
                log.info("✅ Manual reset completed successfully - {} records processed", result.getSuccessCount());
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("❌ Manual reset failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get quota reset status and statistics
     */
    @GetMapping("/reset/status")
    @Operation(
        summary = "Get quota reset status", 
        description = "Returns information about quota reset requirements and statistics",
        responses = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getResetStatus() {
        try {
            LocalDate currentDate = LocalDate.now();
            LocalDate resetDate = currentDate.withDayOfMonth(1);
            String currentMonthYear = currentDate.getYear() + "-" + String.format("%02d", currentDate.getMonthValue());
            
            // Get statistics about records needing reset
            long recordsNeedingReset = usageRepository.countRecordsNeedingReset(resetDate);
            long totalRecords = usageRepository.count();
            
            Map<String, Object> status = new HashMap<>();
            status.put("currentDate", currentDate);
            status.put("currentMonth", currentMonthYear);
            status.put("nextResetDate", resetDate.plusMonths(1));
            status.put("totalUsageRecords", totalRecords);
            status.put("recordsNeedingReset", recordsNeedingReset);
            status.put("recordsUpToDate", totalRecords - recordsNeedingReset);
            status.put("resetCompletionPercentage", 
                    totalRecords > 0 ? ((totalRecords - recordsNeedingReset) * 100.0) / totalRecords : 100.0);
            
            // Add configuration information
            status.put("scheduledResetEnabled", true); // This controller only exists if scheduler is enabled
            status.put("resetTime", "00:01 UTC on 1st of each month");
            
            log.debug("📊 Reset status requested: {} records need reset out of {} total", 
                    recordsNeedingReset, totalRecords);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("❌ Failed to get reset status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get detailed quota statistics for monitoring
     */
    @GetMapping("/statistics")
    @Operation(
        summary = "Get detailed quota statistics", 
        description = "Returns comprehensive quota usage statistics for monitoring and analytics",
        responses = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required")
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getQuotaStatistics() {
        try {
            String currentMonthYear = LocalDate.now().getYear() + "-" + 
                    String.format("%02d", LocalDate.now().getMonthValue());
            
            // Get current month statistics
            var quotaExceeded = usageRepository.findQuotaExceededKeys(currentMonthYear);
            var approachingQuota = usageRepository.findKeysApproachingQuota(currentMonthYear);
            
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("currentMonth", currentMonthYear);
            statistics.put("keysExceedingQuota", quotaExceeded.size());
            statistics.put("keysApproachingQuota", approachingQuota.size());
            
            // Calculate aggregate statistics
            long totalActiveKeys = usageRepository.count();
            statistics.put("totalActiveKeys", totalActiveKeys);
            
            if (!quotaExceeded.isEmpty()) {
                statistics.put("quotaExceededDetails", quotaExceeded.stream()
                    .map(usage -> Map.of(
                        "apiKeyId", usage.getApiKeyId(),
                        "userId", usage.getUserId(),
                        "totalCalls", usage.getTotalCalls(),
                        "quotaLimit", usage.getQuotaLimit(),
                        "usagePercentage", usage.getQuotaUsagePercentage()
                    ))
                    .toList());
            }
            
            log.debug("📈 Quota statistics requested for month {}: {} exceeded, {} approaching", 
                    currentMonthYear, quotaExceeded.size(), approachingQuota.size());
            
            return ResponseEntity.ok(statistics);
            
        } catch (Exception e) {
            log.error("❌ Failed to get quota statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Health check endpoint for the quota reset system
     */
    @GetMapping("/health")
    @Operation(
        summary = "Health check for quota reset system", 
        description = "Returns health status of the quota reset system components",
        responses = {
            @ApiResponse(responseCode = "200", description = "System is healthy"),
            @ApiResponse(responseCode = "403", description = "Access denied - Admin role required"),
            @ApiResponse(responseCode = "503", description = "System has issues")
        }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("timestamp", java.time.LocalDateTime.now());
            
            // Check database connectivity
            try {
                long recordCount = usageRepository.count();
                health.put("database", "UP");
                health.put("totalRecords", recordCount);
            } catch (Exception e) {
                health.put("database", "DOWN");
                health.put("databaseError", e.getMessage());
                return ResponseEntity.status(503).body(health);
            }
            
            // Check scheduler configuration
            health.put("scheduler", "UP");
            health.put("schedulerClass", resetScheduler.getClass().getSimpleName());
            
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            log.error("❌ Health check failed: {}", e.getMessage(), e);
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(503).body(health);
        }
    }
}