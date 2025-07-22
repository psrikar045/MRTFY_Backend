package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.CheckUsernameRequest;
import com.example.jwtauthenticator.dto.SimpleCheckUsernameRequest;
import com.example.jwtauthenticator.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class AuthServiceUsernameCheckTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCheckUsernameExists_WithBrandId_Exists() {
        // Given
        CheckUsernameRequest request = new CheckUsernameRequest("existingUser", "brand1");
        when(userRepository.existsByUsername("existingUser")).thenReturn(true);

        // When
        boolean result = authService.checkUsernameExists(request);

        // Then
        assertTrue(result);
    }

    @Test
    public void testCheckUsernameExists_WithBrandId_NotExists() {
        // Given
        CheckUsernameRequest request = new CheckUsernameRequest("newUser", "brand1");
        when(userRepository.existsByUsername("newUser")).thenReturn(false);

        // When
        boolean result = authService.checkUsernameExists(request);

        // Then
        assertFalse(result);
    }

    @Test
    public void testCheckUsernameExists_Simple_Exists() {
        // Given
        SimpleCheckUsernameRequest request = new SimpleCheckUsernameRequest("existingUser");
        when(userRepository.existsByUsername("existingUser")).thenReturn(true);

        // When
        boolean result = authService.checkUsernameExists(request);

        // Then
        assertTrue(result);
    }

    @Test
    public void testCheckUsernameExists_Simple_NotExists() {
        // Given
        SimpleCheckUsernameRequest request = new SimpleCheckUsernameRequest("newUser");
        when(userRepository.existsByUsername("newUser")).thenReturn(false);

        // When
        boolean result = authService.checkUsernameExists(request);

        // Then
        assertFalse(result);
    }

    @Test
    public void testCheckUsernameExists_String_Exists() {
        // Given
        String username = "existingUser";
        when(userRepository.existsByUsername(username)).thenReturn(true);

        // When
        boolean result = authService.checkUsernameExists(username);

        // Then
        assertTrue(result);
    }

    @Test
    public void testCheckUsernameExists_String_NotExists() {
        // Given
        String username = "newUser";
        when(userRepository.existsByUsername(username)).thenReturn(false);

        // When
        boolean result = authService.checkUsernameExists(username);

        // Then
        assertFalse(result);
    }
}