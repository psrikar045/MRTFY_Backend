package com.example.jwtauthenticator.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to monitor connection pool health and prevent timeout issues
 * ✅ ADDED: Connection pool monitoring to prevent API key creation timeouts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectionPoolHealthService implements HealthIndicator {

    private final DataSource dataSource;

    /**
     * Monitor connection pool every 5 minutes during debugging
     * ✅ DEBUGGING: Reduced frequency to avoid log noise during debugging
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void monitorConnectionPool() {
        try {
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
                
                int activeConnections = poolBean.getActiveConnections();
                int idleConnections = poolBean.getIdleConnections();
                int totalConnections = poolBean.getTotalConnections();
                int threadsAwaitingConnection = poolBean.getThreadsAwaitingConnection();
                
                // Calculate utilization percentage
                double utilization = (double) activeConnections / totalConnections * 100;
                
                // Log warning if pool is under stress
                if (utilization > 80 || threadsAwaitingConnection > 10) {
                    log.warn("🚨 Connection Pool Under Stress: active={}, idle={}, total={}, waiting={}, utilization={:.1f}%", 
                            activeConnections, idleConnections, totalConnections, threadsAwaitingConnection, utilization);
                } else if (utilization > 60) {
                    log.info("⚠️ Connection Pool High Usage: active={}, idle={}, total={}, waiting={}, utilization={:.1f}%", 
                            activeConnections, idleConnections, totalConnections, threadsAwaitingConnection, utilization);
                } else {
                    log.debug("✅ Connection Pool Healthy: active={}, idle={}, total={}, waiting={}, utilization={:.1f}%", 
                            activeConnections, idleConnections, totalConnections, threadsAwaitingConnection, utilization);
                }
            }
        } catch (Exception e) {
            log.error("Failed to monitor connection pool: {}", e.getMessage());
        }
    }

    /**
     * Health check for Spring Boot Actuator
     */
    @Override
    public Health health() {
        try {
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
                
                int activeConnections = poolBean.getActiveConnections();
                int totalConnections = poolBean.getTotalConnections();
                int threadsAwaitingConnection = poolBean.getThreadsAwaitingConnection();
                
                double utilization = (double) activeConnections / totalConnections * 100;
                
                Map<String, Object> details = new HashMap<>();
                details.put("activeConnections", activeConnections);
                details.put("idleConnections", poolBean.getIdleConnections());
                details.put("totalConnections", totalConnections);
                details.put("threadsAwaitingConnection", threadsAwaitingConnection);
                details.put("utilizationPercent", Math.round(utilization * 10.0) / 10.0);
                
                // Determine health status
                if (utilization > 90 || threadsAwaitingConnection > 20) {
                    return Health.down()
                            .withDetails(details)
                            .withDetail("status", "Connection pool critically overloaded")
                            .build();
                } else if (utilization > 80 || threadsAwaitingConnection > 10) {
                    return Health.status("WARN")
                            .withDetails(details)
                            .withDetail("status", "Connection pool under stress")
                            .build();
                } else {
                    return Health.up()
                            .withDetails(details)
                            .withDetail("status", "Connection pool healthy")
                            .build();
                }
            }
            
            return Health.unknown().withDetail("status", "Unable to determine connection pool status").build();
            
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "Connection pool health check failed")
                    .build();
        }
    }

    /**
     * Get current connection pool statistics
     */
    public Map<String, Object> getConnectionPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
                
                stats.put("activeConnections", poolBean.getActiveConnections());
                stats.put("idleConnections", poolBean.getIdleConnections());
                stats.put("totalConnections", poolBean.getTotalConnections());
                stats.put("threadsAwaitingConnection", poolBean.getThreadsAwaitingConnection());
                stats.put("maximumPoolSize", hikariDataSource.getMaximumPoolSize());
                stats.put("minimumIdle", hikariDataSource.getMinimumIdle());
                
                double utilization = (double) poolBean.getActiveConnections() / poolBean.getTotalConnections() * 100;
                stats.put("utilizationPercent", Math.round(utilization * 10.0) / 10.0);
                
                // Add health status
                if (utilization > 90) {
                    stats.put("healthStatus", "CRITICAL");
                } else if (utilization > 80) {
                    stats.put("healthStatus", "WARNING");
                } else if (utilization > 60) {
                    stats.put("healthStatus", "MODERATE");
                } else {
                    stats.put("healthStatus", "HEALTHY");
                }
            }
        } catch (Exception e) {
            stats.put("error", e.getMessage());
            stats.put("healthStatus", "ERROR");
        }
        
        return stats;
    }
}