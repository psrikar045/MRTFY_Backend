package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.config.TestSecurityConfig;
import com.example.jwtauthenticator.dto.RegisterResponse;
import com.example.jwtauthenticator.dto.ForgotPasswordRequest;
import com.example.jwtauthenticator.dto.GoogleSignInRequest;
import com.example.jwtauthenticator.dto.GoogleUserInfo;
import com.example.jwtauthenticator.dto.ResetPasswordConfirmRequest;
import com.example.jwtauthenticator.dto.CheckUsernameRequest;
import com.example.jwtauthenticator.dto.CheckEmailRequest;
import com.example.jwtauthenticator.model.AuthRequest;
import com.example.jwtauthenticator.model.AuthResponse;
import com.example.jwtauthenticator.model.RegisterRequest;
import com.example.jwtauthenticator.service.AuthService;
import com.example.jwtauthenticator.service.GoogleTokenVerificationService;
import com.example.jwtauthenticator.service.PasswordResetService;
import com.example.jwtauthenticator.service.RateLimiterService;
import com.example.jwtauthenticator.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private GoogleTokenVerificationService googleTokenVerificationService;

    @MockBean
    private PasswordResetService passwordResetService;

    @MockBean
    private RateLimiterService rateLimiterService;

    private RegisterRequest registerRequest;
    private AuthRequest authRequest;
    private AuthResponse authResponse;
    private RegisterResponse registerResponse;

    @BeforeEach
    void setUp() {
        registerRequest = TestDataFactory.createRegisterRequest();
        authRequest = TestDataFactory.createAuthRequest();
        authResponse = new AuthResponse(
            TestDataFactory.createTestJwtToken(),
            TestDataFactory.createTestRefreshToken(),
            "test-brand",
            3600L
        );
        registerResponse = new RegisterResponse(
            "User registered successfully. Please verify your email.",
            "test-brand",
            "testuser",
            "test@example.com",
            true
        );
        
        // Mock rate limiter to allow all requests
        ConsumptionProbe allowedProbe = mock(ConsumptionProbe.class);
        when(allowedProbe.isConsumed()).thenReturn(true);
        when(rateLimiterService.consume(anyString())).thenReturn(allowedProbe);
        when(rateLimiterService.consumePublic(anyString())).thenReturn(allowedProbe);
    }

    @Test
    void register_success() throws Exception {
        // Arrange
        when(authService.registerUser(any(RegisterRequest.class))).thenReturn(registerResponse);

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully. Please verify your email."))
                .andExpect(jsonPath("$.success").value(true));

        verify(authService, times(1)).registerUser(any(RegisterRequest.class));
    }

    @Test
    void register_validationError() throws Exception {
        // Arrange
        RegisterRequest invalidRequest = new RegisterRequest(
            "", // Empty username
            "weak", // Weak password
            "invalid-email", // Invalid email
            "",
            "",
            "",
            "",
            ""
        );

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
                .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(authService, never()).registerUser(any(RegisterRequest.class));
    }

    @Test
    void register_userAlreadyExists() throws Exception {
        // Arrange
        when(authService.registerUser(any(RegisterRequest.class)))
            .thenThrow(new RuntimeException("Username already exists for this brand"));

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists for this brand"));

        verify(authService, times(1)).registerUser(any(RegisterRequest.class));
    }

    @Test
    void login_success() throws Exception {
        // Arrange
        when(authService.createAuthenticationToken(any(AuthRequest.class))).thenReturn(authResponse);

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(authResponse.token()))
                .andExpect(jsonPath("$.refreshToken").value(authResponse.refreshToken()))
                .andExpect(jsonPath("$.brandId").value(authResponse.brandId()))
                .andExpect(jsonPath("$.expirationTime").value(authResponse.expirationTime()));

        verify(authService, times(1)).createAuthenticationToken(any(AuthRequest.class));
    }

    @Test
    void login_invalidCredentials() throws Exception {
        // Arrange
        when(authService.createAuthenticationToken(any(AuthRequest.class)))
            .thenThrow(new RuntimeException("Invalid credentials"));

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        verify(authService, times(1)).createAuthenticationToken(any(AuthRequest.class));
    }

    @Test
    void login_emailNotVerified() throws Exception {
        // Arrange
        when(authService.createAuthenticationToken(any(AuthRequest.class)))
            .thenThrow(new RuntimeException("Email not verified. Please verify your email to login."));

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
                .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email not verified. Please verify your email to login."));

        verify(authService, times(1)).createAuthenticationToken(any(AuthRequest.class));
    }

    @Test
    void login_rateLimited() throws Exception {
        // Arrange
        ConsumptionProbe deniedProbe = mock(ConsumptionProbe.class);
        when(deniedProbe.isConsumed()).thenReturn(false);
        when(rateLimiterService.consume(anyString())).thenReturn(deniedProbe);

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest))
                .with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));

        verify(authService, never()).createAuthenticationToken(any(AuthRequest.class));
    }

    @Test
    void googleSignIn_success() throws Exception {
        // Arrange
        GoogleSignInRequest googleRequest = TestDataFactory.createGoogleSignInRequest();
        GoogleUserInfo googleUserInfo = GoogleUserInfo.builder()
            .email("test@example.com")
            .name("John Doe")
            .givenName("John")
            .familyName("Doe")
            .picture("https://example.com/photo.jpg")
            .emailVerified(true)
            .googleId("google-id")
            .build();
        
        when(authService.googleSignIn(any(GoogleSignInRequest.class))).thenReturn(authResponse);

        // Act & Assert
        mockMvc.perform(post("/auth/google-signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(googleRequest))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(authResponse.token()))
                .andExpect(jsonPath("$.refreshToken").value(authResponse.refreshToken()))
                .andExpect(jsonPath("$.brandId").value(authResponse.brandId()))
                .andExpect(jsonPath("$.expirationTime").value(authResponse.expirationTime()));

        verify(authService, times(1)).googleSignIn(any(GoogleSignInRequest.class));
    }

    @Test
    void googleSignIn_invalidToken() throws Exception {
        // Arrange
        GoogleSignInRequest googleRequest = TestDataFactory.createGoogleSignInRequest();
        when(googleTokenVerificationService.verifyToken(anyString()))
            .thenThrow(new RuntimeException("Invalid Google token"));

        // Act & Assert
        mockMvc.perform(post("/auth/google-signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(googleRequest))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid Google token"));

        verify(authService, never()).googleSignIn(any(GoogleSignInRequest.class));
    }

    @Test
    void refreshToken_success() throws Exception {
        // Arrange
        String refreshToken = TestDataFactory.createTestRefreshToken();
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", refreshToken);
        
        when(authService.refreshToken(anyString())).thenReturn(authResponse);

        // Act & Assert
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(authResponse.token()))
                .andExpect(jsonPath("$.refreshToken").value(authResponse.refreshToken()));

        verify(authService, times(1)).refreshToken(refreshToken);
    }

    @Test
    void refreshToken_invalid() throws Exception {
        // Arrange
        String refreshToken = "invalid-token";
        Map<String, String> request = new HashMap<>();
        request.put("refreshToken", refreshToken);
        
        when(authService.refreshToken(anyString()))
            .thenThrow(new RuntimeException("Invalid refresh token"));

        // Act & Assert
        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));

        verify(authService, times(1)).refreshToken(refreshToken);
    }

    @Test
    void verifyEmail_success() throws Exception {
        // Arrange
        String token = TestDataFactory.createTestVerificationToken();
        when(authService.verifyEmail(anyString())).thenReturn("Email verified successfully!");

        // Act & Assert
        mockMvc.perform(get("/auth/verify-email")
                .param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string("Email verified successfully!"));

        verify(authService, times(1)).verifyEmail(token);
    }

    @Test
    void verifyEmail_invalidToken() throws Exception {
        // Arrange
        String token = "invalid-token";
        when(authService.verifyEmail(anyString()))
            .thenThrow(new RuntimeException("Invalid verification token"));

        // Act & Assert
        mockMvc.perform(get("/auth/verify-email")
                .param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid verification token"));

        verify(authService, times(1)).verifyEmail(token);
    }

    @Test
    void forgotPassword_success() throws Exception {
        // Arrange
        ForgotPasswordRequest request = TestDataFactory.createForgotPasswordRequest();
        doNothing().when(passwordResetService).createPasswordResetToken(anyString());

        // Act & Assert
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset instructions sent to your email"));

        verify(passwordResetService, times(1)).createPasswordResetToken(request.email());
    }

    @Test
    void forgotPassword_userNotFound() throws Exception {
        // Arrange
        ForgotPasswordRequest request = TestDataFactory.createForgotPasswordRequest();
        doThrow(new RuntimeException("User not found"))
            .when(passwordResetService).createPasswordResetToken(anyString());

        // Act & Assert
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        verify(passwordResetService, times(1)).createPasswordResetToken(request.email());
    }

    @Test
    void resetPassword_success() throws Exception {
        // Arrange
        ResetPasswordConfirmRequest request = TestDataFactory.createResetPasswordConfirmRequest();
        doNothing().when(passwordResetService).resetPassword(anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"));

        verify(passwordResetService, times(1)).resetPassword(request.token(), request.newPassword());
    }

    @Test
    void resetPassword_invalidToken() throws Exception {
        // Arrange
        ResetPasswordConfirmRequest request = TestDataFactory.createResetPasswordConfirmRequest();
        doThrow(new RuntimeException("Invalid or expired reset token"))
            .when(passwordResetService).resetPassword(anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));

        verify(passwordResetService, times(1)).resetPassword(request.token(), request.newPassword());
    }

    @Test
    void checkUsername_available() throws Exception {
        // Arrange
        CheckUsernameRequest request = TestDataFactory.createCheckUsernameRequest("newuser", "test-brand");
        when(authService.checkUsernameExists(any(CheckUsernameRequest.class))).thenReturn(false);

        // Act & Assert
        mockMvc.perform(post("/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.message").value("Username is available"));

        verify(authService, times(1)).checkUsernameExists(any(CheckUsernameRequest.class));
    }
//
    @Test
    void checkUsername_notAvailable() throws Exception {
        // Arrange
        CheckUsernameRequest request = TestDataFactory.createCheckUsernameRequest("existinguser", "test-brand");
        when(authService.checkUsernameExists(any(CheckUsernameRequest.class))).thenReturn(true);

        // Act & Assert
        mockMvc.perform(post("/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value("Username is not available"));

        verify(authService, times(1)).checkUsernameExists(any(CheckUsernameRequest.class));
    }
}