package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 🚀 RivoFetch ID Generator Service - RIVO9XXXXXX ID Generation
 * 
 * This service generates unique RIVO9XXXXXX format IDs for RivoFetch request logs.
 * Uses database sequence for persistence and includes fallback mechanisms.
 * 
 * Features:
 * - RIVO9XXXXXX format (RIVO9 + 6-digit sequence)
 * - Database sequence persistence
 * - In-memory cache for performance
 * - Fallback mechanisms for high availability
 * - Thread-safe operations
 * - Sequence validation and recovery
 * 
 * @author BrandSnap API Team
 * @version 2.0
 * @since Java 21 - Phase 2 Implementation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RivoFetchIdGeneratorService {
    
    private final JdbcTemplate jdbcTemplate;
    
    // Constants
    private static final String ID_PREFIX = "RIVO9";
    private static final int SEQUENCE_LENGTH = 6;
    private static final long MAX_SEQUENCE_VALUE = 999999L;
    private static final String SEQUENCE_NAME = "rivo_fetch_id_sequence";
    
    // In-memory cache for performance (fallback mechanism)
    private final AtomicLong fallbackSequence = new AtomicLong(1);
    private final ConcurrentHashMap<String, Long> sequenceCache = new ConcurrentHashMap<>();
    
    /**
     * 🚀 Initialize sequence on service startup
     */
    @jakarta.annotation.PostConstruct
    public void initializeSequence() {
        try {
            log.info("🚀 Initializing RivoFetch ID sequence...");
            
            // Step 1: Ensure sequence exists
            ensureSequenceExists();
            
            // Step 2: Fix sequence value based on existing records
            fixSequenceValue();
            
            log.info("✅ RivoFetch ID sequence initialized successfully");
        } catch (Exception e) {
            log.error("❌ Failed to initialize RivoFetch ID sequence: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 🔧 Fix sequence value based on existing records (SIMPLE)
     */
    @Transactional
    public void fixSequenceValue() {
        try {
            // Find the highest sequence number from existing RIVO9 records
            // PostgreSQL: SUBSTRING(string, start, length) - extract 6 digits after "RIVO9"
            String findMaxSql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(rivo_fetch_log_id, 6, 6) AS INTEGER)), 0)
                FROM rivo_fetch_request_logs 
                WHERE rivo_fetch_log_id ~ '^RIVO9[0-9]{6}$'
                """;
            
            Integer maxSequence = jdbcTemplate.queryForObject(findMaxSql, Integer.class);
            long nextValue = (maxSequence != null ? maxSequence : 0) + 1;
            
            log.info("🔧 Found max sequence: {}, setting next value to: {}", maxSequence, nextValue);
            
            // Set the sequence to the correct next value
            String setSeqSql = "SELECT setval(?, ?, false)"; // false = next call returns this value
            jdbcTemplate.queryForObject(setSeqSql, Long.class, SEQUENCE_NAME, nextValue);
            
            // Verify it worked
            String checkSql = "SELECT last_value FROM " + SEQUENCE_NAME;
            Long currentValue = jdbcTemplate.queryForObject(checkSql, Long.class);
            
            log.info("✅ Sequence fixed - Next value will be: {}, Current last_value: {}", nextValue, currentValue);
            
        } catch (Exception e) {
            log.error("❌ Failed to fix sequence value: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 🎯 Generate next RivoFetch ID in RIVO9XXXXXX format
     * 
     * @return Generated ID in RIVO9XXXXXX format
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateRivoFetchId() {
        try {
            // Try to get next value from database sequence
            Long sequenceValue = getNextSequenceValue();
            
            if (sequenceValue != null) {
                String generatedId = formatRivoFetchId(sequenceValue);
                log.debug("🎯 Generated RivoFetch ID from database sequence: {}", generatedId);
                return generatedId;
            }
            
            // Fallback to in-memory sequence if database fails
            return generateFallbackId();
            
        } catch (Exception e) {
            log.warn("⚠️ Failed to generate RivoFetch ID from database, using fallback: {}", e.getMessage());
            return generateFallbackId();
        }
    }
    
    /**
     * 🔢 Get next sequence value from database
     */
    private Long getNextSequenceValue() {
        try {
            String sql = "SELECT nextval(?)";
            Long value = jdbcTemplate.queryForObject(sql, Long.class, SEQUENCE_NAME);
            
            if (value != null && value > MAX_SEQUENCE_VALUE) {
                log.warn("⚠️ Sequence value {} exceeds maximum {}, sequence will cycle", value, MAX_SEQUENCE_VALUE);
            }
            
            return value;
            
        } catch (DataAccessException e) {
            log.error("❌ Failed to get next sequence value from database: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 🔄 Generate fallback ID using in-memory sequence
     */
    private String generateFallbackId() {
        long fallbackValue = fallbackSequence.getAndIncrement();
        
        // Ensure fallback doesn't exceed maximum
        if (fallbackValue > MAX_SEQUENCE_VALUE) {
            fallbackSequence.set(1); // Reset to 1
            fallbackValue = 1;
            log.info("🔄 Fallback sequence reset to 1 after reaching maximum");
        }
        
        String fallbackId = formatRivoFetchId(fallbackValue);
        log.debug("🔄 Generated fallback RivoFetch ID: {}", fallbackId);
        return fallbackId;
    }
    
    /**
     * 📝 Format sequence value into RIVO9XXXXXX format
     */
    private String formatRivoFetchId(long sequenceValue) {
        // Ensure sequence value is within bounds
        long normalizedValue = sequenceValue % (MAX_SEQUENCE_VALUE + 1);
        if (normalizedValue == 0) {
            normalizedValue = MAX_SEQUENCE_VALUE; // Use max value instead of 0
        }
        
        // Format with leading zeros
        String paddedSequence = String.format("%0" + SEQUENCE_LENGTH + "d", normalizedValue);
        return ID_PREFIX + paddedSequence;
    }
    
    /**
     * ✅ Validate RivoFetch ID format
     */
    public boolean isValidRivoFetchId(String id) {
        if (id == null || id.length() != (ID_PREFIX.length() + SEQUENCE_LENGTH)) {
            return false;
        }
        
        if (!id.startsWith(ID_PREFIX)) {
            return false;
        }
        
        String sequencePart = id.substring(ID_PREFIX.length());
        try {
            long sequenceValue = Long.parseLong(sequencePart);
            return sequenceValue >= 1 && sequenceValue <= MAX_SEQUENCE_VALUE;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 🔍 Extract sequence number from RivoFetch ID
     */
    public Long extractSequenceNumber(String rivoFetchId) {
        if (!isValidRivoFetchId(rivoFetchId)) {
            return null;
        }
        
        try {
            String sequencePart = rivoFetchId.substring(ID_PREFIX.length());
            return Long.parseLong(sequencePart);
        } catch (NumberFormatException e) {
            log.warn("⚠️ Failed to extract sequence number from ID: {}", rivoFetchId);
            return null;
        }
    }
    
    /**
     * 📊 Get current sequence information
     */
    public SequenceInfo getCurrentSequenceInfo() {
        try {
            // Get current sequence value without incrementing
            String sql = "SELECT last_value, is_called FROM " + SEQUENCE_NAME;
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                long lastValue = rs.getLong("last_value");
                boolean isCalled = rs.getBoolean("is_called");
                
                // If sequence hasn't been called yet, current value is the start value
                long currentValue = isCalled ? lastValue : 1;
                
                return new SequenceInfo(
                    currentValue,
                    MAX_SEQUENCE_VALUE,
                    MAX_SEQUENCE_VALUE - currentValue,
                    ((double) currentValue / MAX_SEQUENCE_VALUE) * 100.0
                );
            });
            
        } catch (DataAccessException e) {
            log.error("❌ Failed to get sequence info: {}", e.getMessage());
            
            // Return fallback info
            long fallbackValue = fallbackSequence.get();
            return new SequenceInfo(
                fallbackValue,
                MAX_SEQUENCE_VALUE,
                MAX_SEQUENCE_VALUE - fallbackValue,
                ((double) fallbackValue / MAX_SEQUENCE_VALUE) * 100.0
            );
        }
    }
    
    /**
     * 🔧 Reset sequence to specific value (admin operation)
     */
    @Transactional
    public boolean resetSequence(long newValue) {
        if (newValue < 1 || newValue > MAX_SEQUENCE_VALUE) {
            log.warn("⚠️ Invalid sequence reset value: {}. Must be between 1 and {}", newValue, MAX_SEQUENCE_VALUE);
            return false;
        }
        
        try {
            String sql = "SELECT setval(?, ?, true)";
            // jdbcTemplate.update(sql, SEQUENCE_NAME, newValue);
            jdbcTemplate.queryForObject(sql, Long.class, SEQUENCE_NAME, newValue);
            log.info("✅ Successfully reset RivoFetch sequence to: {}", newValue);
            return true;
            
        } catch (DataAccessException e) {
            log.error("❌ Failed to reset sequence to {}: {}", newValue, e.getMessage());
            return false;
        }
    }
    
    /**
     * 🔍 Check if sequence exists and create if needed
     */
    @Transactional
    public boolean ensureSequenceExists() {
        try {
            // Check if sequence exists
            String checkSql = """
                SELECT COUNT(*) FROM information_schema.sequences 
                WHERE sequence_name = ? AND sequence_schema = current_schema()
                """;
            
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, SEQUENCE_NAME);
            
            if (count != null && count > 0) {
                log.debug("✅ RivoFetch sequence already exists");
                return true;
            }
            
            // Create sequence if it doesn't exist
            String createSql = String.format("""
                CREATE SEQUENCE %s 
                START WITH 1 
                INCREMENT BY 1 
                MINVALUE 1 
                MAXVALUE %d 
                CYCLE
                """, SEQUENCE_NAME, MAX_SEQUENCE_VALUE);
            
            jdbcTemplate.execute(createSql);
            log.info("✅ Created RivoFetch sequence: {}", SEQUENCE_NAME);
            return true;
            
        } catch (DataAccessException e) {
            log.error("❌ Failed to ensure sequence exists: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 🧪 Generate test IDs for validation
     */
    public String[] generateTestIds(int count) {
        String[] testIds = new String[count];
        for (int i = 0; i < count; i++) {
            testIds[i] = generateRivoFetchId();
        }
        return testIds;
    }
    
    /**
     * 📊 Sequence information record
     */
    public record SequenceInfo(
        long currentValue,
        long maxValue,
        long remainingValues,
        double usagePercentage
    ) {
        public boolean isNearingLimit() {
            return usagePercentage > 90.0;
        }
        
        public boolean isAtLimit() {
            return currentValue >= maxValue;
        }
        
        @Override
        public String toString() {
            return String.format(
                "SequenceInfo{current=%d, max=%d, remaining=%d, usage=%.2f%%}",
                currentValue, maxValue, remainingValues, usagePercentage
            );
        }
    }
    
    /**
     * 🎯 Batch generate multiple IDs (for high-throughput scenarios)
     */
    public String[] generateBatchIds(int batchSize) {
        if (batchSize <= 0 || batchSize > 1000) {
            throw new IllegalArgumentException("Batch size must be between 1 and 1000");
        }
        
        String[] ids = new String[batchSize];
        for (int i = 0; i < batchSize; i++) {
            ids[i] = generateRivoFetchId();
        }
        
        log.debug("🎯 Generated batch of {} RivoFetch IDs", batchSize);
        return ids;
    }
    
    /**
     * 🔄 Initialize sequence from existing records
     */
    @Transactional
    public void initializeSequenceFromExistingRecords() {
        try {
            // First, let's debug what records exist
            debugExistingRecords();
            
            long nextValue = getNextStartValueFromExistingRecords();
            
            // Get current sequence value before setting
            Long currentSeqValue = getCurrentSequenceValue();
            log.info("🔍 Current sequence value BEFORE setting: {}", currentSeqValue);
            
            // Set sequence to the correct next value
            // Using setval with 'false' means the NEXT call to nextval() will return this value
            String sql = "SELECT setval(?, ?, false)";
jdbcTemplate.queryForObject(sql, Long.class, SEQUENCE_NAME, nextValue);            
            // Verify the sequence was set correctly
            Long newSeqValue = getCurrentSequenceValue();
            log.info("🔄 Initialized RivoFetch sequence - Previous: {}, Set to: {}, Current: {}", 
                    currentSeqValue, nextValue, newSeqValue);
            
        } catch (DataAccessException e) {
            log.error("❌ Failed to initialize sequence from existing records: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 🔍 Debug existing records to understand the data
     */
    private void debugExistingRecords() {
        try {
            String sql = """
                SELECT rivo_fetch_log_id, SUBSTRING(rivo_fetch_log_id, 6) as sequence_part
                FROM rivo_fetch_request_logs 
                WHERE rivo_fetch_log_id LIKE 'RIVO9%' 
                ORDER BY rivo_fetch_log_id
                """;
            
            List<Map<String, Object>> records = jdbcTemplate.queryForList(sql);
            log.info("🔍 DEBUG: Found {} existing RIVO9 records:", records.size());
            
            for (Map<String, Object> record : records) {
                log.info("🔍 DEBUG: ID: {}, Sequence Part: {}", 
                        record.get("rivo_fetch_log_id"), record.get("sequence_part"));
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to debug existing records: {}", e.getMessage());
        }
    }
    
    /**
     * 🔍 Get current sequence value
     */
    private Long getCurrentSequenceValue() {
        try {
            String sql = "SELECT last_value FROM " + SEQUENCE_NAME;
            return jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            log.error("❌ Failed to get current sequence value: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 🔍 Get next start value based on existing records
     */
    private long getNextStartValueFromExistingRecords() {
        try {
            // Find the highest sequence number from existing records
            // RIVO9XXXXXX format: extract the 6-digit number after "RIVO9"
            // "RIVO9" = 5 chars, so we need to start from position 6 (1-based indexing)
            String sql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(rivo_fetch_log_id, 6) AS INTEGER)), 0) + 1
                FROM rivo_fetch_request_logs 
                WHERE rivo_fetch_log_id LIKE 'RIVO9%' 
                AND LENGTH(rivo_fetch_log_id) = 11
                AND SUBSTRING(rivo_fetch_log_id, 6) ~ '^[0-9]+$'
                """;
            
            log.info("🔍 Executing SQL to find max sequence: {}", sql);
            
            Long maxSequence = jdbcTemplate.queryForObject(sql, Long.class);
            long nextValue = (maxSequence != null) ? maxSequence : 1;
            
            log.info("🔍 Raw max sequence from DB: {}, calculated next value: {}", maxSequence, nextValue);
            
            // Ensure it's within bounds
            if (nextValue > MAX_SEQUENCE_VALUE) {
                nextValue = 1; // Cycle back to 1
                log.warn("🔄 Next value {} exceeds max {}, cycling back to 1", nextValue, MAX_SEQUENCE_VALUE);
            }
            
            log.info("🔍 Final next sequence value: {}", nextValue);
            return nextValue;
            
        } catch (DataAccessException e) {
            log.error("❌ Failed to get next start value from existing records: {}", e.getMessage(), e);
            return 1; // Default to 1 if query fails
        }
    }
}