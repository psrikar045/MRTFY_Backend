package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.service.ConnectionPoolHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for system health monitoring
 * ✅ ADDED: System health endpoints to monitor connection pool and prevent timeouts
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "System Health", description = "System monitoring and health check endpoints")
public class SystemHealthController {

    private final ConnectionPoolHealthService connectionPoolHealthService;

    /**
     * Get connection pool statistics
     * Useful for monitoring database connection health
     */
    @GetMapping("/connection-pool/stats")
    @Operation(
            summary = "Get Connection Pool Statistics",
            description = "Returns current connection pool statistics including active connections, utilization, and health status",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getConnectionPoolStats() {
        try {
            Map<String, Object> stats = connectionPoolHealthService.getConnectionPoolStats();
            log.debug("Connection pool stats requested: {}", stats);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get connection pool stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Failed to retrieve connection pool statistics",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Simple health check endpoint
     */
    @GetMapping("/health")
    @Operation(
            summary = "System Health Check",
            description = "Basic system health check endpoint"
    )
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", java.time.Instant.now().toString(),
                "service", "JWT Authenticator API"
        ));
    }
}