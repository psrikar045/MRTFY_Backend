package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.AppConfig;
import com.example.jwtauthenticator.dto.RegisterResponse;
import com.example.jwtauthenticator.entity.LoginLog;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.model.AuthRequest;
import com.example.jwtauthenticator.model.AuthResponse;
import com.example.jwtauthenticator.model.RegisterRequest;
import com.example.jwtauthenticator.repository.LoginLogRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.security.JwtUserDetailsService;
import com.example.jwtauthenticator.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private JwtUserDetailsService userDetailsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private com.example.jwtauthenticator.service.IdGeneratorService idGeneratorService;
    
    @Mock
    private AppConfig appConfig;
    
    @Mock
    private LoginLogRepository loginLogRepository;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private AuthRequest authRequest;
    private User user;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
            "testuser", 
            "password", 
            "test@example.com", 
            "John", 
            "Doe", 
            "+1234567890", 
            "New York", 
            "brand1"
        );

        authRequest = new AuthRequest("testuser", "password", "brand1");

        user = User.builder()
                .userId(UUID.randomUUID())
                .username("testuser")
                .password("encodedPassword")
                .email("test@example.com")
                .brandId("brand1")
                .emailVerified(true)
                .build();

        userDetails = new org.springframework.security.core.userdetails.User("testuser", "encodedPassword", Collections.emptyList());
    }

    @Test
    void registerUser_success() {
        when(userRepository.existsByUsernameAndBrandId(anyString(), anyString())).thenReturn(false);
        when(userRepository.existsByEmailAndBrandId(anyString(), anyString())).thenReturn(false);
        when(userDetailsService.save(any(User.class))).thenReturn(user);
        // We don't need to mock idGeneratorService.generateDombrUserId() anymore since it's commented out in the code
        when(appConfig.getApiUrl(anyString())).thenReturn("http://localhost:8080/api/auth/verify-email?token=test-token");

        RegisterResponse response = authService.registerUser(registerRequest);

        assertEquals("User registered successfully. Please verify your email.", response.message());
        verify(userDetailsService, times(1)).save(any(User.class));
        verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void registerUser_usernameExists() {
        when(userRepository.existsByUsernameAndBrandId(anyString(), anyString())).thenReturn(true);
        // We don't need to mock idGeneratorService.generateDombrUserId() anymore since it's commented out in the code

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> authService.registerUser(registerRequest));
        assertEquals("Username already exists for this brand", thrown.getMessage());
    }

    @Test
    void createAuthenticationToken_success() throws Exception {
        when(userRepository.findByUsernameAndBrandId(anyString(), anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userDetailsService.loadUserByUsernameAndBrandId(anyString(), anyString())).thenReturn(userDetails);
        when(jwtUtil.generateToken(any(UserDetails.class), anyString())).thenReturn("jwtToken");
        when(jwtUtil.generateRefreshToken(any(UserDetails.class), anyString())).thenReturn("refreshToken");
        
        // Mock login log repository behavior
        when(loginLogRepository.findTopByUserIdAndLoginStatusOrderByLoginTimeDesc(any(UUID.class), eq("SUCCESS")))
            .thenReturn(Optional.empty());

        AuthResponse response = authService.createAuthenticationToken(authRequest);

        assertNotNull(response);
        assertEquals("jwtToken", response.token());
        assertEquals("refreshToken", response.refreshToken());
        verify(userRepository, times(1)).save(any(User.class)); // For refresh token update
        verify(loginLogRepository, times(1)).save(any(LoginLog.class)); // Verify login log is saved
    }

    @Test
    void createAuthenticationToken_emailNotVerified() {
        user.setEmailVerified(false);
        when(userRepository.findByUsernameAndBrandId(anyString(), anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userDetailsService.loadUserByUsernameAndBrandId(anyString(), anyString())).thenReturn(userDetails);
        
        // Mock login log repository behavior for failure log
        when(loginLogRepository.findTopByUserIdAndLoginStatusOrderByLoginTimeDesc(any(UUID.class), eq("FAILURE")))
            .thenReturn(Optional.empty());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> authService.createAuthenticationToken(authRequest));
        assertEquals("Email not verified. Please verify your email to login.", thrown.getMessage());
        
        // Verify that a failure login log was saved
        verify(loginLogRepository, times(2)).save(any(LoginLog.class));
    }

    @Test
    void verifyEmail_success() {
        user.setVerificationToken("testToken");
        user.setEmailVerified(false);
        when(userRepository.findByVerificationToken(anyString())).thenReturn(Optional.of(user));

        String response = authService.verifyEmail("testToken");

        assertEquals("Email verified successfully!", response);
        assertTrue(user.isEmailVerified());
        assertNull(user.getVerificationToken());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void verifyEmail_invalidToken() {
        when(userRepository.findByVerificationToken(anyString())).thenReturn(Optional.empty());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> authService.verifyEmail("invalidToken"));
        assertEquals("Invalid verification token", thrown.getMessage());
    }
}
