package com.example.jwtauthenticator.util;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.entity.PasswordResetToken;
import com.example.jwtauthenticator.model.AuthRequest;
import com.example.jwtauthenticator.model.RegisterRequest;
import com.example.jwtauthenticator.dto.GoogleSignInRequest;
import com.example.jwtauthenticator.dto.ForgotPasswordRequest;
import com.example.jwtauthenticator.dto.ResetPasswordConfirmRequest;
import com.example.jwtauthenticator.dto.CheckUsernameRequest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

/**
 * Factory class for creating test data objects
 */
public class TestDataFactory {

    // Default test constants
    public static final String DEFAULT_USERNAME = "testuser";
    public static final String DEFAULT_PASSWORD = "password123";
    public static final String DEFAULT_EMAIL = "test@example.com";
    public static final String DEFAULT_BRAND_ID = "test-brand";
    public static final String DEFAULT_FIRST_NAME = "John";
    public static final String DEFAULT_LAST_NAME = "Doe";
    public static final String DEFAULT_PHONE_NUMBER = "+1234567890";
    public static final String DEFAULT_LOCATION = "New York";

    /**
     * Creates a valid test User entity
     */
    public static User createTestUser() {
        return User.builder()
                .id("DOMBR000001")
                .userId(UUID.randomUUID())
                .username(DEFAULT_USERNAME)
                .password("$2a$10$encodedPassword") // BCrypt encoded
                .email(DEFAULT_EMAIL)
                .firstName(DEFAULT_FIRST_NAME)
                .lastName(DEFAULT_LAST_NAME)
                .phoneNumber(DEFAULT_PHONE_NUMBER)
                .role(User.Role.USER)
                .authProvider(User.AuthProvider.LOCAL)
                .emailVerified(true)
                .tfaEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Creates a test User with custom email verification status
     */
    public static User createTestUser(boolean emailVerified) {
        User user = createTestUser();
        user.setEmailVerified(emailVerified);
        if (!emailVerified) {
            user.setVerificationToken(UUID.randomUUID().toString());
        }
        return user;
    }

    /**
     * Creates a test User with custom parameters
     */
    public static User createTestUser(String username, String email) {
        User user = createTestUser();
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    /**
     * Creates a test RegisterRequest
     */
    public static RegisterRequest createRegisterRequest() {
        return new RegisterRequest(
                DEFAULT_USERNAME,
                DEFAULT_PASSWORD,
                DEFAULT_EMAIL,
                DEFAULT_FIRST_NAME,
                DEFAULT_LAST_NAME,
                DEFAULT_PHONE_NUMBER,
                DEFAULT_LOCATION,
                DEFAULT_BRAND_ID
        );
    }

    /**
     * Creates a test RegisterRequest with custom parameters
     */
    public static RegisterRequest createRegisterRequest(String username, String email, String brandId) {
        return new RegisterRequest(
                username,
                DEFAULT_PASSWORD,
                email,
                DEFAULT_FIRST_NAME,
                DEFAULT_LAST_NAME,
                DEFAULT_PHONE_NUMBER,
                DEFAULT_LOCATION,
                brandId
        );
    }

    /**
     * Creates a test AuthRequest
     */
    public static AuthRequest createAuthRequest() {
        return new AuthRequest(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_BRAND_ID);
    }

    /**
     * Creates a test AuthRequest with custom parameters
     */
    public static AuthRequest createAuthRequest(String username, String password, String brandId) {
        return new AuthRequest(username, password, brandId);
    }

    /**
     * Creates a test PasswordResetToken
     */
    public static PasswordResetToken createPasswordResetToken() {
        return PasswordResetToken.builder()
                .id(1L)
                .token("test-reset-token")
                .user(createTestUser())
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    /**
     * Creates a test PasswordResetToken with custom user
     */
    public static PasswordResetToken createPasswordResetToken(User user) {
        return PasswordResetToken.builder()
                .id(1L)
                .token("test-reset-token")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    /**
     * Creates a list of invalid passwords for testing
     */
    public static List<String> getInvalidPasswords() {
        return List.of(
            "short", // Too short
            "nouppercase123", // No uppercase
            "NOLOWERCASE123", // No lowercase
            "NoNumbers", // No numbers
            "a".repeat(200) // Too long
        );
    }

    /**
     * Creates a test JWT token (for testing purposes)
     */
    public static String createTestJwtToken() {
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImJyYW5kSWQiOiJ0ZXN0LWJyYW5kIiwiaWF0IjoxNTE2MjM5MDIyfQ.test-signature";
    }

    /**
     * Creates a test refresh token
     */
    public static String createTestRefreshToken() {
        return "refresh-token-" + UUID.randomUUID().toString();
    }

    /**
     * Creates a test verification token
     */
    public static String createTestVerificationToken() {
        return "verification-token-" + UUID.randomUUID().toString();
    }

    /**
     * Creates a test password reset token
     */
    public static String createTestPasswordResetToken() {
        return "password-reset-token-" + UUID.randomUUID().toString();
    }

    /**
     * Creates a test GoogleSignInRequest
     */
    public static GoogleSignInRequest createGoogleSignInRequest() {
        return new GoogleSignInRequest("google-id-token-" + UUID.randomUUID().toString());
    }

    /**
     * Creates a test ForgotPasswordRequest
     */
    public static ForgotPasswordRequest createForgotPasswordRequest() {
        return new ForgotPasswordRequest(DEFAULT_EMAIL);
    }

    /**
     * Creates a test ResetPasswordConfirmRequest
     */
    public static ResetPasswordConfirmRequest createResetPasswordConfirmRequest() {
        return new ResetPasswordConfirmRequest(
            "reset-token-" + UUID.randomUUID().toString(),
            "newPassword123"
        );
    }

    /**
     * Creates a test CheckUsernameRequest
     */
    public static CheckUsernameRequest createCheckUsernameRequest() {
        return new CheckUsernameRequest(DEFAULT_USERNAME, DEFAULT_BRAND_ID);
    }

    /**
     * Creates a test CheckUsernameRequest with custom values
     */
    public static CheckUsernameRequest createCheckUsernameRequest(String username, String brandId) {
        return new CheckUsernameRequest(username, brandId);
    }

    /**
     * Creates an oversized string for testing edge cases
     */
    public static String createOversizedString(int length) {
        StringBuilder sb = new StringBuilder();
        String pattern = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            sb.append(pattern.charAt(i % pattern.length()));
        }
        return sb.toString();
    }

    /**
     * Creates a string with XSS attack patterns for testing
     */
    public static String createXssString() {
        return "<script>alert('XSS')</script>";
    }
}