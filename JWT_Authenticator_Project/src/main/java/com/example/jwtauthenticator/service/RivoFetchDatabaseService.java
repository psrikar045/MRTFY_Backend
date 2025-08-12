package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.RivoFetchRequestLog;
import com.example.jwtauthenticator.repository.RivoFetchRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 🚀 RivoFetch Database Service - Dedicated Transactional Database Operations
 * 
 * This service handles all database operations for RivoFetch logging with proper
 * transaction management. It's separated from the async logging service to avoid
 * transaction propagation issues with @Async methods.
 * 
 * Features:
 * - Proper transaction management
 * - Separate from async operations
 * - ID collision detection
 * - Retry mechanisms
 * - Error handling and logging
 * 
 * @author BrandSnap API Team
 * @version 2.0
 * @since Java 21 - Phase 2 Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RivoFetchDatabaseService {
    
    private final RivoFetchRequestLogRepository rivoFetchRepository;
    
    /**
     * 💾 Save RivoFetch log entry with proper transaction management
     * 
     * @param logEntry Log entry to save
     * @return Saved log entry or null if failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RivoFetchRequestLog saveRivoFetchLog(RivoFetchRequestLog logEntry) {
        try {
            // Check for ID collision before saving
            if (rivoFetchRepository.existsById(logEntry.getRivoFetchLogId())) {
                log.error("🚨 ID COLLISION DETECTED: {} already exists in database!", 
                         logEntry.getRivoFetchLogId());
                return null;
            }
            
            // Save with flush to ensure immediate persistence
            RivoFetchRequestLog saved = rivoFetchRepository.saveAndFlush(logEntry);
            
            log.info("💾 ✅ Successfully saved RivoFetch log: {} | User: {} | API Key: {} | Success: {} | URL: {}", 
                     saved.getRivoFetchLogId(), 
                     saved.getRivoFetchUserId(),
                     saved.getRivoFetchApiKeyId(),
                     saved.getRivoFetchSuccess(),
                     saved.getRivoFetchTargetUrl());
            
            return saved;
            
        } catch (Exception e) {
            log.error("❌ 🚨 CRITICAL: Failed to save RivoFetch log {} to database: {}", 
                     logEntry.getRivoFetchLogId(), e.getMessage(), e);
            
            // Log detailed information for debugging
            log.error("❌ Failed log entry details: ID={}, User={}, ApiKey={}, URL={}, Success={}", 
                     logEntry.getRivoFetchLogId(),
                     logEntry.getRivoFetchUserId(),
                     logEntry.getRivoFetchApiKeyId(),
                     logEntry.getRivoFetchTargetUrl(),
                     logEntry.getRivoFetchSuccess());
            
            return null;
        }
    }
    
    /**
     * 🔄 Save with retry mechanism
     * 
     * @param logEntry Log entry to save
     * @param maxRetries Maximum number of retries
     * @return Saved log entry or null if all retries failed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RivoFetchRequestLog saveWithRetry(RivoFetchRequestLog logEntry, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Check for ID collision
                if (rivoFetchRepository.existsById(logEntry.getRivoFetchLogId())) {
                    log.warn("⚠️ ID collision on attempt {}: {}", attempt, logEntry.getRivoFetchLogId());
                    
                    if (attempt < maxRetries) {
                        // Generate new ID and retry
                        // Note: This would require ID regeneration logic
                        continue;
                    } else {
                        log.error("🚨 Max retries reached due to ID collisions: {}", logEntry.getRivoFetchLogId());
                        return null;
                    }
                }
                
                // Attempt to save
                RivoFetchRequestLog saved = rivoFetchRepository.saveAndFlush(logEntry);
                
                if (attempt > 1) {
                    log.warn("💾 ⚠️ Retry successful on attempt {}: {}", attempt, saved.getRivoFetchLogId());
                } else {
                    log.info("💾 ✅ Saved on first attempt: {}", saved.getRivoFetchLogId());
                }
                
                return saved;
                
            } catch (Exception e) {
                log.error("❌ Save attempt {} failed for {}: {}", attempt, logEntry.getRivoFetchLogId(), e.getMessage());
                
                if (attempt < maxRetries) {
                    try {
                        // Wait before retry
                        Thread.sleep(50 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("❌ 🚨 All {} save attempts failed for: {}", maxRetries, logEntry.getRivoFetchLogId());
                }
            }
        }
        
        return null;
    }
    
    /**
     * ✅ Check if ID exists in database
     * 
     * @param rivoFetchId ID to check
     * @return true if exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsById(String rivoFetchId) {
        try {
            return rivoFetchRepository.existsById(rivoFetchId);
        } catch (Exception e) {
            log.error("❌ Error checking if ID exists: {}", rivoFetchId, e);
            return false;
        }
    }
    
    /**
     * 📊 Get total count of records
     * 
     * @return Total count
     */
    @Transactional(readOnly = true)
    public long getTotalCount() {
        try {
            return rivoFetchRepository.count();
        } catch (Exception e) {
            log.error("❌ Error getting total count", e);
            return 0;
        }
    }
    
    /**
     * 🔍 Find latest records for debugging
     * 
     * @param limit Number of records to fetch
     * @return List of latest records
     */
    @Transactional(readOnly = true)
    public java.util.List<RivoFetchRequestLog> findLatestRecords(int limit) {
        try {
            return rivoFetchRepository.findAll(
                org.springframework.data.domain.PageRequest.of(0, limit,
                org.springframework.data.domain.Sort.by("rivoFetchTimestamp").descending())
            ).getContent();
        } catch (Exception e) {
            log.error("❌ Error fetching latest records", e);
            return java.util.Collections.emptyList();
        }
    }
}