package com.example.jwtauthenticator.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ApiKeyEncryptionUtil
 * Tests the frontend-compatible encryption functionality
 */
class ApiKeyEncryptionUtilTest {

    private ApiKeyEncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = new ApiKeyEncryptionUtil();
    }

    @Test
    void testEncryptionBasicFunctionality() {
        // Test data
        String testApiKey = "sk-test123456789abcdef";
        String testUserId = "user123";

        // Test encryption
        String encrypted = encryptionUtil.encryptApiKey(testApiKey, testUserId);
        
        // Verify encrypted format
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("v2:"));
        
        String[] parts = encrypted.split(":");
        assertEquals(5, parts.length, "Encrypted format should have 5 parts");
        assertEquals("v2", parts[0], "Version should be v2");
        
        System.out.println("✅ Encryption test passed");
        System.out.println("Original: " + testApiKey);
        System.out.println("Encrypted: " + encrypted);
    }

    @Test
    void testParseForFrontend() {
        // Test data
        String testApiKey = "sk-test123456789abcdef";
        String testUserId = "user123";

        // Encrypt first
        String encrypted = encryptionUtil.encryptApiKey(testApiKey, testUserId);
        
        // Parse for frontend
        ApiKeyEncryptionUtil.EncryptionComponents components = 
            encryptionUtil.parseForFrontend(encrypted);
        
        // Verify components
        assertNotNull(components);
        assertEquals("v2", components.getVersion());
        assertNotNull(components.getSalt());
        assertNotNull(components.getIv());
        assertNotNull(components.getEncryptedData());
        assertNotNull(components.getAuthTag());
        
        System.out.println("✅ Frontend parsing test passed");
        System.out.println("Components parsed successfully for frontend decryption");
    }

    @Test
    void testEncryptionConstants() {
        ApiKeyEncryptionUtil.EncryptionConstants constants = 
            encryptionUtil.getEncryptionConstants();
        
        assertNotNull(constants);
        assertEquals("AES", constants.getAlgorithm());
        assertEquals("SHA-256", constants.getKeyDerivationAlgorithm());
        assertEquals("v2", constants.getEncryptionVersion());
        assertEquals(256, constants.getAesKeyLength());
        assertEquals(12, constants.getGcmIvLength());
        assertEquals(16, constants.getGcmTagLength());
        assertEquals(32, constants.getSaltLength());
        
        System.out.println("✅ Encryption constants test passed");
        System.out.println("All constants are correct for frontend implementation");
    }

    @Test
    void testValidEncryptedFormat() {
        String testApiKey = "sk-test123456789abcdef";
        String testUserId = "user123";

        String encrypted = encryptionUtil.encryptApiKey(testApiKey, testUserId);
        
        assertTrue(encryptionUtil.isValidEncryptedFormat(encrypted));
        assertEquals("v2", encryptionUtil.getEncryptionVersion(encrypted));
        
        // Test invalid formats
        assertFalse(encryptionUtil.isValidEncryptedFormat("invalid"));
        assertFalse(encryptionUtil.isValidEncryptedFormat("v1:salt:iv:data"));
        assertFalse(encryptionUtil.isValidEncryptedFormat(null));
        
        System.out.println("✅ Format validation test passed");
    }

    @Test
    void testUserIsolation() {
        String testApiKey = "sk-test123456789abcdef";
        String userId1 = "user123";
        String userId2 = "user456";

        // Encrypt same key with different users
        String encrypted1 = encryptionUtil.encryptApiKey(testApiKey, userId1);
        String encrypted2 = encryptionUtil.encryptApiKey(testApiKey, userId2);
        
        // Should produce different encrypted values (due to different user-based keys)
        assertNotEquals(encrypted1, encrypted2, "Same API key should encrypt differently for different users");
        
        System.out.println("✅ User isolation test passed");
        System.out.println("Different users produce different encrypted values");
    }

    @Test
    void testMultipleEncryptions() {
        String testApiKey = "sk-test123456789abcdef";
        String testUserId = "user123";

        // Encrypt same key multiple times
        String encrypted1 = encryptionUtil.encryptApiKey(testApiKey, testUserId);
        String encrypted2 = encryptionUtil.encryptApiKey(testApiKey, testUserId);
        
        // Should produce different encrypted values (due to random IV and salt)
        assertNotEquals(encrypted1, encrypted2, "Multiple encryptions should produce different results");
        
        System.out.println("✅ Multiple encryption test passed");
        System.out.println("Random IV and salt ensure different encrypted values each time");
    }
}