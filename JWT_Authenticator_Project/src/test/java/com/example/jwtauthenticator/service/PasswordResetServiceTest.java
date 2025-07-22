package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.entity.PasswordResetToken;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.repository.PasswordResetTokenRepository;
import com.example.jwtauthenticator.config.AppConfig;
import com.example.jwtauthenticator.security.JwtUserDetailsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService Tests")
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUserDetailsService jwtUserDetailsService;

    @Mock
    private AppConfig appConfig;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User testUser;
    private PasswordResetToken testToken;
    private String testEmail;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testUser = User.builder()
                .id("DOMBR000001")
                .userId(UUID.randomUUID())
                .username("testuser")
                .email(testEmail)
                .password("encodedPassword")
                .firstName("Test")
                .lastName("User")
                .role(User.Role.USER)
                .authProvider(User.AuthProvider.LOCAL)
                .build();

        testToken = PasswordResetToken.builder()
                .id(1L)
                .token("test-token")
                .user(testUser)
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    @Test
    @DisplayName("Should create password reset token for existing user")
    void createPasswordResetToken_ValidEmail_Success() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);
        when(appConfig.getApiUrl(anyString())).thenReturn("http://localhost:8080/api");
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString());

        // Act & Assert
        assertDoesNotThrow(() -> passwordResetService.createPasswordResetToken(testEmail));
        
        verify(userRepository, times(1)).findByEmail(testEmail);
        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(emailService, times(1)).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void createPasswordResetToken_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> passwordResetService.createPasswordResetToken(testEmail));
        assertEquals("User with that email not found", exception.getMessage());
        
        verify(userRepository, times(1)).findByEmail(testEmail);
        verify(passwordResetTokenRepository, never()).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Should reset password with valid token")
    void resetPassword_ValidToken_Success() {
        // Arrange
        String newPassword = "newPassword123";
        when(passwordResetTokenRepository.findByToken(testToken.getToken()))
                .thenReturn(Optional.of(testToken));
        when(jwtUserDetailsService.save(any(User.class))).thenReturn(testUser);
        doNothing().when(emailService).sendPasswordResetConfirmation(anyString(), anyString());

        // Act & Assert
        assertDoesNotThrow(() -> passwordResetService.resetPassword(testToken.getToken(), newPassword));
        
        verify(passwordResetTokenRepository, times(1)).findByToken(testToken.getToken());
        verify(jwtUserDetailsService, times(1)).save(testUser);
        verify(emailService, times(1)).sendPasswordResetConfirmation(testUser.getEmail(), testUser.getUsername());
        verify(passwordResetTokenRepository, times(1)).delete(testToken);
        assertEquals(newPassword, testUser.getPassword());
    }

    @Test
    @DisplayName("Should throw exception for invalid token")
    void resetPassword_InvalidToken_ThrowsException() {
        // Arrange
        String invalidToken = "invalid-token";
        String newPassword = "newPassword123";
        when(passwordResetTokenRepository.findByToken(invalidToken)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> passwordResetService.resetPassword(invalidToken, newPassword));
        assertEquals("Invalid or expired password reset token", exception.getMessage());
        
        verify(passwordResetTokenRepository, times(1)).findByToken(invalidToken);
        verify(jwtUserDetailsService, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception for expired token")
    void resetPassword_ExpiredToken_ThrowsException() {
        // Arrange
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .id(2L)
                .token("expired-token")
                .user(testUser)
                .expiryDate(LocalDateTime.now().minusHours(1))
                .build();
        String newPassword = "newPassword123";
        
        when(passwordResetTokenRepository.findByToken(expiredToken.getToken()))
                .thenReturn(Optional.of(expiredToken));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> passwordResetService.resetPassword(expiredToken.getToken(), newPassword));
        assertEquals("Password reset token has expired", exception.getMessage());
        
        verify(passwordResetTokenRepository, times(1)).findByToken(expiredToken.getToken());
        verify(jwtUserDetailsService, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should create password reset token for user directly")
    void createPasswordResetTokenForUser_ValidUser_Success() {
        // Arrange
        String token = "test-token-123";
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);
        when(appConfig.getApiUrl(anyString())).thenReturn("http://localhost:8080/api");
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString(), anyString());

        // Act & Assert
        assertDoesNotThrow(() -> passwordResetService.createPasswordResetTokenForUser(testUser, token));
        
        verify(passwordResetTokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(emailService, times(1)).sendPasswordResetEmail(
                testUser.getEmail(), 
                testUser.getUsername(), 
                token, 
                "http://localhost:8080/api"
        );
    }
}