package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for cleaning up expired and revoked API keys.
 * Runs scheduled tasks to maintain database hygiene.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyCleanupService {
    
    private final ApiKeyRepository apiKeyRepository;
    
    /**
     * Scheduled task to clean up expired API keys.
     * Runs daily at 2:00 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredApiKeys() {
        log.info("Starting cleanup of expired API keys");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            List<ApiKey> allKeys = apiKeyRepository.findAll();
            
            int expiredCount = 0;
            int revokedCount = 0;
            
            for (ApiKey key : allKeys) {
                boolean shouldDeactivate = false;
                String reason = "";
                
                // Check if key is expired
                if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(now)) {
                    shouldDeactivate = true;
                    reason = "expired";
                    expiredCount++;
                }
                
                // Check if key was revoked more than 30 days ago (optional cleanup)
                if (key.getRevokedAt() != null && 
                    key.getRevokedAt().isBefore(now.minusDays(30))) {
                    shouldDeactivate = true;
                    reason = "revoked over 30 days ago";
                    revokedCount++;
                }
                
                if (shouldDeactivate && key.isActive()) {
                    key.setActive(false);
                    key.setUpdatedAt(now);
                    apiKeyRepository.save(key);
                    
                    log.debug("Deactivated API key '{}' (ID: {}) - {}", 
                             key.getName(), key.getId(), reason);
                }
            }
            
            log.info("API key cleanup completed. Expired: {}, Long-revoked: {}", 
                    expiredCount, revokedCount);
            
        } catch (Exception e) {
            log.error("Error during API key cleanup", e);
        }
    }
    
    /**
     * Scheduled task to log API key usage statistics.
     * Runs weekly on Sunday at 1:00 AM.
     */
    @Scheduled(cron = "0 0 1 ? * SUN")
    @Transactional(readOnly = true)
    public void logApiKeyStatistics() {
        log.info("Generating API key usage statistics");
        
        try {
            List<ApiKey> allKeys = apiKeyRepository.findAll();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneWeekAgo = now.minusDays(7);
            LocalDateTime oneMonthAgo = now.minusDays(30);
            
            long totalKeys = allKeys.size();
            long activeKeys = allKeys.stream().mapToLong(key -> key.isActive() ? 1 : 0).sum();
            long expiredKeys = allKeys.stream()
                .mapToLong(key -> 
                    key.getExpiresAt() != null && key.getExpiresAt().isBefore(now) ? 1 : 0)
                .sum();
            long revokedKeys = allKeys.stream()
                .mapToLong(key -> key.getRevokedAt() != null ? 1 : 0)
                .sum();
            long usedLastWeek = allKeys.stream()
                .mapToLong(key -> 
                    key.getLastUsedAt() != null && key.getLastUsedAt().isAfter(oneWeekAgo) ? 1 : 0)
                .sum();
            long usedLastMonth = allKeys.stream()
                .mapToLong(key -> 
                    key.getLastUsedAt() != null && key.getLastUsedAt().isAfter(oneMonthAgo) ? 1 : 0)
                .sum();
            
            log.info("API Key Statistics - Total: {}, Active: {}, Expired: {}, Revoked: {}, " +
                    "Used Last Week: {}, Used Last Month: {}", 
                    totalKeys, activeKeys, expiredKeys, revokedKeys, usedLastWeek, usedLastMonth);
            
        } catch (Exception e) {
            log.error("Error generating API key statistics", e);
        }
    }
    
    /**
     * Manual cleanup method for administrative use.
     * @return Number of keys cleaned up
     */
    @Transactional
    public int performManualCleanup() {
        log.info("Performing manual API key cleanup");
        
        LocalDateTime now = LocalDateTime.now();
        List<ApiKey> allKeys = apiKeyRepository.findAll();
        int cleanedCount = 0;
        
        for (ApiKey key : allKeys) {
            if ((key.getExpiresAt() != null && key.getExpiresAt().isBefore(now)) ||
                (key.getRevokedAt() != null && key.getRevokedAt().isBefore(now.minusDays(30)))) {
                
                if (key.isActive()) {
                    key.setActive(false);
                    key.setUpdatedAt(now);
                    apiKeyRepository.save(key);
                    cleanedCount++;
                }
            }
        }
        
        log.info("Manual cleanup completed. Cleaned up {} API keys", cleanedCount);
        return cleanedCount;
    }
    
    /**
     * Get cleanup statistics without performing cleanup.
     * @return Statistics about keys that would be cleaned up
     */
    @Transactional(readOnly = true)
    public CleanupStatistics getCleanupStatistics() {
        LocalDateTime now = LocalDateTime.now();
        List<ApiKey> allKeys = apiKeyRepository.findAll();
        
        long expiredActive = allKeys.stream()
            .mapToLong(key -> 
                key.isActive() && key.getExpiresAt() != null && key.getExpiresAt().isBefore(now) ? 1 : 0)
            .sum();
        
        long longRevokedActive = allKeys.stream()
            .mapToLong(key -> 
                key.isActive() && key.getRevokedAt() != null && key.getRevokedAt().isBefore(now.minusDays(30)) ? 1 : 0)
            .sum();
        
        return new CleanupStatistics(expiredActive, longRevokedActive);
    }
    
    /**
     * Statistics class for cleanup information.
     */
    public static class CleanupStatistics {
        private final long expiredActiveKeys;
        private final long longRevokedActiveKeys;
        
        public CleanupStatistics(long expiredActiveKeys, long longRevokedActiveKeys) {
            this.expiredActiveKeys = expiredActiveKeys;
            this.longRevokedActiveKeys = longRevokedActiveKeys;
        }
        
        public long getExpiredActiveKeys() {
            return expiredActiveKeys;
        }
        
        public long getLongRevokedActiveKeys() {
            return longRevokedActiveKeys;
        }
        
        public long getTotalKeysToCleanup() {
            return expiredActiveKeys + longRevokedActiveKeys;
        }
    }
}