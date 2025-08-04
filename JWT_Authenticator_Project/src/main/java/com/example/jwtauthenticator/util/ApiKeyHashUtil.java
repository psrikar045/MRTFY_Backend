package com.example.jwtauthenticator.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Utility class for API key hashing and generation operations.
 * Provides secure hashing using SHA-256 and cryptographically secure key generation.
 * 
 * SECURITY FEATURES:
 * - SHA-256 hashing for irreversible key storage
 * - 256-bit entropy for key generation
 * - Prefix-based key identification
 * - Format validation
 */
@Component
public class ApiKeyHashUtil {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
    
    // Key generation constants
    private static final int KEY_BYTE_LENGTH = 32; // 256 bits of entropy
    private static final String DEFAULT_PREFIX = "sk-"; // Secret key prefix
    private static final String ADMIN_PREFIX = "admin-"; // Admin key prefix
    private static final String BUSINESS_PREFIX = "biz-"; // Business key prefix

    /**
     * Generates a cryptographically secure API key with the specified prefix.
     * 
     * @param prefix The prefix for the API key (e.g., "sk-", "admin-", "biz-")
     * @return A secure API key string
     */
    public String generateSecureApiKey(String prefix) {
        byte[] randomBytes = new byte[KEY_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String keySuffix = base64Encoder.encodeToString(randomBytes);
        
        // Ensure prefix ends with hyphen for proper formatting
        String finalPrefix;
        if (prefix != null && !prefix.trim().isEmpty()) {
            finalPrefix = prefix.trim();
            if (!finalPrefix.endsWith("-")) {
                finalPrefix += "-";
            }
        } else {
            finalPrefix = DEFAULT_PREFIX;
        }
        
        return finalPrefix + keySuffix;
    }

    /**
     * Generates a standard secret key with default prefix.
     * 
     * @return A secure API key string with "sk-" prefix
     */
    public String generateSecretKey() {
        return generateSecureApiKey(DEFAULT_PREFIX);
    }

    /**
     * Generates an admin API key with admin prefix.
     * 
     * @return A secure API key string with "admin-" prefix
     */
    public String generateAdminKey() {
        return generateSecureApiKey(ADMIN_PREFIX);
    }

    /**
     * Generates a business API key with business prefix.
     * 
     * @return A secure API key string with "biz-" prefix
     */
    public String generateBusinessKey() {
        return generateSecureApiKey(BUSINESS_PREFIX);
    }

    /**
     * Hashes an API key using SHA-256.
     * 
     * @param apiKey The plain text API key to hash
     * @return The SHA-256 hash of the API key as a hexadecimal string
     * @throws RuntimeException if hashing fails
     */
    public String hashApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies if a plain text API key matches the stored hash.
     * Uses constant-time comparison to prevent timing attacks.
     * 
     * @param plainTextKey The plain text API key to verify
     * @param storedHash The stored hash to compare against
     * @return true if the key matches the hash, false otherwise
     */
    public boolean verifyApiKey(String plainTextKey, String storedHash) {
        if (plainTextKey == null || storedHash == null) {
            return false;
        }
        
        String computedHash = hashApiKey(plainTextKey);
        return constantTimeEquals(computedHash, storedHash);
    }

    /**
     * Validates API key format (supports custom prefixes).
     * 
     * @param apiKey The API key to validate
     * @return true if the format is valid, false otherwise
     */
    public boolean isValidApiKeyFormat(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        
        // Basic format validation: should have a prefix and sufficient length
        boolean hasPrefix = apiKey.contains("-");
        boolean sufficientLength = apiKey.length() >= 20; // Minimum security length
        
        // Allow any prefix that ends with a dash (more flexible for custom prefixes)
        boolean hasValidPrefixFormat = hasPrefix && apiKey.indexOf('-') > 0;
        
        return hasPrefix && sufficientLength && hasValidPrefixFormat;
    }

    /**
     * Extracts the prefix from an API key.
     * 
     * @param apiKey The API key to extract prefix from
     * @return The prefix (e.g., "sk-", "admin-", "biz-") or null if invalid
     */
    public String extractPrefix(String apiKey) {
        if (!isValidApiKeyFormat(apiKey)) {
            return null;
        }
        
        int dashIndex = apiKey.indexOf('-');
        return dashIndex > 0 ? apiKey.substring(0, dashIndex + 1) : null;
    }

    /**
     * Determines the key type based on prefix.
     * 
     * @param apiKey The API key to analyze
     * @return String describing the key type
     */
    public String getKeyType(String apiKey) {
        String prefix = extractPrefix(apiKey);
        if (prefix == null) {
            return "INVALID";
        }
        
        switch (prefix) {
            case "sk-":
                return "SECRET";
            case "admin-":
                return "ADMIN";
            case "biz-":
                return "BUSINESS";
            default:
                return "CUSTOM"; // Support for custom prefixes
        }
    }

    /**
     * Get all supported default prefixes
     */
    public List<String> getDefaultPrefixes() {
        return Arrays.asList(DEFAULT_PREFIX, ADMIN_PREFIX, BUSINESS_PREFIX);
    }

    /**
     * Check if a prefix is a default prefix
     */
    public boolean isDefaultPrefix(String prefix) {
        return DEFAULT_PREFIX.equals(prefix) || 
               ADMIN_PREFIX.equals(prefix) || 
               BUSINESS_PREFIX.equals(prefix);
    }

    /**
     * Converts byte array to hexadecimal string.
     * 
     * @param bytes The byte array to convert
     * @return Hexadecimal string representation
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * 
     * @param a First string to compare
     * @param b Second string to compare
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }

    /**
     * Generates a secure random salt for additional security (future use).
     * 
     * @return Base64-encoded salt string
     */
    public String generateSalt() {
        byte[] salt = new byte[16]; // 128 bits
        secureRandom.nextBytes(salt);
        return base64Encoder.encodeToString(salt);
    }
}