package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled("Skipping API Key Service tests for now")
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApiKeyHashUtil apiKeyHashUtil;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private User testUser;
    private ApiKeyCreateRequestDTO validRequest;
    private ApiKey testApiKey;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("TEST_USER_123")
                .username("testuser")
                .email("test@example.com")
                .build();

        validRequest = ApiKeyCreateRequestDTO.builder()
                .name("Test API Key")
                .description("Test description")
                .prefix("sk-")
                .rateLimitTier("BASIC")
                .scopes(Arrays.asList("READ_USERS", "READ_BRANDS"))
                .build();

        testApiKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .userFkId("TEST_USER_123")
                .keyHash("hashed_key_value")
                .name("Test API Key")
                .description("Test description")
                .prefix("sk-")
                .isActive(true)
                .rateLimitTier("BASIC")
                .scopes("READ_USERS,READ_BRANDS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createApiKey_ValidRequest_Success() {
        // Arrange
        when(userRepository.findById("TEST_USER_123")).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.existsByNameAndUserFkId("Test API Key", "TEST_USER_123")).thenReturn(false);
        when(apiKeyRepository.findByUserFkId("TEST_USER_123")).thenReturn(Arrays.asList());
        when(apiKeyHashUtil.generateSecureApiKey("sk-")).thenReturn("sk-generated_key_value");
        when(apiKeyHashUtil.hashApiKey("sk-generated_key_value")).thenReturn("hashed_key_value");
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // Act
        ApiKeyGeneratedResponseDTO result = apiKeyService.createApiKey("TEST_USER_123", validRequest);

        // Assert
        assertNotNull(result);
        assertEquals("Test API Key", result.getName());
        assertEquals("sk-generated_key_value", result.getKeyValue());
        assertNotNull(result.getId());

        verify(userRepository).findById("TEST_USER_123");
        verify(apiKeyRepository).existsByNameAndUserFkId("Test API Key", "TEST_USER_123");
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void createApiKey_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findById("INVALID_USER")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("INVALID_USER", validRequest)
        );

        assertEquals("User with ID INVALID_USER not found.", exception.getMessage());
        verify(userRepository).findById("INVALID_USER");
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void createApiKey_NullUserId_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey(null, validRequest)
        );

        assertEquals("User ID cannot be null or empty", exception.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void createApiKey_EmptyUserId_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("   ", validRequest)
        );

        assertEquals("User ID cannot be null or empty", exception.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void createApiKey_NullRequest_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("TEST_USER_123", null)
        );

        assertEquals("API key request cannot be null", exception.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void createApiKey_EmptyName_ThrowsException() {
        // Arrange
        validRequest.setName("   ");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("TEST_USER_123", validRequest)
        );

        assertEquals("API key name cannot be null or empty", exception.getMessage());
        verifyNoInteractions(userRepository);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void createApiKey_DuplicateName_ThrowsException() {
        // Arrange
        when(userRepository.findById("TEST_USER_123")).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.existsByNameAndUserFkId("Test API Key", "TEST_USER_123")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("TEST_USER_123", validRequest)
        );

        assertEquals("API key with name 'Test API Key' already exists for this user", exception.getMessage());
        verify(userRepository).findById("TEST_USER_123");
        verify(apiKeyRepository).existsByNameAndUserFkId("Test API Key", "TEST_USER_123");
    }

    @Test
    void createApiKey_InvalidRateLimitTier_ThrowsException() {
        // Arrange
        validRequest.setRateLimitTier("INVALID_TIER");
        when(userRepository.findById("TEST_USER_123")).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.existsByNameAndUserFkId("Test API Key", "TEST_USER_123")).thenReturn(false);
        when(apiKeyRepository.findByUserFkId("TEST_USER_123")).thenReturn(Arrays.asList());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("TEST_USER_123", validRequest)
        );

        assertTrue(exception.getMessage().contains("Invalid rate limit tier: INVALID_TIER"));
    }

    @Test
    void createApiKey_InvalidScope_ThrowsException() {
        // Arrange
        validRequest.setScopes(Arrays.asList("INVALID_SCOPE"));
        when(userRepository.findById("TEST_USER_123")).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.existsByNameAndUserFkId("Test API Key", "TEST_USER_123")).thenReturn(false);
        when(apiKeyRepository.findByUserFkId("TEST_USER_123")).thenReturn(Arrays.asList());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("TEST_USER_123", validRequest)
        );

        assertTrue(exception.getMessage().contains("Invalid scope: INVALID_SCOPE"));
    }

    @Test
    void createApiKey_MaxApiKeysReached_ThrowsException() {
        // Arrange
        List<ApiKey> existingKeys = Arrays.asList(
                new ApiKey(), new ApiKey(), new ApiKey(), new ApiKey(), new ApiKey(),
                new ApiKey(), new ApiKey(), new ApiKey(), new ApiKey(), new ApiKey()
        );
        
        when(userRepository.findById("TEST_USER_123")).thenReturn(Optional.of(testUser));
        when(apiKeyRepository.existsByNameAndUserFkId("Test API Key", "TEST_USER_123")).thenReturn(false);
        when(apiKeyRepository.findByUserFkId("TEST_USER_123")).thenReturn(existingKeys);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> apiKeyService.createApiKey("TEST_USER_123", validRequest)
        );

        assertEquals("Maximum number of API keys (10) reached for this user", exception.getMessage());
    }

    @Test
    void getApiKeysForUser_ValidUser_ReturnsKeys() {
        // Arrange
        List<ApiKey> apiKeys = Arrays.asList(testApiKey);
        when(apiKeyRepository.findByUserFkId("TEST_USER_123")).thenReturn(apiKeys);

        // Act
        List<ApiKeyResponseDTO> result = apiKeyService.getApiKeysForUser("TEST_USER_123");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test API Key", result.get(0).getName());
        verify(apiKeyRepository).findByUserFkId("TEST_USER_123");
    }

    @Test
    void validateApiKey_ValidKey_ReturnsApiKey() {
        // Arrange
        String plainTextKey = "sk-valid_key";
        String keyHash = "hashed_key_value";
        
        when(apiKeyHashUtil.isValidApiKeyFormat(plainTextKey)).thenReturn(true);
        when(apiKeyHashUtil.hashApiKey(plainTextKey)).thenReturn(keyHash);
        when(apiKeyRepository.findByKeyHash(keyHash)).thenReturn(Optional.of(testApiKey));

        // Act
        Optional<ApiKey> result = apiKeyService.validateApiKey(plainTextKey);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testApiKey, result.get());
        verify(apiKeyHashUtil).isValidApiKeyFormat(plainTextKey);
        verify(apiKeyHashUtil).hashApiKey(plainTextKey);
        verify(apiKeyRepository).findByKeyHash(keyHash);
    }

    @Test
    void validateApiKey_InvalidFormat_ReturnsEmpty() {
        // Arrange
        String plainTextKey = "invalid_key";
        when(apiKeyHashUtil.isValidApiKeyFormat(plainTextKey)).thenReturn(false);

        // Act
        Optional<ApiKey> result = apiKeyService.validateApiKey(plainTextKey);

        // Assert
        assertTrue(result.isEmpty());
        verify(apiKeyHashUtil).isValidApiKeyFormat(plainTextKey);
        verifyNoMoreInteractions(apiKeyHashUtil);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void revokeApiKey_ValidKey_Success() {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findByIdAndUserFkId(keyId, "TEST_USER_123")).thenReturn(Optional.of(testApiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(testApiKey);

        // Act
        boolean result = apiKeyService.revokeApiKey(keyId, "TEST_USER_123");

        // Assert
        assertTrue(result);
        verify(apiKeyRepository).findByIdAndUserFkId(keyId, "TEST_USER_123");
        verify(apiKeyRepository).save(any(ApiKey.class));
    }

    @Test
    void revokeApiKey_KeyNotFound_ReturnsFalse() {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findByIdAndUserFkId(keyId, "TEST_USER_123")).thenReturn(Optional.empty());

        // Act
        boolean result = apiKeyService.revokeApiKey(keyId, "TEST_USER_123");

        // Assert
        assertFalse(result);
        verify(apiKeyRepository).findByIdAndUserFkId(keyId, "TEST_USER_123");
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }
}