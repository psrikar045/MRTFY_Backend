package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.IdSequence;
import com.example.jwtauthenticator.repository.IdSequenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class IdGeneratorService {

    @Autowired
    private IdSequenceRepository idSequenceRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${brand.id.prefix:MRTFY}")
    private String defaultPrefix;

    @Value("${brand.id.number.padding:4}")
    private int numberPadding;
    
    @Value("${user.id.prefix:DOMBR}")
    private String userIdPrefix;
    
    @Value("${user.id.number.padding:6}")
    private int userIdPadding;

    /**
     * Generate next ID with default prefix from application.properties
     * @return Generated ID (e.g., MRTFY0001)
     */
    @Transactional
    public String generateNextId() {
        return generateNextId(defaultPrefix);
    }

    /**
     * Generate next ID with custom prefix
     * @param prefix Custom prefix (e.g., MKTY, MRTFY)
     * @return Generated ID (e.g., MKTY0001)
     */
    @Transactional
    public String generateNextId(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = defaultPrefix;
        }
        
        prefix = prefix.trim().toUpperCase();
        
        // Validate prefix length
        if (prefix.length() > 10) {
            throw new IllegalArgumentException("Prefix cannot be longer than 10 characters");
        }

        try {
            // Get or create sequence with pessimistic lock for thread safety
            IdSequence sequence = getOrCreateSequenceWithLock(prefix);
            
            // Increment the number
            sequence.setCurrentNumber(sequence.getCurrentNumber() + 1);
            
            // Save the updated sequence
            idSequenceRepository.save(sequence);
            
            // Format the ID
            String formattedId = formatId(prefix, sequence.getCurrentNumber());
            
            log.info("Generated ID: {} for prefix: {}", formattedId, prefix);
            
            return formattedId;
            
        } catch (Exception e) {
            log.error("Error generating ID for prefix: {}", prefix, e);
            throw new RuntimeException("Failed to generate ID for prefix: " + prefix, e);
        }
    }

    /**
     * Get current number for a prefix without incrementing
     * @param prefix The prefix to check
     * @return Current number (0 if prefix doesn't exist)
     */
    public Long getCurrentNumber(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = defaultPrefix;
        }
        
        prefix = prefix.trim().toUpperCase();
        
        Optional<IdSequence> sequence = idSequenceRepository.findByPrefix(prefix);
        return sequence.map(IdSequence::getCurrentNumber).orElse(0L);
    }

    /**
     * Preview next ID without generating it
     * @param prefix The prefix to preview
     * @return What the next ID would be
     */
    public String previewNextId(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = defaultPrefix;
        }
        
        prefix = prefix.trim().toUpperCase();
        
        Long currentNumber = getCurrentNumber(prefix);
        return formatId(prefix, currentNumber + 1);
    }

    /**
     * Reset sequence for a prefix (use with caution!)
     * @param prefix The prefix to reset
     * @param startNumber The number to start from (default: 0)
     */
    @Transactional
    public void resetSequence(String prefix, Long startNumber) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Prefix cannot be null or empty");
        }
        
        prefix = prefix.trim().toUpperCase();
        startNumber = startNumber != null ? startNumber : 0L;
        
        Optional<IdSequence> existingSequence = idSequenceRepository.findByPrefix(prefix);
        
        if (existingSequence.isPresent()) {
            IdSequence sequence = existingSequence.get();
            sequence.setCurrentNumber(startNumber);
            idSequenceRepository.save(sequence);
            log.warn("Reset sequence for prefix: {} to start from: {}", prefix, startNumber);
        } else {
            // Create new sequence
            IdSequence newSequence = IdSequence.builder()
                    .prefix(prefix)
                    .currentNumber(startNumber)
                    .build();
            idSequenceRepository.save(newSequence);
            log.info("Created new sequence for prefix: {} starting from: {}", prefix, startNumber);
        }
    }

    /**
     * Get or create sequence with pessimistic lock for thread safety
     */
    private IdSequence getOrCreateSequenceWithLock(String prefix) {
        // Try to get existing sequence with lock
        Optional<IdSequence> existingSequence = idSequenceRepository.findByPrefixWithLock(prefix);
        
        if (existingSequence.isPresent()) {
            return existingSequence.get();
        }
        
        // Create new sequence if it doesn't exist
        IdSequence newSequence = IdSequence.builder()
                .prefix(prefix)
                .currentNumber(0L)
                .build();
        
        return idSequenceRepository.save(newSequence);
    }

    /**
     * Format ID with prefix and padded number
     */
    private String formatId(String prefix, Long number) {
        String paddedNumber = String.format("%0" + numberPadding + "d", number);
        return prefix + paddedNumber;
    }

    /**
     * Get all available prefixes
     */
    public java.util.List<String> getAllPrefixes() {
        return idSequenceRepository.findAll()
                .stream()
                .map(IdSequence::getPrefix)
                .sorted()
                .toList();
    }

    /**
     * Check if a prefix exists
     */
    public boolean prefixExists(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return false;
        }
        return idSequenceRepository.existsByPrefix(prefix.trim().toUpperCase());
    }
    
    /**
     * Generate a unique user ID with DOMBR prefix and 6-digit sequential number
     * Uses the PostgreSQL sequence for guaranteed sequential IDs
     * @return Generated user ID (e.g., DOMBR000001)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateDombrUserId() {
        try {
            // Use the PostgreSQL function to generate a sequential ID
            String userId = jdbcTemplate.queryForObject(
                "SELECT generate_dombr_id()", String.class);
            
            log.info("Generated sequential DOMBR user ID: {}", userId);
            return userId;
        } catch (Exception e) {
            log.error("Error using generate_dombr_id() function: {}", e.getMessage());
            
            // Fallback: Direct sequence access
            try {
                Long nextVal = jdbcTemplate.queryForObject(
                    "SELECT nextval('dombr_user_id_seq')", Long.class);
                
                String userId = String.format("DOMBR%06d", nextVal);
                log.info("Generated DOMBR user ID using direct sequence: {}", userId);
                return userId;
            } catch (Exception e2) {
                log.error("Error using direct sequence access: {}", e2.getMessage());
                
                // Last resort: Use the simple sequential approach
                log.warn("Falling back to simple ID generation method");
                return generateSimpleDombrUserId();
            }
        }
    }
    
    /**
     * Initialize the DOMBR sequence and function
     * This method should be called once to set up the database properly
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initializeDombrSequence() {
        try {
            // Drop the sequence if it exists
            jdbcTemplate.execute("DROP SEQUENCE IF EXISTS dombr_user_id_seq");
            
            // Create a clean sequence starting from 1
            jdbcTemplate.execute(
                "CREATE SEQUENCE dombr_user_id_seq " +
                "START WITH 1 " +
                "INCREMENT BY 1 " +
                "NO MINVALUE " +
                "MAXVALUE 999999 " +
                "CACHE 1"
            );
            
            // Drop the function if it exists
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS generate_dombr_id()");
            
            // Create the function to generate sequential DOMBR IDs
            jdbcTemplate.execute(
                "CREATE OR REPLACE FUNCTION generate_dombr_id() " +
                "RETURNS VARCHAR AS $$ " +
                "DECLARE " +
                "    next_val INTEGER; " +
                "    formatted_id VARCHAR; " +
                "BEGIN " +
                "    SELECT nextval('dombr_user_id_seq') INTO next_val; " +
                "    formatted_id := 'DOMBR' || LPAD(next_val::TEXT, 6, '0'); " +
                "    RETURN formatted_id; " +
                "END; " +
                "$$ LANGUAGE plpgsql"
            );
            
            // Test the function
            String testId = jdbcTemplate.queryForObject("SELECT generate_dombr_id()", String.class);
            log.info("DOMBR sequence and function initialized successfully. Test ID: {}", testId);
        } catch (Exception e) {
            log.error("Failed to initialize DOMBR sequence: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Generate a simple DOMBR user ID using an in-memory counter
     * This method can be used directly if database sequences are causing issues
     * @return Generated user ID (e.g., DOMBR000001)
     */
    private static final AtomicLong simpleCounter = new AtomicLong(1);
    
    public String generateSimpleDombrUserId() {
        // Get the next value from the atomic counter
        long nextVal = simpleCounter.getAndIncrement();
        
        // Ensure we don't exceed the maximum value
        if (nextVal > 999999) {
            log.warn("Simple counter exceeded maximum value, resetting to 1");
            simpleCounter.set(1);
            nextVal = 1;
        }
        
        String userId = String.format("DOMBR%06d", nextVal);
        log.info("Generated simple sequential DOMBR user ID: {}", userId);
        return userId;
    }
    
    /**
     * Fallback method to generate DOMBR user IDs
     * @return Generated user ID (e.g., DOMBR000001)
     */
    private String generateFallbackDombrUserId() {
        // Use the simple sequential approach
        return generateSimpleDombrUserId();
    }
    
    /**
     * Preview the next DOMBR user ID without generating it
     * @return What the next DOMBR user ID would be
     */
    public String previewNextDombrUserId() {
        Long currentNumber = getCurrentNumber(userIdPrefix);
        String paddedNumber = String.format("%0" + userIdPadding + "d", currentNumber + 1);
        return userIdPrefix + paddedNumber;
    }
}