package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiKeyController.class)
@Disabled("Skipping API Key Controller tests for now")
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiKeyService apiKeyService;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiKeyCreateRequestDTO validRequest;
    private ApiKeyGeneratedResponseDTO generatedResponse;
    private ApiKeyResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        validRequest = ApiKeyCreateRequestDTO.builder()
                .name("Test API Key")
                .description("Test description")
                .prefix("sk-")
                .rateLimitTier("BASIC")
                .scopes(Arrays.asList("READ_USERS", "READ_BRANDS"))
                .build();

        generatedResponse = ApiKeyGeneratedResponseDTO.builder()
                .id(UUID.randomUUID())
                .name("Test API Key")
                .keyValue("sk-generated_key_value")
                .build();

        responseDTO = ApiKeyResponseDTO.builder()
                .id(UUID.randomUUID())
                .name("Test API Key")
                .description("Test description")
                .prefix("sk-")
                .isActive(true)
                .rateLimitTier("BASIC")
                .scopes(Arrays.asList("READ_USERS", "READ_BRANDS"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "testuser")
    void createApiKey_ValidRequest_ReturnsCreated() throws Exception {
        // Arrange
        when(apiKeyService.createApiKey(eq("testuser"), any(ApiKeyCreateRequestDTO.class)))
                .thenReturn(generatedResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/api-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test API Key"))
                .andExpect(jsonPath("$.keyValue").value("sk-generated_key_value"))
                .andExpect(jsonPath("$.id").exists());

        verify(apiKeyService).createApiKey(eq("testuser"), any(ApiKeyCreateRequestDTO.class));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createApiKey_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Arrange
        validRequest.setName(""); // Invalid empty name

        // Act & Assert
        mockMvc.perform(post("/api/v1/api-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(apiKeyService);
    }

    @Test
    @WithMockUser(username = "testuser")
    void createApiKey_ServiceThrowsException_ReturnsBadRequest() throws Exception {
        // Arrange
        when(apiKeyService.createApiKey(eq("testuser"), any(ApiKeyCreateRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("User not found"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/api-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User not found"));

        verify(apiKeyService).createApiKey(eq("testuser"), any(ApiKeyCreateRequestDTO.class));
    }

    @Test
    void createApiKey_Unauthenticated_ReturnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/api-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(apiKeyService);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getApiKeysForCurrentUser_ValidUser_ReturnsKeys() throws Exception {
        // Arrange
        List<ApiKeyResponseDTO> apiKeys = Arrays.asList(responseDTO);
        when(apiKeyService.getApiKeysForUser("testuser")).thenReturn(apiKeys);

        // Act & Assert
        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Test API Key"))
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(apiKeyService).getApiKeysForUser("testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void getApiKeyById_ValidKey_ReturnsKey() throws Exception {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.getApiKeyByIdForUser(keyId, "testuser")).thenReturn(Optional.of(responseDTO));

        // Act & Assert
        mockMvc.perform(get("/api/v1/api-keys/{keyId}", keyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test API Key"))
                .andExpect(jsonPath("$.isActive").value(true));

        verify(apiKeyService).getApiKeyByIdForUser(keyId, "testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void getApiKeyById_KeyNotFound_ReturnsNotFound() throws Exception {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.getApiKeyByIdForUser(keyId, "testuser")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/api-keys/{keyId}", keyId))
                .andExpect(status().isNotFound());

        verify(apiKeyService).getApiKeyByIdForUser(keyId, "testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void revokeApiKey_ValidKey_ReturnsNoContent() throws Exception {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.revokeApiKey(keyId, "testuser")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/api-keys/{keyId}/revoke", keyId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(apiKeyService).revokeApiKey(keyId, "testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void revokeApiKey_KeyNotFound_ReturnsNotFound() throws Exception {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.revokeApiKey(keyId, "testuser")).thenReturn(false);

        // Act & Assert
        mockMvc.perform(patch("/api/v1/api-keys/{keyId}/revoke", keyId)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(apiKeyService).revokeApiKey(keyId, "testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteApiKey_ValidKey_ReturnsNoContent() throws Exception {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.deleteApiKey(keyId, "testuser")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/api-keys/{keyId}", keyId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(apiKeyService).deleteApiKey(keyId, "testuser");
    }

    @Test
    @WithMockUser(username = "testuser")
    void deleteApiKey_KeyNotFound_ReturnsNotFound() throws Exception {
        // Arrange
        UUID keyId = UUID.randomUUID();
        when(apiKeyService.deleteApiKey(keyId, "testuser")).thenReturn(false);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/api-keys/{keyId}", keyId)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(apiKeyService).deleteApiKey(keyId, "testuser");
    }
}