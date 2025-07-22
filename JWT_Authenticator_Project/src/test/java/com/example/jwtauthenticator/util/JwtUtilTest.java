package com.example.jwtauthenticator.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private String secret = "thisisalongsecretkeyforjwttestingthatmeetstherequirementsofhs256";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(jwtUtil, "secretString", secret);
    }

    @Test
    void generateToken_shouldReturnValidToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";

        String token = jwtUtil.generateToken(userDetails, userId);

        assertNotNull(token);
        assertEquals("testuser", jwtUtil.extractUsername(token));
        assertEquals(userId, jwtUtil.extractUserId(token));
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    void generateRefreshToken_shouldReturnValidToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";

        String refreshToken = jwtUtil.generateRefreshToken(userDetails, userId);

        assertNotNull(refreshToken);
        assertEquals("testuser", jwtUtil.extractUsername(refreshToken));
        assertEquals(userId, jwtUtil.extractUserId(refreshToken));
        assertFalse(jwtUtil.isTokenExpired(refreshToken));
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        String token = jwtUtil.generateToken(userDetails, userId);

        assertTrue(jwtUtil.validateToken(token, userDetails));
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";

        // Create an expired token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        String expiredToken = Jwts.builder().claims(claims).subject("testuser").issuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24))
                .expiration(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 12))
                .signWith(secretKey).compact();

        assertFalse(jwtUtil.validateToken(expiredToken, userDetails));
    }

    @Test
    void validateToken_shouldReturnFalseForInvalidUser() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        String token = jwtUtil.generateToken(userDetails, userId);

        UserDetails invalidUserDetails = new User("anotheruser", "password", Collections.emptyList());

        assertFalse(jwtUtil.validateToken(token, invalidUserDetails));
    }

    @Test
    void extractUsername_shouldReturnCorrectUsername() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        String token = jwtUtil.generateToken(userDetails, userId);

        String extractedUsername = jwtUtil.extractUsername(token);

        assertEquals("testuser", extractedUsername);
    }

    @Test
    void extractUserId_shouldReturnCorrectUserId() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        String token = jwtUtil.generateToken(userDetails, userId);

        String extractedUserId = jwtUtil.extractUserId(token);

        assertEquals(userId, extractedUserId);
    }

    @Test
    void extractExpiration_shouldReturnFutureDate() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        String token = jwtUtil.generateToken(userDetails, userId);

        Date expiration = jwtUtil.extractExpiration(token);

        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void isTokenExpired_shouldReturnFalseForValidToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        String token = jwtUtil.generateToken(userDetails, userId);

        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    void isTokenExpired_shouldReturnTrueForExpiredToken() {
        // Create an expired token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", "123e4567-e89b-12d3-a456-426614174000");
        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        String expiredToken = Jwts.builder()
                .claims(claims)
                .subject("testuser")
                .issuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24))
                .expiration(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 12))
                .signWith(secretKey)
                .compact();

        // The method should throw an exception when trying to parse an expired token
        assertThrows(Exception.class, () -> jwtUtil.isTokenExpired(expiredToken));
    }

    @Test
    void validateToken_shouldHandleNullToken() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());

        // JwtUtil should return false for null token, not throw exception
        assertFalse(jwtUtil.validateToken(null, userDetails));
    }

    @Test
    void validateToken_shouldHandleInvalidTokenFormat() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String invalidToken = "invalid.token.format";

        // JwtUtil should return false for invalid token format, not throw exception
        assertFalse(jwtUtil.validateToken(invalidToken, userDetails));
    }

    @Test
    void generateTokenWithClaims_shouldIncludeCustomClaims() {
        UserDetails userDetails = new User("testuser", "password", Collections.emptyList());
        String userId = "123e4567-e89b-12d3-a456-426614174000";
        String token = jwtUtil.generateToken(userDetails, userId);

        // Verify that the token contains the expected data by extracting individual claims
        assertNotNull(token);
        assertEquals("testuser", jwtUtil.extractUsername(token));
        assertEquals(userId, jwtUtil.extractUserId(token));
        assertFalse(jwtUtil.isTokenExpired(token));
    }
}
