package com.example.jwtauthenticator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executors;

/**
 * Dashboard Scheduler Configuration
 * Configures the thread pool for scheduled tasks
 */
@Configuration
@ConditionalOnProperty(name = "dashboard.scheduler.enabled", havingValue = "true")
@Slf4j
public class DashboardSchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // Use a dedicated thread pool for dashboard scheduling
        taskRegistrar.setScheduler(Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "dashboard-scheduler-");
            thread.setDaemon(true);
            return thread;
        }));
        
        log.info("Dashboard scheduler thread pool configured");
    }
}