package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyUpdateRequestDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User; // Import User entity
import com.example.jwtauthenticator.enums.ApiKeyScope;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository; // Import UserRepository
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
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
        // Input validation
        if (userFkId == null || userFkId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (request == null) {
            throw new IllegalArgumentException("API key request cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("API key name cannot be null or empty");
        }
        
        // Validate if the userFkId exists in the User table's 'id' column
        User user = userRepository.findById(userFkId) // Use findById for the primary key lookup
                      .orElseThrow(() -> new IllegalArgumentException("User with ID " + userFkId + " not found."));
      
        // Check for duplicate API key names for this user
        if (apiKeyRepository.existsByNameAndUserFkId(request.getName().trim(), userFkId)) {
            throw new IllegalArgumentException("API key with name '" + request.getName() + "' already exists for this user");
        }
        
        // Check API key limit per user (configurable limit)
        List<ApiKey> userApiKeys = apiKeyRepository.findByUserFkId(userFkId);
        int maxApiKeysPerUser = 10; // This could be made configurable
        if (userApiKeys.size() >= maxApiKeysPerUser) {
            throw new IllegalArgumentException("Maximum number of API keys (" + maxApiKeysPerUser + ") reached for this user");
        }
        
        // Validate rate limit tier if provided
        if (request.getRateLimitTier() != null && !request.getRateLimitTier().trim().isEmpty()) {
            try {
                RateLimitTier.valueOf(request.getRateLimitTier().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid rate limit tier: " + request.getRateLimitTier() + 
                    ". Valid values are: FREE_TIER, PRO_TIER, BUSINESS_TIER");
            }
        }
        
        // Validate scopes if provided
        if (request.getScopes() != null && !request.getScopes().isEmpty()) {
            for (String scope : request.getScopes()) {
                if (scope == null || scope.trim().isEmpty()) {
                    throw new IllegalArgumentException("Scope cannot be null or empty");
                }
                try {
                    ApiKeyScope.valueOf(scope.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid scope: " + scope + 
                        ". Please check available scopes in ApiKeyScope enum");
                }
            }
        }

        String generatedKeyValue = generateSecureApiKey(request.getPrefix());
        String keyHash = apiKeyHashUtil.hashApiKey(generatedKeyValue); // Hash the key for storage

        // Handle expiration date: if null, set to 1 year from now
        LocalDateTime expirationDate = request.getExpiresAt();
        if (expirationDate == null) {
            expirationDate = LocalDateTime.now().plusYears(1);
            log.info("No expiration date provided for API key '{}', setting default expiration to: {}", 
                    request.getName(), expirationDate);
        }

        ApiKey apiKey = ApiKey.builder()
                .userFkId(userFkId) // Set the String user ID
                .keyHash(keyHash) // Store the hash instead of plain key
                .name(request.getName().trim())
                .description(request.getDescription())
                .prefix(request.getPrefix())
                .registeredDomain(request.getRegisteredDomain()) // Add the missing registeredDomain field
                .isActive(true)
                .expiresAt(expirationDate) // Use the processed expiration date
                .allowedIps(request.getAllowedIps() != null ? String.join(",", request.getAllowedIps()) : null)
                .allowedDomains(request.getAllowedDomains() != null ? String.join(",", request.getAllowedDomains()) : null)
                .rateLimitTier(request.getRateLimitTier() != null && !request.getRateLimitTier().trim().isEmpty() ? 
                              RateLimitTier.valueOf(request.getRateLimitTier().toUpperCase().trim()) : RateLimitTier.FREE_TIER)
                .scopes(request.getScopes() != null ? String.join(",", request.getScopes()) : null)
                .build();

        try {
            ApiKey savedKey = apiKeyRepository.save(apiKey);
            
            log.info("API key '{}' created successfully for user '{}' with ID: {}", 
                    savedKey.getName(), userFkId, savedKey.getId());
            
            return ApiKeyGeneratedResponseDTO.builder()
                    .id(savedKey.getId())
                    .name(savedKey.getName())
                    .keyValue(generatedKeyValue)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create API key '{}' for user '{}': {}", 
                     request.getName(), userFkId, e.getMessage(), e);
            throw new RuntimeException("Failed to create API key: " + e.getMessage(), e);
        }
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
                existingKey.setRateLimitTier(RateLimitTier.valueOf(request.getRateLimitTier()));
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
     * Check if an API key belongs to a specific user.
     */
    @Transactional(readOnly = true)
    public boolean apiKeyBelongsToUser(UUID keyId, String userFkId) {
        return apiKeyRepository.findById(keyId)
                .map(apiKey -> apiKey.getUserFkId().equals(userFkId))
                .orElse(false);
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