package com.example.jwtauthenticator.integration;

import com.example.jwtauthenticator.config.TestConfig;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.model.AuthRequest;
import com.example.jwtauthenticator.model.RegisterRequest;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.dto.ForgotPasswordRequest;
import com.example.jwtauthenticator.dto.ResetPasswordConfirmRequest;
import com.example.jwtauthenticator.dto.CheckUsernameRequest;
import com.example.jwtauthenticator.dto.CheckEmailRequest;
import com.example.jwtauthenticator.dto.UserProfileUpdateRequestDTO;
import com.example.jwtauthenticator.dto.TfaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class UserFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private RegisterRequest registerRequest;
    private AuthRequest authRequest;
    private String testEmail;
    private String testUsername;
    private String testBrandId;
    private String jwtToken;
    private String refreshToken;

    @BeforeEach
    void setUp() {
        testEmail = "integrationtest@example.com";
        testUsername = "integrationuser";
        testBrandId = "integration-brand";
        
        registerRequest = new RegisterRequest(
            testUsername,
            "password123",
            testEmail,
            "Integration",
            "User",
            "+1234567890",
            "Test City",
            testBrandId
        );
        
        authRequest = new AuthRequest(testUsername, "password123", testBrandId);
        
        // Clean up any existing test data
        userRepository.findByUsername(testUsername)
            .ifPresent(user -> userRepository.delete(user));
        userRepository.findByEmail(testEmail)
            .ifPresent(user -> userRepository.delete(user));
    }

    @Test
    @Order(1)
    void completeUserRegistrationAndLoginFlow() throws Exception {
        // Step 1: Check if username is available
        CheckUsernameRequest usernameCheckRequest = new CheckUsernameRequest(testUsername, testBrandId);
        mockMvc.perform(post("/auth/check-username")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(usernameCheckRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.message").value("Username is available"));

        // Step 2: Check if email is available
        CheckEmailRequest emailCheckRequest = new CheckEmailRequest(testEmail, testBrandId);
        mockMvc.perform(post("/auth/check-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emailCheckRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.message").value("Email is available"));

        // Step 3: Register user
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully. Please verify your email."))
                .andExpect(jsonPath("$.success").value(true));

        // Verify user was created in database
        Optional<User> createdUser = userRepository.findByUsername(testUsername);
        assertTrue(createdUser.isPresent());
        assertFalse(createdUser.get().isEmailVerified());
        assertNotNull(createdUser.get().getVerificationToken());

        // Step 4: Try to login without email verification (should fail)
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email not verified. Please verify your email to login."));

        // Step 5: Verify email
        String verificationToken = createdUser.get().getVerificationToken();
        mockMvc.perform(get("/auth/verify-email")
                .param("token", verificationToken))
                .andExpect(status().isOk())
                .andExpect(content().string("Email verified successfully!"));

        // Verify email verification in database
        User verifiedUser = userRepository.findByUsername(testUsername).get();
        assertTrue(verifiedUser.isEmailVerified());
        assertNull(verifiedUser.getVerificationToken());

        // Step 6: Login successfully
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.brandId").value(testBrandId))
                .andExpect(jsonPath("$.expirationTime").exists())
                .andReturn();

        // Extract tokens for further testing
        String responseJson = loginResult.getResponse().getContentAsString();
        Map<String, Object> loginResponse = objectMapper.readValue(responseJson, Map.class);
        jwtToken = (String) loginResponse.get("token");
        refreshToken = (String) loginResponse.get("refreshToken");

        assertNotNull(jwtToken);
        assertNotNull(refreshToken);
    }

    @Test
    @Order(2)
    void userProfileManagementFlow() throws Exception {
        // First complete registration and login
        completeUserRegistrationAndLoginFlow();

        // Step 1: Get user profile
        mockMvc.perform(get("/user/profile")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUsername))
                .andExpect(jsonPath("$.email").value(testEmail))
                .andExpect(jsonPath("$.firstName").value("Integration"))
                .andExpect(jsonPath("$.lastName").value("User"));

        // Step 2: Update user profile
        // First get the user's ID
        User currentUser = userRepository.findByUsername(testUsername).get();
        UserProfileUpdateRequestDTO updateRequest = UserProfileUpdateRequestDTO.builder()
            .id(currentUser.getId()) // Using the actual user ID
            .firstName("Updated Integration")
            .surname("Updated User")
            .phoneNumber("+9876543210")
            .city("Updated City")
            .username(testUsername)
            .build();

        mockMvc.perform(put("/user/profile")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated Integration"))
                .andExpect(jsonPath("$.lastName").value("Updated User"))
                .andExpect(jsonPath("$.phoneNumber").value("+9876543210"));

        // Step 3: Verify profile update in database
        User updatedUser = userRepository.findByUsername(testUsername).get();
        assertEquals("Updated Integration", updatedUser.getFirstName());
        assertEquals("Updated User", updatedUser.getLastName());
        assertEquals("+9876543210", updatedUser.getPhoneNumber());
    }

    @Test
    @Order(3)
    void passwordResetFlow() throws Exception {
        // First complete registration and login
        completeUserRegistrationAndLoginFlow();

        // Step 1: Request password reset
        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest(testEmail);
        mockMvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset instructions sent to your email"));

        // Step 2: Use a mock reset token (in real scenario, this would be sent via email)
        String resetToken = "mock-reset-token-123";

        // Step 3: Reset password
        ResetPasswordConfirmRequest resetRequest = new ResetPasswordConfirmRequest(resetToken, "newPassword123");
        mockMvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"));

        // Step 4: Try to login with old password (should fail)
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        // Step 5: Login with new password
        AuthRequest newAuthRequest = new AuthRequest(testUsername, "newPassword123", testBrandId);
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAuthRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @Order(4)
    void tokenRefreshFlow() throws Exception {
        // First complete registration and login
        completeUserRegistrationAndLoginFlow();

        // Step 1: Use refresh token to get new access token
        Map<String, String> refreshRequest = new HashMap<>();
        refreshRequest.put("refreshToken", refreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        // Step 2: Extract new tokens
        String responseJson = refreshResult.getResponse().getContentAsString();
        Map<String, Object> refreshResponse = objectMapper.readValue(responseJson, Map.class);
        String newJwtToken = (String) refreshResponse.get("token");
        String newRefreshToken = (String) refreshResponse.get("refreshToken");

        assertNotNull(newJwtToken);
        assertNotNull(newRefreshToken);
        assertNotEquals(jwtToken, newJwtToken); // Should be different tokens

        // Step 3: Use new token to access protected resource
        mockMvc.perform(get("/user/profile")
                .header("Authorization", "Bearer " + newJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUsername));
    }

    @Test
    @Order(5)
    void twoFactorAuthenticationFlow() throws Exception {
        // First complete registration and login
        completeUserRegistrationAndLoginFlow();

        // Step 1: Generate TFA secret
        mockMvc.perform(post("/user/tfa/generate")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").exists())
                .andExpect(jsonPath("$.qrCodeUrl").exists());

        // Step 2: Generate QR code
        mockMvc.perform(get("/user/tfa/qr-code")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));

        // Step 3: Enable TFA with verification code (mock valid code)
        // In real scenario, user would scan QR code and enter the code from their app
        TfaRequest tfaRequest = new TfaRequest(testUsername, "123456"); // Mock verification code
        
        // Mock the TFA verification to return true for testing
        mockMvc.perform(post("/user/tfa/enable")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tfaRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("TFA enabled successfully"));

        // Step 4: Verify TFA is enabled
        mockMvc.perform(get("/user/tfa/status")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Step 5: Disable TFA
        mockMvc.perform(post("/user/tfa/disable")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tfaRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("TFA disabled successfully"));

        // Step 6: Verify TFA is disabled
        mockMvc.perform(get("/user/tfa/status")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @Order(6)
    void logoutFlow() throws Exception {
        // First complete registration and login
        completeUserRegistrationAndLoginFlow();

        // Step 1: Logout
        Map<String, String> logoutRequest = new HashMap<>();
        logoutRequest.put("refreshToken", refreshToken);

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Step 2: Try to access protected resource with logged out token (should fail)
        mockMvc.perform(get("/user/profile")
                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isUnauthorized());

        // Step 3: Try to use refresh token after logout (should fail)
        Map<String, String> refreshRequest = new HashMap<>();
        refreshRequest.put("refreshToken", refreshToken);

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    @Order(7)
    void duplicateRegistrationFlow() throws Exception {
        // First complete registration
        completeUserRegistrationAndLoginFlow();

        // Step 1: Try to register with same username (should fail)
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists for this brand"));

        // Step 2: Try to register with same email (should fail)
        RegisterRequest duplicateEmailRequest = new RegisterRequest(
            "differentuser",
            "password123",
            testEmail, // Same email
            "Different",
            "User",
            "+1234567890",
            "Test City",
            testBrandId
        );

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateEmailRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists for this brand"));

        // Step 3: Register with same username but different brand (should succeed)
        RegisterRequest differentBrandRequest = new RegisterRequest(
            testUsername, // Same username
            "password123",
            "different@example.com",
            "Different",
            "User",
            "+1234567890",
            "Test City",
            "different-brand" // Different brand
        );

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(differentBrandRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully. Please verify your email."))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(8)
    void resendVerificationEmailFlow() throws Exception {
        // Step 1: Register user
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // Step 2: Resend verification email
        CheckEmailRequest resendRequest = new CheckEmailRequest(testEmail, testBrandId);
        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent successfully"));

        // Step 3: Try to resend for already verified user
        // First verify the user
        User user = userRepository.findByEmailAndBrandId(testEmail, testBrandId).get();
        String verificationToken = user.getVerificationToken();
        mockMvc.perform(get("/auth/verify-email")
                .param("token", verificationToken))
                .andExpect(status().isOk());

        // Then try to resend verification
        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already verified"));
    }
}