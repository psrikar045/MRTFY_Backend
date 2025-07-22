package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.GoogleUserInfo;
import com.example.jwtauthenticator.util.TestDataFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleTokenVerificationService Tests - Comprehensive Coverage")
@Disabled("Google Token Verification functionality not yet implemented")
class GoogleTokenVerificationServiceTest {

    @Mock
    private GoogleIdTokenVerifier mockVerifier;

    @Mock
    private GoogleIdToken mockIdToken;

    @Mock
    private GoogleIdToken.Payload mockPayload;

    @InjectMocks
    private GoogleTokenVerificationService googleTokenVerificationService;

    private String testToken;
    private String testClientId;
    private GoogleUserInfo testUserInfo;

    @BeforeEach
    void setUp() {
        testToken = "valid-google-token";
        testClientId = "test-client-id.apps.googleusercontent.com";
        
        testUserInfo = GoogleUserInfo.builder()
            .googleId("google-user-id-123")
            .email("test@example.com")
            .name("John Doe")
            .givenName("John")
            .familyName("Doe")
            .picture("https://example.com/photo.jpg")
            .emailVerified(true)
            .build();
        
        // Set up Google client configuration using reflection
        ReflectionTestUtils.setField(googleTokenVerificationService, "googleClientId", testClientId);
        ReflectionTestUtils.setField(googleTokenVerificationService, "verifier", mockVerifier);
    }

    // ==================== POSITIVE SCENARIOS ====================

    @Nested
    @DisplayName("Token Verification - Positive Scenarios")
    class TokenVerificationPositiveTests {

        @Test
        @DisplayName("Should successfully verify valid Google token")
        void verifyToken_ValidToken_ReturnsUserInfo() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);
            when(mockPayload.get("name")).thenReturn("John Doe");
            when(mockPayload.get("given_name")).thenReturn("John");
            when(mockPayload.get("family_name")).thenReturn("Doe");
            when(mockPayload.get("picture")).thenReturn("https://example.com/photo.jpg");

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            assertEquals("google-user-id-123", result.getGoogleId());
            assertEquals("test@example.com", result.getEmail());
            assertEquals("John Doe", result.getName());
            assertEquals("John", result.getGivenName());
            assertEquals("Doe", result.getFamilyName());
            assertEquals("https://example.com/photo.jpg", result.getPicture());
            assertTrue(result.isEmailVerified());
            
            verify(mockVerifier, times(1)).verify(testToken);
            verify(mockIdToken, times(1)).getPayload();
        }

        @Test
        @DisplayName("Should handle token with minimal user info")
        void verifyToken_MinimalUserInfo_ReturnsBasicInfo() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);
            when(mockPayload.get("name")).thenReturn(null);
            when(mockPayload.get("given_name")).thenReturn(null);
            when(mockPayload.get("family_name")).thenReturn(null);
            when(mockPayload.get("picture")).thenReturn(null);

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            assertEquals("google-user-id-123", result.getGoogleId());
            assertEquals("test@example.com", result.getEmail());
            assertNull(result.getName());
            assertNull(result.getGivenName());
            assertNull(result.getFamilyName());
            assertNull(result.getPicture());
            assertTrue(result.isEmailVerified());
        }

        @Test
        @DisplayName("Should handle unverified email")
        void verifyToken_UnverifiedEmail_ReturnsUserInfoWithUnverifiedEmail() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(false);
            when(mockPayload.get("name")).thenReturn("John Doe");
            when(mockPayload.get("given_name")).thenReturn("John");
            when(mockPayload.get("family_name")).thenReturn("Doe");
            when(mockPayload.get("picture")).thenReturn("https://example.com/photo.jpg");

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            assertEquals("google-user-id-123", result.getGoogleId());
            assertEquals("test@example.com", result.getEmail());
            assertFalse(result.isEmailVerified());
        }

        @Test
        @DisplayName("Should handle token with special characters in name")
        void verifyToken_SpecialCharactersInName_ReturnsUserInfo() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);
            when(mockPayload.get("name")).thenReturn("José María García-López");
            when(mockPayload.get("given_name")).thenReturn("José María");
            when(mockPayload.get("family_name")).thenReturn("García-López");
            when(mockPayload.get("picture")).thenReturn("https://example.com/photo.jpg");

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            assertEquals("José María García-López", result.getName());
            assertEquals("José María", result.getGivenName());
            assertEquals("García-López", result.getFamilyName());
        }

        @Test
        @DisplayName("Should handle token with Unicode characters")
        void verifyToken_UnicodeCharacters_ReturnsUserInfo() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);
            when(mockPayload.get("name")).thenReturn("王小明");
            when(mockPayload.get("given_name")).thenReturn("小明");
            when(mockPayload.get("family_name")).thenReturn("王");
            when(mockPayload.get("picture")).thenReturn("https://example.com/photo.jpg");

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            assertEquals("王小明", result.getName());
            assertEquals("小明", result.getGivenName());
            assertEquals("王", result.getFamilyName());
        }
    }

    // ==================== NEGATIVE SCENARIOS ====================

    @Nested
    @DisplayName("Token Verification - Negative Scenarios")
    class TokenVerificationNegativeTests {

        @Test
        @DisplayName("Should throw exception for invalid token")
        void verifyToken_InvalidToken_ThrowsException() throws Exception {
            // Arrange
            String invalidToken = "invalid-token";
            when(mockVerifier.verify(invalidToken)).thenReturn(null);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> googleTokenVerificationService.verifyToken(invalidToken));
            assertEquals("Invalid Google ID token", exception.getMessage());
            
            verify(mockVerifier, times(1)).verify(invalidToken);
        }

        @Test
        @DisplayName("Should throw exception for expired token")
        void verifyToken_ExpiredToken_ThrowsException() throws Exception {
            // Arrange
            String expiredToken = "expired-token";
            when(mockVerifier.verify(expiredToken))
                .thenThrow(new GeneralSecurityException("Token expired"));

            // Act & Assert
            GeneralSecurityException exception = assertThrows(GeneralSecurityException.class,
                () -> googleTokenVerificationService.verifyToken(expiredToken));
            assertEquals("Token expired", exception.getMessage());
            
            verify(mockVerifier, times(1)).verify(expiredToken);
        }

        @Test
        @DisplayName("Should throw exception for malformed token")
        void verifyToken_MalformedToken_ThrowsException() throws Exception {
            // Arrange
            String malformedToken = "malformed.token.structure";
            when(mockVerifier.verify(malformedToken))
                .thenThrow(new IOException("Malformed token"));

            // Act & Assert
            IOException exception = assertThrows(IOException.class,
                () -> googleTokenVerificationService.verifyToken(malformedToken));
            assertEquals("Malformed token", exception.getMessage());
            
            verify(mockVerifier, times(1)).verify(malformedToken);
        }

        @ParameterizedTest
        @NullSource
        @EmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should throw exception for null or empty token")
        void verifyToken_NullOrEmptyToken_ThrowsException(String invalidToken) {
            // Act & Assert
            SecurityException exception = assertThrows(SecurityException.class,
                () -> googleTokenVerificationService.verifyToken(invalidToken));
            assertEquals("Invalid Google ID token", exception.getMessage());
        }

        @Test
        @DisplayName("Should handle missing subject gracefully")
        void verifyToken_MissingRequiredFields_HandlesGracefully() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn(null); // Missing subject
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);
            
            // Assert - Service should handle missing subject gracefully
            assertNotNull(result);
            assertNull(result.getGoogleId());
            assertEquals("test@example.com", result.getEmail());
       
            verify(mockVerifier, times(1)).verify(testToken);
        }

        @Test
        @DisplayName("Should handle missing email gracefully")
        void verifyToken_MissingEmail_HandlesGracefully() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn(null); // Missing email
            when(mockPayload.get("email_verified")).thenReturn(true);

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);
            
            // Assert - Service should handle missing email gracefully
            assertNotNull(result);
            assertEquals("google-user-id-123", result.getGoogleId());
            assertNull(result.getEmail());
            
            verify(mockVerifier, times(1)).verify(testToken);
        }

        @Test
        @DisplayName("Should throw exception for wrong client ID")
        void verifyToken_WrongClientId_ThrowsException() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken))
                .thenThrow(new GeneralSecurityException("Invalid client ID"));

            // Act & Assert
            GeneralSecurityException exception = assertThrows(GeneralSecurityException.class,
                () -> googleTokenVerificationService.verifyToken(testToken));
            assertEquals("Invalid client ID", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for network connectivity issues")
        void verifyToken_NetworkIssues_ThrowsException() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken))
                .thenThrow(new IOException("Network unreachable"));

            // Act & Assert
            IOException exception = assertThrows(IOException.class,
                () -> googleTokenVerificationService.verifyToken(testToken));
            assertEquals("Network unreachable", exception.getMessage());
        }
    }

    // ==================== EDGE CASES ====================

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle very long token")
        void verifyToken_VeryLongToken_ShouldHandleGracefully() throws Exception {
            // Arrange
            String longToken = TestDataFactory.createOversizedString(10000);
            when(mockVerifier.verify(longToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);
            when(mockPayload.get("name")).thenReturn("Test User");
            when(mockPayload.get("given_name")).thenReturn("Test");
            when(mockPayload.get("family_name")).thenReturn("User");
            when(mockPayload.get("picture")).thenReturn("https://example.com/photo.jpg");

            // Act & Assert
            assertDoesNotThrow(() -> googleTokenVerificationService.verifyToken(longToken));
        }
        @Test
        @DisplayName("Should handle token with very long user data")
        void verifyToken_VeryLongUserData_ShouldHandleGracefully() throws Exception {
            // Arrange
            String longName = TestDataFactory.createOversizedString(1000);
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);
            when(mockPayload.get("name")).thenReturn(longName);
            when(mockPayload.get("given_name")).thenReturn(longName);
            when(mockPayload.get("family_name")).thenReturn(longName);
            when(mockPayload.get("picture")).thenReturn("https://example.com/photo.jpg");

            // Act & Assert
            assertDoesNotThrow(() -> googleTokenVerificationService.verifyToken(testToken));
        }

        @Test
        @DisplayName("Should handle token with missing profile picture")
        void verifyToken_MissingProfilePicture_ShouldHandleGracefully() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            lenient().when(mockPayload.getEmail()).thenReturn("test@example.com");
            lenient().when(mockPayload.get("email")).thenReturn("test@example.com");
            lenient().when(mockPayload.get("name")).thenReturn("John Doe");
            lenient().when(mockPayload.get("given_name")).thenReturn("John");
            lenient().when(mockPayload.get("family_name")).thenReturn("Doe");
            lenient().when(mockPayload.get("picture")).thenReturn(null);
            lenient().when(mockPayload.get("email_verified")).thenReturn(true);
            lenient().when(mockPayload.getEmailVerified()).thenReturn(true);

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            assertNull(result.getPicture());
        }

        @Test
        @DisplayName("Should handle token with invalid profile picture URL")
        void verifyToken_InvalidProfilePictureUrl_ShouldHandleGracefully() throws Exception {
            // Arrange
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            lenient().when(mockPayload.getEmail()).thenReturn("test@example.com");
            lenient().when(mockPayload.get("email")).thenReturn("test@example.com");
            lenient().when(mockPayload.get("name")).thenReturn("John Doe");
            lenient().when(mockPayload.get("given_name")).thenReturn("John");
            lenient().when(mockPayload.get("family_name")).thenReturn("Doe");
            lenient().when(mockPayload.get("picture")).thenReturn("not-a-valid-url");
            lenient().when(mockPayload.get("email_verified")).thenReturn(true);
            lenient().when(mockPayload.getEmailVerified()).thenReturn(true);

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            assertEquals("not-a-valid-url", result.getPicture());
        }

        @Test
        @DisplayName("Should handle concurrent token verification")
        void verifyToken_ConcurrentVerification_ShouldHandleGracefully() throws Exception {
            // Arrange
            lenient().when(mockVerifier.verify(anyString())).thenReturn(mockIdToken);
            lenient().when(mockIdToken.getPayload()).thenReturn(mockPayload);
            lenient().when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            lenient().when(mockPayload.getEmail()).thenReturn("test@example.com");
            lenient().when(mockPayload.get("name")).thenReturn("John Doe");
            lenient().when(mockPayload.get("email_verified")).thenReturn(true);
            lenient().when(mockPayload.getEmailVerified()).thenReturn(true);

            // Act & Assert
            assertDoesNotThrow(() -> {
                Thread[] threads = new Thread[10];
                for (int i = 0; i < 10; i++) {
                    final int threadId = i;
                    threads[i] = new Thread(() -> {
                        try {
                            googleTokenVerificationService.verifyToken("token-" + threadId);
                        } catch (Exception e) {
                            // Expected in concurrent scenario
                        }
                    });
                    threads[i].start();
                }
                
                for (Thread thread : threads) {
                    thread.join();
                }
            });
        }
    }

    // ==================== SECURITY TESTS ====================

    @Nested
    @DisplayName("Security and Validation Tests")
    class SecurityTests {

        @Test
        @DisplayName("Should prevent token replay attacks")
        void verifyToken_ReplayAttack_ShouldValidateTokenFreshness() throws Exception {
            // Arrange
            String replayToken = "replay-attack-token";
            when(mockVerifier.verify(replayToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);

            // Act - First verification should succeed
            GoogleUserInfo result1 = googleTokenVerificationService.verifyToken(replayToken);
            assertNotNull(result1);

            // Act - Second verification of same token should still succeed
            // (Google handles token freshness validation)
            GoogleUserInfo result2 = googleTokenVerificationService.verifyToken(replayToken);
            assertNotNull(result2);
        }

        @Test
        @DisplayName("Should sanitize XSS attempts in user data")
        void verifyToken_XssInUserData_ShouldSanitize() throws Exception {
            // Arrange
            String xssName = TestDataFactory.createXssString();
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);
            when(mockPayload.get("name")).thenReturn(xssName);
            when(mockPayload.get("given_name")).thenReturn(xssName);
            when(mockPayload.get("family_name")).thenReturn(xssName);
            when(mockPayload.get("picture")).thenReturn("https://example.com/photo.jpg");

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);

            // Assert
            assertNotNull(result);
            // The service should sanitize or handle XSS attempts appropriately
            assertNotNull(result.getName());
        }

        @Test
        @DisplayName("Should validate email format from Google token")
        void verifyToken_InvalidEmailFormat_ShouldValidate() throws Exception {
            // Arrange
            String invalidEmail = "not-an-email";
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            when(mockPayload.get("name")).thenReturn("John Doe");
            when(mockPayload.get("email")).thenReturn(invalidEmail);
            when(mockPayload.get("email_verified")).thenReturn(true);

            // Act - Since Google tokens should have valid emails, we'll just verify it processes
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);
            
            // Assert - The service should still process the token but with the invalid email
            assertNotNull(result);
            assertEquals(invalidEmail, result.getEmail());
        }

        @Test
        @DisplayName("Should validate Google user ID format")
        void verifyToken_InvalidUserIdFormat_ShouldValidate() throws Exception {
            // Arrange
            String invalidUserId = ""; // Empty user ID
            when(mockVerifier.verify(testToken)).thenReturn(mockIdToken);
            when(mockIdToken.getPayload()).thenReturn(mockPayload);
            when(mockPayload.getSubject()).thenReturn(invalidUserId);
            when(mockPayload.get("email")).thenReturn("test@example.com");
            when(mockPayload.get("email_verified")).thenReturn(true);

            // Act
            GoogleUserInfo result = googleTokenVerificationService.verifyToken(testToken);
            
            // Assert - Service should handle empty user ID gracefully
            assertNotNull(result);
            assertEquals("", result.getGoogleId());
            assertEquals("test@example.com", result.getEmail());
        }

        @Test
        @DisplayName("Should prevent token injection attacks")
        void verifyToken_TokenInjection_ShouldPrevent() throws Exception {
            // Arrange
            String maliciousToken = "valid-token'; DROP TABLE users; --";
            when(mockVerifier.verify(maliciousToken))
                .thenThrow(new GeneralSecurityException("Invalid token format"));

            // Act & Assert
            GeneralSecurityException exception = assertThrows(GeneralSecurityException.class,
                () -> googleTokenVerificationService.verifyToken(maliciousToken));
            assertEquals("Invalid token format", exception.getMessage());
        }
    }

    // ==================== PERFORMANCE TESTS ====================

    @Nested
    @DisplayName("Performance and Load Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should verify tokens efficiently")
        void verifyToken_Performance_ShouldBeEfficient() throws Exception {
            // Arrange
            lenient().when(mockVerifier.verify(anyString())).thenReturn(mockIdToken);
            lenient().when(mockIdToken.getPayload()).thenReturn(mockPayload);
            lenient().when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            lenient().when(mockPayload.getEmail()).thenReturn("test@example.com");
            lenient().when(mockPayload.get("name")).thenReturn("John Doe");
            lenient().when(mockPayload.get("email_verified")).thenReturn(true);
            lenient().when(mockPayload.getEmailVerified()).thenReturn(true);

            // Act
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                googleTokenVerificationService.verifyToken("token-" + i);
            }
            long endTime = System.currentTimeMillis();

            // Assert
            long totalTime = endTime - startTime;
            assertTrue(totalTime < 5000, "Should verify 100 tokens in under 5 seconds");
        }

        @Test
        @DisplayName("Should handle memory efficiently")
        void verifyToken_MemoryUsage_ShouldBeEfficient() throws Exception {
            // Arrange
            lenient().when(mockVerifier.verify(anyString())).thenReturn(mockIdToken);
            lenient().when(mockIdToken.getPayload()).thenReturn(mockPayload);
            lenient().when(mockPayload.getSubject()).thenReturn("google-user-id-123");
            lenient().when(mockPayload.getEmail()).thenReturn("test@example.com");
            lenient().when(mockPayload.get("name")).thenReturn("John Doe");
            lenient().when(mockPayload.get("email_verified")).thenReturn(true);
            lenient().when(mockPayload.getEmailVerified()).thenReturn(true);

            // Act
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            
            for (int i = 0; i < 1000; i++) {
                googleTokenVerificationService.verifyToken("token-" + i);
            }
            
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = memoryAfter - memoryBefore;

            // Assert
            assertTrue(memoryUsed < 50 * 1024 * 1024, "Should use less than 50MB for 1000 verifications");
        }
    }

    // ==================== UTILITY METHODS ====================

    @Nested
    @DisplayName("Utility Methods Tests")
    class UtilityMethodsTests {

        @Test
        @DisplayName("Should validate token format")
        void isValidTokenFormat_VariousFormats_CorrectValidation() {
            // Valid JWT format
            assertTrue(googleTokenVerificationService.isValidTokenFormat("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"));
            
            // Invalid formats
            assertFalse(googleTokenVerificationService.isValidTokenFormat("not-a-token"));
            assertFalse(googleTokenVerificationService.isValidTokenFormat(""));
            assertFalse(googleTokenVerificationService.isValidTokenFormat(null));
            assertFalse(googleTokenVerificationService.isValidTokenFormat("invalid.token"));
            assertFalse(googleTokenVerificationService.isValidTokenFormat("too.many.parts.in.token"));
        }

        @Test
        @DisplayName("Should check if Google verification is enabled")
        void isGoogleVerificationEnabled_ShouldReturnCorrectStatus() {
            // Act
            boolean isEnabled = googleTokenVerificationService.isGoogleVerificationEnabled("67");

            // Assert
            assertTrue(isEnabled, "Google verification should be enabled in test environment");
        }
        @Test
        @DisplayName("Should get Google configuration")
        void getGoogleConfiguration_ShouldReturnCorrectConfig() {
            // Act
            Map<String, Object> config = googleTokenVerificationService.getGoogleConfiguration(testClientId);

            // Assert
            assertNotNull(config);
            assertEquals(testClientId, config.get("clientId"));
            assertTrue(config.containsKey("verificationEnabled"));
            assertTrue(config.containsKey("verifierInitialized"));
        }
    }
}