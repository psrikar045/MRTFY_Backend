package com.example.jwtauthenticator.config;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.service.ApiKeyService;
import com.example.jwtauthenticator.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Disabled("Dynamic Authentication Strategy tests - remove @Disabled when needed")
class DynamicAuthenticationStrategyTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    private DynamicAuthenticationStrategy authStrategy;

    @BeforeEach
    void setUp() {
        authStrategy = new DynamicAuthenticationStrategy(apiKeyService, jwtUtil);
        
        // Set default configuration values
        ReflectionTestUtils.setField(authStrategy, "authMethod", "both");
        ReflectionTestUtils.setField(authStrategy, "apiKeyHeader", "X-API-Key");
        ReflectionTestUtils.setField(authStrategy, "jwtHeader", "Authorization");
        ReflectionTestUtils.setField(authStrategy, "allowFallback", true);
        ReflectionTestUtils.setField(authStrategy, "apiKeyPrefix", "");
        ReflectionTestUtils.setField(authStrategy, "jwtPrefix", "Bearer ");
    }

    @Test
    void testApiKeyAuthentication_Success() {
        // Arrange
        String apiKeyValue = "sk-test-api-key-123";
        ApiKey mockApiKey = createMockApiKey();
        
        when(request.getHeader("X-API-Key")).thenReturn(apiKeyValue);
        when(apiKeyService.validateApiKey(apiKeyValue)).thenReturn(Optional.of(mockApiKey));

        // Act
        DynamicAuthenticationStrategy.AuthenticationResult result = authStrategy.authenticate(request);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("user123", result.getUserId());
        assertEquals(DynamicAuthenticationStrategy.AuthMethod.API_KEY, result.getMethod());
        assertEquals(mockApiKey, result.getApiKey());
    }

    @Test
    void testApiKeyAuthentication_InvalidKey() {
        // Arrange
        String apiKeyValue = "invalid-api-key";
        
        when(request.getHeader("X-API-Key")).thenReturn(apiKeyValue);
        when(apiKeyService.validateApiKey(apiKeyValue)).thenReturn(Optional.empty());

        // Act
        DynamicAuthenticationStrategy.AuthenticationResult result = authStrategy.authenticate(request);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Invalid API key", result.getMessage());
        assertEquals(DynamicAuthenticationStrategy.AuthMethod.API_KEY, result.getMethod());
    }

    @Test
    void testJwtAuthentication_Success() {
        // Arrange
        String jwtToken = "valid-jwt-token";
        String authHeader = "Bearer " + jwtToken;
        
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtUtil.isTokenExpired(jwtToken)).thenReturn(false);
        when(jwtUtil.extractUserId(jwtToken)).thenReturn("user456");

        // Act
        DynamicAuthenticationStrategy.AuthenticationResult result = authStrategy.authenticate(request);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("user456", result.getUserId());
        assertEquals(DynamicAuthenticationStrategy.AuthMethod.JWT, result.getMethod());
        assertNull(result.getApiKey());
    }

    @Test
    void testJwtAuthentication_ExpiredToken() {
        // Arrange
        String jwtToken = "expired-jwt-token";
        String authHeader = "Bearer " + jwtToken;
        
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(authHeader);
        when(jwtUtil.isTokenExpired(jwtToken)).thenReturn(true);

        // Act
        DynamicAuthenticationStrategy.AuthenticationResult result = authStrategy.authenticate(request);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("JWT token has expired", result.getMessage());
        assertEquals(DynamicAuthenticationStrategy.AuthMethod.JWT, result.getMethod());
    }

    @Test
    void testNoAuthentication_BothMethodsFail() {
        // Arrange
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        DynamicAuthenticationStrategy.AuthenticationResult result = authStrategy.authenticate(request);

        // Assert
        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
    }

    @Test
    void testApiKeyOnlyMode() {
        // Arrange
        ReflectionTestUtils.setField(authStrategy, "authMethod", "api_key");
        String jwtToken = "valid-jwt-token";
        String authHeader = "Bearer " + jwtToken;
        
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(authHeader);

        // Act
        DynamicAuthenticationStrategy.AuthenticationResult result = authStrategy.authenticate(request);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("API key not found", result.getMessage());
    }

    @Test
    void testJwtOnlyMode() {
        // Arrange
        ReflectionTestUtils.setField(authStrategy, "authMethod", "jwt");
        String apiKeyValue = "sk-test-api-key-123";
        
        when(request.getHeader("X-API-Key")).thenReturn(apiKeyValue);
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        DynamicAuthenticationStrategy.AuthenticationResult result = authStrategy.authenticate(request);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("JWT token not found", result.getMessage());
    }

    @Test
    void testAuthConfig() {
        // Act
        DynamicAuthenticationStrategy.AuthConfig config = authStrategy.getAuthConfig();

        // Assert
        assertNotNull(config);
        assertEquals("both", config.getAuthMethod());
        assertEquals("X-API-Key", config.getApiKeyHeader());
        assertEquals("Authorization", config.getJwtHeader());
        assertTrue(config.isAllowFallback());
        assertEquals("", config.getApiKeyPrefix());
        assertEquals("Bearer ", config.getJwtPrefix());
        assertNotNull(config.getEnabledMethods());
    }

    @Test
    void testIsApiKeyAuthEnabled() {
        // Test both mode
        assertTrue(authStrategy.isApiKeyAuthEnabled());
        
        // Test API key only mode
        ReflectionTestUtils.setField(authStrategy, "authMethod", "api_key");
        assertTrue(authStrategy.isApiKeyAuthEnabled());
        
        // Test JWT only mode
        ReflectionTestUtils.setField(authStrategy, "authMethod", "jwt");
        assertFalse(authStrategy.isApiKeyAuthEnabled());
    }

    @Test
    void testIsJwtAuthEnabled() {
        // Test both mode
        assertTrue(authStrategy.isJwtAuthEnabled());
        
        // Test JWT only mode
        ReflectionTestUtils.setField(authStrategy, "authMethod", "jwt");
        assertTrue(authStrategy.isJwtAuthEnabled());
        
        // Test API key only mode
        ReflectionTestUtils.setField(authStrategy, "authMethod", "api_key");
        assertFalse(authStrategy.isJwtAuthEnabled());
    }

    private ApiKey createMockApiKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID());
        apiKey.setUserFkId("user123");
        apiKey.setName("Test API Key");
        apiKey.setActive(true);
        apiKey.setRateLimitTier("BASIC");
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());
        return apiKey;
    }
}