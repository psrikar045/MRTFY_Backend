package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.TestDataFactory;
import com.example.jwtauthenticator.dto.TfaRequest;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled("TFA functionality not yet implemented")
class TfaServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoogleAuthenticator googleAuthenticator;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private TfaService tfaService;

    private User testUser;
    private String testUserId;
    private GoogleAuthenticatorKey testKey;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID().toString();
        testUser = TestDataFactory.createTestUser();
        testUser.setId(testUserId);
        
        // Create a mock GoogleAuthenticatorKey - only stub when needed in specific tests
        testKey = mock(GoogleAuthenticatorKey.class);
        
        // Inject the mock GoogleAuthenticator into the service using reflection
        ReflectionTestUtils.setField(tfaService, "gAuth", googleAuthenticator);
    }

    @Test
    void generateNewSecret_success() {
        // Arrange
        when(testKey.getKey()).thenReturn("TESTSECRETKEY123456");
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
        when(googleAuthenticator.createCredentials()).thenReturn(testKey);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        String result = tfaService.generateNewSecret(testUser.getUsername());

        // Assert
        assertNotNull(result);
        assertEquals("TESTSECRETKEY123456", result);
        assertEquals("TESTSECRETKEY123456", testUser.getTfaSecret());
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
        verify(googleAuthenticator, times(1)).createCredentials();
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void generateNewSecret_userNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tfaService.generateNewSecret("nonexistent"));
        assertEquals("User not found", exception.getMessage());
        
        verify(userRepository, times(1)).findByUsername("nonexistent");
        verify(googleAuthenticator, never()).createCredentials();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void generateQRCode_success() throws Exception {
        // Arrange
        String tfaSecret = "TESTSECRETKEY123456";
        testUser.setTfaSecret(tfaSecret);
        
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));

        // Act
        byte[] result = tfaService.generateQRCode(testUser.getUsername());

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
    }

    @Test
    void generateQRCode_userNotFound() {
        // Arrange
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tfaService.generateQRCode("nonexistent"));
        assertEquals("User not found", exception.getMessage());
        
        verify(userRepository, times(1)).findByUsername("nonexistent");
    }

    @Test
    void generateQRCode_noTfaSecret() {
        // Arrange
        testUser.setTfaSecret(null);
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tfaService.generateQRCode(testUser.getUsername()));
        assertEquals("2FA secret not generated. Please generate secret first.", exception.getMessage());
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
    }

    @Test
    void verifyCode_success() {
        // Arrange
        String tfaSecret = "TESTSECRETKEY123456";
        int tfaCode = 123456;
        testUser.setTfaSecret(tfaSecret);
        
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
        when(googleAuthenticator.authorize(tfaSecret, tfaCode)).thenReturn(true);

        // Act
        boolean result = tfaService.verifyCode(testUser.getUsername(), tfaCode);

        // Assert
        assertTrue(result);
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
        verify(googleAuthenticator, times(1)).authorize(tfaSecret, tfaCode);
    }

    @Test
    void verifyCode_invalidCode() {
        // Arrange
        String tfaSecret = "TESTSECRETKEY123456";
        int tfaCode = 654321;
        testUser.setTfaSecret(tfaSecret);
        
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
        when(googleAuthenticator.authorize(tfaSecret, tfaCode)).thenReturn(false);

        // Act
        boolean result = tfaService.verifyCode(testUser.getUsername(), tfaCode);

        // Assert
        assertFalse(result);
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
        verify(googleAuthenticator, times(1)).authorize(tfaSecret, tfaCode);
    }

    @Test
    void verifyCode_userNotFound() {
        // Arrange
        int tfaCode = 123456;
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tfaService.verifyCode("nonexistent", tfaCode));
        assertEquals("User not found", exception.getMessage());
        
        verify(userRepository, times(1)).findByUsername("nonexistent");
        verify(googleAuthenticator, never()).authorize(anyString(), anyInt());
    }

    @Test
    void verifyCode_noTfaSecret() {
        // Arrange
        int tfaCode = 123456;
        testUser.setTfaSecret(null);
        
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> tfaService.verifyCode(testUser.getUsername(), tfaCode));
        assertEquals("2FA not set up for this user", exception.getMessage());
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
        verify(googleAuthenticator, never()).authorize(anyString(), anyInt());
    }

    @Test
    void enableTfa_success() {
        // Arrange
        String tfaSecret = "TESTSECRETKEY123456";
        testUser.setTfaSecret(tfaSecret);
        testUser.setTfaEnabled(false);
        
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
        when(googleAuthenticator.authorize(tfaSecret, 123456)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        boolean result = tfaService.verifyCode(testUser.getUsername(), 123456);
        if (result) {
            tfaService.enableTfa(testUser.getUsername());
        }

        // Assert
        assertTrue(result);
        
        verify(userRepository, times(2)).findByUsername(testUser.getUsername()); // Once for verify, once for enable
        verify(googleAuthenticator, times(1)).authorize(tfaSecret, 123456);
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void verifyCode_invalidVerificationCode() {
        // Arrange
        String tfaSecret = "TESTSECRETKEY123456";
        testUser.setTfaSecret(tfaSecret);
        testUser.setTfaEnabled(false);
        
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
        when(googleAuthenticator.authorize(tfaSecret, 654321)).thenReturn(false);

        // Act
        boolean result = tfaService.verifyCode(testUser.getUsername(), 654321);

        // Assert
        assertFalse(result);
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
        verify(googleAuthenticator, times(1)).authorize(tfaSecret, 654321);
    }

    @Test
    void disableTfa_success() {
        // Arrange
        testUser.setTfaEnabled(true);
        testUser.setTfaSecret("TESTSECRETKEY123456");
        
        when(userRepository.findByUsername(testUser.getUsername())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        tfaService.disableTfa(testUser.getUsername());

        // Assert
        assertFalse(testUser.isTfaEnabled());
        assertNull(testUser.getTfaSecret());
        
        verify(userRepository, times(1)).findByUsername(testUser.getUsername());
        verify(userRepository, times(1)).save(testUser);
    }

    // Removed duplicate test methods - keeping only the original ones above
}