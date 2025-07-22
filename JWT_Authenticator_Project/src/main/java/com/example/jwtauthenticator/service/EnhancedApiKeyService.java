package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.enums.ApiKeyScope;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enhanced API Key Service with business logic for different key types and scopes.
 * This service provides higher-level business operations for API key management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedApiKeyService {
    
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyHashUtil apiKeyHashUtil;
    
    /**
     * Create a standard user API key with basic scopes.
     */
    @Transactional
    public ApiKeyGeneratedResponseDTO createStandardUserApiKey(String userId, String keyName, String description) {
        validateUser(userId);
        
        ApiKeyCreateRequestDTO request = ApiKeyCreateRequestDTO.builder()
            .name(keyName)
            .description(description)
            .prefix("sk-")
            .rateLimitTier(RateLimitTier.BASIC.name())
            .scopes(List.of(
                ApiKeyScope.READ_USERS.name(),
                ApiKeyScope.READ_BRANDS.name(),
                ApiKeyScope.READ_CATEGORIES.name()
            ))
            .build();
            
        return createApiKeyInternal(userId, request);
    }
    
    /**
     * Create a business API key with extended scopes.
     */
    @Transactional
    public ApiKeyGeneratedResponseDTO createBusinessApiKey(String userId, String keyName, String description,
                                                          List<String> allowedIps, RateLimitTier tier) {
        validateUser(userId);
        
        ApiKeyCreateRequestDTO request = ApiKeyCreateRequestDTO.builder()
            .name(keyName)
            .description(description)
            .prefix("biz-")
            .rateLimitTier(tier.name())
            .allowedIps(allowedIps)
            .scopes(List.of(
                ApiKeyScope.BUSINESS_READ.name(),
                ApiKeyScope.BUSINESS_WRITE.name(),
                ApiKeyScope.READ_USERS.name(),
                ApiKeyScope.READ_BRANDS.name()
            ))
            .build();
            
        return createApiKeyInternal(userId, request);
    }
    
    /**
     * Create an admin API key with full access.
     */
    @Transactional
    public ApiKeyGeneratedResponseDTO createAdminApiKey(String userId, String keyName, String description) {
        validateUser(userId);
        
        ApiKeyCreateRequestDTO request = ApiKeyCreateRequestDTO.builder()
            .name(keyName)
            .description(description)
            .prefix("admin-")
            .rateLimitTier(RateLimitTier.UNLIMITED.name())
            .scopes(List.of(
                ApiKeyScope.FULL_ACCESS.name()
            ))
            .build();
            
        return createApiKeyInternal(userId, request);
    }
    
    /**
     * Create a custom API key with specified scopes.
     */
    @Transactional
    public ApiKeyGeneratedResponseDTO createCustomApiKey(String userId, String keyName, String description,
                                                        List<ApiKeyScope> scopes, RateLimitTier tier,
                                                        List<String> allowedIps, List<String> allowedDomains,
                                                        LocalDateTime expiresAt) {
        validateUser(userId);
        
        List<String> scopeNames = scopes.stream()
            .map(ApiKeyScope::name)
            .toList();
        
        ApiKeyCreateRequestDTO request = ApiKeyCreateRequestDTO.builder()
            .name(keyName)
            .description(description)
            .prefix("sk-")
            .rateLimitTier(tier.name())
            .allowedIps(allowedIps)
            .allowedDomains(allowedDomains)
            .expiresAt(expiresAt)
            .scopes(scopeNames)
            .build();
            
        return createApiKeyInternal(userId, request);
    }
    
    /**
     * Internal method to create API key with proper hashing.
     */
    private ApiKeyGeneratedResponseDTO createApiKeyInternal(String userId, ApiKeyCreateRequestDTO request) {
        // Generate secure API key
        String generatedKeyValue = apiKeyHashUtil.generateSecureApiKey(request.getPrefix());
        String keyHash = apiKeyHashUtil.hashApiKey(generatedKeyValue);
        
        // Build API key entity
        ApiKey apiKey = ApiKey.builder()
            .userFkId(userId)
            .keyHash(keyHash)
            .name(request.getName())
            .description(request.getDescription())
            .prefix(request.getPrefix())
            .isActive(true)
            .expiresAt(request.getExpiresAt())
            .allowedIps(request.getAllowedIps() != null ? String.join(",", request.getAllowedIps()) : null)
            .allowedDomains(request.getAllowedDomains() != null ? String.join(",", request.getAllowedDomains()) : null)
            .rateLimitTier(request.getRateLimitTier())
            .scopes(request.getScopes() != null ? String.join(",", request.getScopes()) : null)
            .build();
            
        ApiKey savedKey = apiKeyRepository.save(apiKey);
        
        log.info("Created API key '{}' for user '{}' with scopes: {}", 
                savedKey.getName(), userId, savedKey.getScopes());
        
        return ApiKeyGeneratedResponseDTO.builder()
            .id(savedKey.getId())
            .name(savedKey.getName())
            .keyValue(generatedKeyValue) // Return plain key only once
            .build();
    }
    
    /**
     * Grant additional scopes to an existing API key.
     */
    @Transactional
    public boolean grantScopes(UUID keyId, String userId, List<ApiKeyScope> additionalScopes) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userId).map(apiKey -> {
            List<String> currentScopes = apiKey.getScopesAsList();
            
            // Add new scopes
            for (ApiKeyScope scope : additionalScopes) {
                if (!currentScopes.contains(scope.name())) {
                    currentScopes.add(scope.name());
                }
            }
            
            apiKey.setScopes(String.join(",", currentScopes));
            apiKeyRepository.save(apiKey);
            
            log.info("Granted additional scopes to API key '{}' (ID: {}): {}", 
                    apiKey.getName(), keyId, additionalScopes);
            
            return true;
        }).orElse(false);
    }
    
    /**
     * Revoke specific scopes from an API key.
     */
    @Transactional
    public boolean revokeScopes(UUID keyId, String userId, List<ApiKeyScope> scopesToRevoke) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userId).map(apiKey -> {
            List<String> currentScopes = apiKey.getScopesAsList();
            
            // Remove scopes
            for (ApiKeyScope scope : scopesToRevoke) {
                currentScopes.remove(scope.name());
            }
            
            apiKey.setScopes(String.join(",", currentScopes));
            apiKeyRepository.save(apiKey);
            
            log.info("Revoked scopes from API key '{}' (ID: {}): {}", 
                    apiKey.getName(), keyId, scopesToRevoke);
            
            return true;
        }).orElse(false);
    }
    
    /**
     * Upgrade rate limit tier for an API key.
     */
    @Transactional
    public boolean upgradeRateLimitTier(UUID keyId, String userId, RateLimitTier newTier) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userId).map(apiKey -> {
            String oldTier = apiKey.getRateLimitTier();
            apiKey.setRateLimitTier(newTier.name());
            apiKeyRepository.save(apiKey);
            
            log.info("Upgraded rate limit tier for API key '{}' (ID: {}) from {} to {}", 
                    apiKey.getName(), keyId, oldTier, newTier);
            
            return true;
        }).orElse(false);
    }
    
    /**
     * Add IP restrictions to an existing API key.
     */
    @Transactional
    public boolean addIpRestrictions(UUID keyId, String userId, List<String> allowedIps) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userId).map(apiKey -> {
            apiKey.setAllowedIps(String.join(",", allowedIps));
            apiKeyRepository.save(apiKey);
            
            log.info("Added IP restrictions to API key '{}' (ID: {}): {}", 
                    apiKey.getName(), keyId, allowedIps);
            
            return true;
        }).orElse(false);
    }
    
    /**
     * Set expiration date for an API key.
     */
    @Transactional
    public boolean setExpiration(UUID keyId, String userId, LocalDateTime expiresAt) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userId).map(apiKey -> {
            apiKey.setExpiresAt(expiresAt);
            apiKeyRepository.save(apiKey);
            
            log.info("Set expiration for API key '{}' (ID: {}) to: {}", 
                    apiKey.getName(), keyId, expiresAt);
            
            return true;
        }).orElse(false);
    }
    
    /**
     * Get API keys by scope.
     */
    @Transactional(readOnly = true)
    public List<ApiKey> getApiKeysByScope(ApiKeyScope scope) {
        return apiKeyRepository.findAll().stream()
            .filter(key -> key.getScopesAsList().contains(scope.name()))
            .toList();
    }
    
    /**
     * Check if user has reached API key limit.
     */
    @Transactional(readOnly = true)
    public boolean hasReachedApiKeyLimit(String userId, int limit) {
        long userKeyCount = apiKeyRepository.findByUserFkId(userId).size();
        return userKeyCount >= limit;
    }
    
    /**
     * Regenerate an API key (creates new key, invalidates old one).
     */
    @Transactional
    public Optional<ApiKeyGeneratedResponseDTO> regenerateApiKey(UUID keyId, String userId) {
        return apiKeyRepository.findByIdAndUserFkId(keyId, userId).map(existingKey -> {
            // Store existing key details
            String name = existingKey.getName();
            String description = existingKey.getDescription();
            String prefix = existingKey.getPrefix();
            
            // Create new key value and hash
            String newKeyValue = apiKeyHashUtil.generateSecureApiKey(prefix);
            String newKeyHash = apiKeyHashUtil.hashApiKey(newKeyValue);
            
            // Update the existing key
            existingKey.setKeyHash(newKeyHash);
            existingKey.setUpdatedAt(LocalDateTime.now());
            
            ApiKey savedKey = apiKeyRepository.save(existingKey);
            
            log.info("Regenerated API key '{}' (ID: {}) for user: {}", name, keyId, userId);
            
            return ApiKeyGeneratedResponseDTO.builder()
                .id(savedKey.getId())
                .name(savedKey.getName())
                .keyValue(newKeyValue)
                .build();
        });
    }
    
    /**
     * Validate that user exists.
     */
    private void validateUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User with ID " + userId + " not found");
        }
    }
}