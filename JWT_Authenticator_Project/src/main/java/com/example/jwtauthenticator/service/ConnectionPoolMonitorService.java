package com.example.jwtauthenticator.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

/**
 * 📊 Connection Pool Monitor Service
 * 
 * Monitors HikariCP connection pool health and logs warnings
 * when pool utilization is high or connections are being waited for
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.monitoring.connection-pool.enabled", havingValue = "true", matchIfMissing = true)
public class ConnectionPoolMonitorService {

    private final DataSource dataSource;
    
    private static final double HIGH_UTILIZATION_THRESHOLD = 80.0;
    private static final double CRITICAL_UTILIZATION_THRESHOLD = 95.0;

    /**
     * 🔍 Monitor connection pool every 30 seconds
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void monitorConnectionPool() {
        try {
            if (!(dataSource instanceof HikariDataSource)) {
                return;
            }

            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();

            int activeConnections = poolBean.getActiveConnections();
            int idleConnections = poolBean.getIdleConnections();
            int totalConnections = poolBean.getTotalConnections();
            int threadsAwaitingConnection = poolBean.getThreadsAwaitingConnection();
            int maxPoolSize = hikariDataSource.getMaximumPoolSize();

            double utilization = maxPoolSize > 0 ? (double) totalConnections / maxPoolSize * 100 : 0;

            // Log detailed metrics every 5 minutes (10 * 30 seconds)
            if (System.currentTimeMillis() % 300000 < 30000) {
                log.info("📊 Connection Pool Status: active={}, idle={}, total={}/{}, utilization={:.1f}%, waiting={}",
                        activeConnections, idleConnections, totalConnections, maxPoolSize, utilization, threadsAwaitingConnection);
            }

            // Alert on high utilization
            if (utilization >= CRITICAL_UTILIZATION_THRESHOLD) {
                log.error("🚨 CRITICAL: Connection pool utilization is {:.1f}% (active={}, total={}/{})",
                        utilization, activeConnections, totalConnections, maxPoolSize);
            } else if (utilization >= HIGH_UTILIZATION_THRESHOLD) {
                log.warn("⚠️ WARNING: Connection pool utilization is {:.1f}% (active={}, total={}/{})",
                        utilization, activeConnections, totalConnections, maxPoolSize);
            }

            // Alert on waiting threads
            if (threadsAwaitingConnection > 0) {
                log.warn("⏳ WARNING: {} threads are waiting for database connections", threadsAwaitingConnection);
            }

            // Alert on no idle connections
            if (idleConnections == 0 && totalConnections == maxPoolSize) {
                log.warn("🔄 WARNING: No idle connections available, pool is at maximum capacity");
            }

        } catch (Exception e) {
            log.debug("Failed to monitor connection pool: {}", e.getMessage());
        }
    }

    /**
     * 📈 Log detailed connection pool statistics every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void logDetailedStatistics() {
        try {
            if (!(dataSource instanceof HikariDataSource)) {
                return;
            }

            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();

            log.info("📊 Detailed Connection Pool Statistics:");
            log.info("   Pool Name: {}", hikariDataSource.getPoolName());
            log.info("   Active Connections: {}", poolBean.getActiveConnections());
            log.info("   Idle Connections: {}", poolBean.getIdleConnections());
            log.info("   Total Connections: {}", poolBean.getTotalConnections());
            log.info("   Maximum Pool Size: {}", hikariDataSource.getMaximumPoolSize());
            log.info("   Minimum Idle: {}", hikariDataSource.getMinimumIdle());
            log.info("   Threads Awaiting Connection: {}", poolBean.getThreadsAwaitingConnection());
            log.info("   Connection Timeout: {}ms", hikariDataSource.getConnectionTimeout());
            log.info("   Idle Timeout: {}ms", hikariDataSource.getIdleTimeout());
            log.info("   Max Lifetime: {}ms", hikariDataSource.getMaxLifetime());

        } catch (Exception e) {
            log.debug("Failed to log detailed connection pool statistics: {}", e.getMessage());
        }
    }
}