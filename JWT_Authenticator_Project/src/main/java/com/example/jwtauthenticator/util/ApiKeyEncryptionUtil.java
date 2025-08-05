package com.example.jwtauthenticator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * High-Performance API Key Encryption Utility with Strong Security
 * 
 * SECURITY FEATURES:
 * - AES-256-GCM encryption (authenticated encryption)
 * - SHA-256 key derivation (fast, secure, frontend-compatible)
 * - Random IV/Nonce for each encryption (prevents rainbow table attacks)
 * - Authentication tag prevents tampering
 * - Memory clearing to prevent key leakage
 * - UserId-based encryption for user isolation
 * 
 * ENCRYPTION FORMAT: {VERSION}:{SALT}:{IV}:{ENCRYPTED_DATA}:{AUTH_TAG}
 * All components are Base64 encoded for safe storage and frontend compatibility
 */
@Component
@Slf4j
public class ApiKeyEncryptionUtil {
    
    // Encryption Constants - High Performance Security
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION_ALGORITHM = "SHA-256";
    
    // Security Parameters
    private static final int AES_KEY_LENGTH = 256; // 256-bit AES key
    private static final int GCM_IV_LENGTH = 12;   // 96-bit IV (recommended for GCM)
    private static final int GCM_TAG_LENGTH = 16;  // 128-bit authentication tag
    private static final int SALT_LENGTH = 32;     // 256-bit salt
    
    // Application-specific constants (change these for your deployment)
    private static final String APPLICATION_PEPPER = "MRTFY_API_KEY_ENCRYPTION_2025"; // Additional security layer
    private static final String ENCRYPTION_VERSION = "v2"; // Frontend-compatible version
    
    private final SecureRandom secureRandom;
    
    public ApiKeyEncryptionUtil() {
        this.secureRandom = new SecureRandom();
        // Pre-seed the random number generator for better entropy
        this.secureRandom.nextBytes(new byte[32]);
    }
    
    /**
     * Encrypts an API key using user ID as the basis for key derivation
     * 
     * SECURITY FLOW:
     * 1. Generate random salt (prevents rainbow table attacks)
     * 2. Derive AES-256 key using userId + salt + pepper + SHA-256
     * 3. Generate random IV for GCM mode
     * 4. Encrypt using AES-256-GCM (provides authentication)
     * 5. Extract authentication tag
     * 6. Encode all components as Base64
     * 7. Return formatted string for storage
     * 
     * @param plainApiKey The plain text API key to encrypt
     * @param userId The user ID used for key derivation (ensures user isolation)
     * @return Base64 encoded encrypted string with metadata
     * @throws RuntimeException if encryption fails
     */
    public String encryptApiKey(String plainApiKey, String userId) {
        if (plainApiKey == null || plainApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        
        byte[] salt = null;
        byte[] iv = null;
        SecretKey secretKey = null;
        
        try {
            // Step 1: Generate random salt
            salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);
            
            // Step 2: Derive encryption key from userId + salt + pepper
            secretKey = deriveKeyFromUserId(userId, salt);
            
            // Step 3: Generate random IV
            iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Step 4: Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            // Step 5: Encrypt the API key
            byte[] encryptedData = cipher.doFinal(plainApiKey.getBytes(StandardCharsets.UTF_8));
            
            // Step 6: Extract authentication tag (last 16 bytes)
            byte[] cipherText = Arrays.copyOfRange(encryptedData, 0, encryptedData.length - GCM_TAG_LENGTH);
            byte[] authTag = Arrays.copyOfRange(encryptedData, encryptedData.length - GCM_TAG_LENGTH, encryptedData.length);
            
            // Step 7: Encode all components and combine
            String encodedSalt = Base64.getEncoder().encodeToString(salt);
            String encodedIv = Base64.getEncoder().encodeToString(iv);
            String encodedCipherText = Base64.getEncoder().encodeToString(cipherText);
            String encodedAuthTag = Base64.getEncoder().encodeToString(authTag);
            
            String result = String.join(":", ENCRYPTION_VERSION, encodedSalt, encodedIv, encodedCipherText, encodedAuthTag);
            
            log.debug("Successfully encrypted API key for user: {} (salt length: {}, iv length: {})", 
                     userId, salt.length, iv.length);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to encrypt API key for user: {}", userId, e);
            throw new RuntimeException("API key encryption failed", e);
        } finally {
            // Security: Clear sensitive data from memory
            clearSensitiveData(salt, iv);
            if (secretKey != null) {
                clearSecretKey(secretKey);
            }
        }
    }
    
    /**
     * Parse encrypted API key components for frontend decryption
     * 
     * This method extracts the encryption components (salt, iv, encrypted data, auth tag)
     * so that Angular frontend can perform the decryption using the same algorithm.
     * 
     * Frontend will use:
     * 1. Same key derivation: SHA-256(userId + pepper + salt)
     * 2. Same AES-256-GCM decryption
     * 3. Same authentication tag verification
     * 
     * @param encryptedApiKey The encrypted API key string
     * @return EncryptionComponents object with all parts needed for frontend decryption
     */
    public EncryptionComponents parseForFrontend(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted API key cannot be null or empty");
        }
        
        try {
            // Parse encrypted string components
            String[] parts = encryptedApiKey.split(":");
            if (parts.length != 5) {
                throw new IllegalArgumentException("Invalid encrypted API key format");
            }
            
            String version = parts[0];
            String encodedSalt = parts[1];
            String encodedIv = parts[2];
            String encodedCipherText = parts[3];
            String encodedAuthTag = parts[4];
            
            // Validate version
            if (!ENCRYPTION_VERSION.equals(version)) {
                throw new IllegalArgumentException("Unsupported encryption version: " + version);
            }
            
            log.debug("Successfully parsed encrypted API key components for frontend decryption");
            
            return new EncryptionComponents(version, encodedSalt, encodedIv, encodedCipherText, encodedAuthTag);
            
        } catch (Exception e) {
            log.error("Failed to parse encrypted API key for frontend: {}", e.getMessage());
            throw new RuntimeException("Failed to parse encrypted API key", e);
        }
    }
    
    /**
     * Get encryption constants for frontend implementation
     * 
     * @return EncryptionConstants object with algorithm details
     */
    public EncryptionConstants getEncryptionConstants() {
        return new EncryptionConstants(
            ALGORITHM,
            TRANSFORMATION,
            KEY_DERIVATION_ALGORITHM,
            APPLICATION_PEPPER,
            ENCRYPTION_VERSION,
            AES_KEY_LENGTH,
            GCM_IV_LENGTH,
            GCM_TAG_LENGTH,
            SALT_LENGTH
        );
    }
    
    /**
     * Derives a cryptographically strong AES key from user ID using SHA-256
     * 
     * SECURITY FEATURES:
     * - SHA-256 key derivation (fast, secure, frontend-compatible)
     * - Application pepper adds additional security layer
     * - Salt prevents rainbow table attacks
     * - User ID isolation (each user has unique keys)
     * 
     * @param userId The user ID
     * @param salt Random salt for this encryption
     * @return AES-256 secret key
     */
    private SecretKey deriveKeyFromUserId(String userId, byte[] salt) throws Exception {
        // Combine userId with application pepper for additional security
        String keyMaterial = userId + APPLICATION_PEPPER;
        
        // Use SHA-256 for fast, secure key derivation
        MessageDigest digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM);
        digest.update(keyMaterial.getBytes(StandardCharsets.UTF_8));
        digest.update(salt);
        byte[] keyBytes = digest.digest();
        
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
    
    /**
     * Validates if an encrypted API key has the correct format
     */
    public boolean isValidEncryptedFormat(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.trim().isEmpty()) {
            return false;
        }
        
        String[] parts = encryptedApiKey.split(":");
        return parts.length == 5 && ENCRYPTION_VERSION.equals(parts[0]);
    }
    
    /**
     * Gets the encryption version from an encrypted API key
     */
    public String getEncryptionVersion(String encryptedApiKey) {
        if (encryptedApiKey == null || encryptedApiKey.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = encryptedApiKey.split(":");
        return parts.length > 0 ? parts[0] : null;
    }
    
    /**
     * Securely clears sensitive byte arrays from memory
     */
    private void clearSensitiveData(byte[]... arrays) {
        for (byte[] array : arrays) {
            if (array != null) {
                Arrays.fill(array, (byte) 0);
            }
        }
    }
    
    /**
     * Securely clears secret key from memory
     */
    private void clearSecretKey(SecretKey key) {
        try {
            if (key instanceof SecretKeySpec) {
                // Use reflection to clear the key bytes if possible
                java.lang.reflect.Field keyField = SecretKeySpec.class.getDeclaredField("key");
                keyField.setAccessible(true);
                byte[] keyBytes = (byte[]) keyField.get(key);
                if (keyBytes != null) {
                    Arrays.fill(keyBytes, (byte) 0);
                }
            }
        } catch (Exception e) {
            // If reflection fails, we can't clear the key, but it's not critical
            log.debug("Could not clear secret key from memory: {}", e.getMessage());
        }
    }
    
    /**
     * Data class for encryption components (for frontend use)
     */
    public static class EncryptionComponents {
        private final String version;
        private final String salt;
        private final String iv;
        private final String encryptedData;
        private final String authTag;
        
        public EncryptionComponents(String version, String salt, String iv, String encryptedData, String authTag) {
            this.version = version;
            this.salt = salt;
            this.iv = iv;
            this.encryptedData = encryptedData;
            this.authTag = authTag;
        }
        
        // Getters
        public String getVersion() { return version; }
        public String getSalt() { return salt; }
        public String getIv() { return iv; }
        public String getEncryptedData() { return encryptedData; }
        public String getAuthTag() { return authTag; }
    }
    
    /**
     * Data class for encryption constants (for frontend use)
     */
    public static class EncryptionConstants {
        private final String algorithm;
        private final String transformation;
        private final String keyDerivationAlgorithm;
        private final String applicationPepper;
        private final String encryptionVersion;
        private final int aesKeyLength;
        private final int gcmIvLength;
        private final int gcmTagLength;
        private final int saltLength;
        
        public EncryptionConstants(String algorithm, String transformation, String keyDerivationAlgorithm,
                                 String applicationPepper, String encryptionVersion, int aesKeyLength,
                                 int gcmIvLength, int gcmTagLength, int saltLength) {
            this.algorithm = algorithm;
            this.transformation = transformation;
            this.keyDerivationAlgorithm = keyDerivationAlgorithm;
            this.applicationPepper = applicationPepper;
            this.encryptionVersion = encryptionVersion;
            this.aesKeyLength = aesKeyLength;
            this.gcmIvLength = gcmIvLength;
            this.gcmTagLength = gcmTagLength;
            this.saltLength = saltLength;
        }
        
        // Getters
        public String getAlgorithm() { return algorithm; }
        public String getTransformation() { return transformation; }
        public String getKeyDerivationAlgorithm() { return keyDerivationAlgorithm; }
        public String getApplicationPepper() { return applicationPepper; }
        public String getEncryptionVersion() { return encryptionVersion; }
        public int getAesKeyLength() { return aesKeyLength; }
        public int getGcmIvLength() { return gcmIvLength; }
        public int getGcmTagLength() { return gcmTagLength; }
        public int getSaltLength() { return saltLength; }
    }
}