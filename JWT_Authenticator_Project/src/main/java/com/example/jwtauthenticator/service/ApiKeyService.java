package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyUpdateRequestDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User; // Import User entity
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository; // Import UserRepository
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository; // Inject UserRepository to fetch User by String ID
    private final ApiKeyHashUtil apiKeyHashUtil;

    @Autowired
    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository, ApiKeyHashUtil apiKeyHashUtil) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository; // Initialize UserRepository
        this.apiKeyHashUtil = apiKeyHashUtil;
    }

    private String generateSecureApiKey(String prefix) {
        return this.apiKeyHashUtil.generateSecureApiKey(prefix);
    }

    @Transactional
    public ApiKeyGeneratedResponseDTO createApiKey(String userFkId, ApiKeyCreateRequestDTO request) {
        // Optional: Validate if the userFkId exists in the User table's 'id' column
        userRepository.findById(userFkId) // findById uses the PK, which is 'id' (String)
                      .orElseThrow(() -> new IllegalArgumentException("User with ID " + userFkId + " not found."));

        String generatedKeyValue = generateSecureApiKey(request.getPrefix());
        String keyHash = apiKeyHashUtil.hashApiKey(generatedKeyValue); // Hash the key for storage

        ApiKey apiKey = ApiKey.builder()
                .userFkId(userFkId) // Set the String user ID
                .keyHash(keyHash) // Store the hash instead of plain key
                .name(request.getName())
                .description(request.getDescription())
                .prefix(request.getPrefix())
                .isActive(true)
                .expiresAt(request.getExpiresAt())
                .allowedIps(request.getAllowedIps() != null ? String.join(",", request.getAllowedIps()) : null)
                .allowedDomains(request.getAllowedDomains() != null ? String.join(",", request.getAllowedDomains()) : null)
                .rateLimitTier(request.getRateLimitTier())
                .build();

        ApiKey savedKey = apiKeyRepository.save(apiKey);

        return ApiKeyGeneratedResponseDTO.builder()
                .id(savedKey.getId())
                .name(savedKey.getName())
                .keyValue(generatedKeyValue)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponseDTO> getApiKeysForUser(String userFkId) {
        return apiKeyRepository.findByUserFkId(userFkId) // Use the updated repository method
                .stream()
                .map(ApiKeyResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ApiKeyResponseDTO> getApiKeyByIdForUser(UUID keyId, String userFkId) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userFkId) // Use the updated repository method
                .map(ApiKeyResponseDTO::fromEntity);
    }

    @Transactional
    public Optional<ApiKeyResponseDTO> updateApiKey(UUID keyId, String userFkId, ApiKeyUpdateRequestDTO request) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userFkId).map(existingKey -> { // Use updated method
            if (request.getName() != null) {
                existingKey.setName(request.getName());
            }
            if (request.getDescription() != null) {
                existingKey.setDescription(request.getDescription());
            }
            if (request.getIsActive() != null) {
                existingKey.setActive(request.getIsActive());
            }
            if (request.getExpiresAt() != null) {
                existingKey.setExpiresAt(request.getExpiresAt());
            }
            if (request.getAllowedIps() != null) {
                existingKey.setAllowedIps(String.join(",", request.getAllowedIps()));
            }
            if (request.getAllowedDomains() != null) {
                existingKey.setAllowedDomains(String.join(",", request.getAllowedDomains()));
            }
            if (request.getRateLimitTier() != null) {
                existingKey.setRateLimitTier(request.getRateLimitTier());
            }
            return ApiKeyResponseDTO.fromEntity(apiKeyRepository.save(existingKey));
        });
    }

    @Transactional
    public boolean revokeApiKey(UUID keyId, String userFkId) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userFkId).map(key -> { // Use updated method
            key.setActive(false);
            key.setRevokedAt(LocalDateTime.now());
            apiKeyRepository.save(key);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean deleteApiKey(UUID keyId, String userFkId) {
        Optional<ApiKey> apiKey = apiKeyRepository.findByIdAndUserFkId(keyId, userFkId); // Use updated method
        if (apiKey.isPresent()) {
            apiKeyRepository.delete(apiKey.get());
            return true;
        }
        return false;
    }

    /**
     * Core validation method for incoming API calls using key hash.
     * This will be used in your API key authentication filter.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> validateApiKey(String plainTextKey) {
        if (!apiKeyHashUtil.isValidApiKeyFormat(plainTextKey)) {
            return Optional.empty();
        }
        
        String keyHash = apiKeyHashUtil.hashApiKey(plainTextKey);
        return apiKeyRepository.findByKeyHash(keyHash)
                .filter(ApiKey::isActive)
                .filter(key -> key.getRevokedAt() == null)
                .filter(key -> key.getExpiresAt() == null || key.getExpiresAt().isAfter(LocalDateTime.now()));
    }
    
    /**
     * Find API key by key hash.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> findByKeyHash(String keyHash) {
        return apiKeyRepository.findByKeyHash(keyHash);
    }
    
    /**
     * Update last used timestamp for an API key.
     */
    @Transactional
    public void updateLastUsed(UUID keyId) {
        apiKeyRepository.findById(keyId).ifPresent(key -> {
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(key);
        });
    }

    /**
     * Fetches the User entity associated with a given API key's userFkId.
     * This is useful in the API key authentication filter to get the User principal.
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByApiKey(ApiKey apiKey) {
        return userRepository.findById(apiKey.getUserFkId()); // Use findById (which uses String PK)
    }
}
