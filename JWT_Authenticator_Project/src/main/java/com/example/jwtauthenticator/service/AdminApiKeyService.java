package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin service for managing API keys across all users.
 * Provides administrative functions for API key management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminApiKeyService {
    
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyHashUtil apiKeyHashUtil;
    
    /**
     * Get all API keys in the system (admin only).
     */
    @Transactional(readOnly = true)
    public List<ApiKeyResponseDTO> getAllApiKeys() {
        return apiKeyRepository.findAll()
            .stream()
            .map(ApiKeyResponseDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all API keys for a specific user (admin only).
     */
    @Transactional(readOnly = true)
    public List<ApiKeyResponseDTO> getApiKeysForUser(String userId) {
        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User with ID " + userId + " not found");
        }
        
        return apiKeyRepository.findByUserFkId(userId)
            .stream()
            .map(ApiKeyResponseDTO::fromEntity)
            .collect(Collectors.toList());
    }
    
    /**
     * Create an API key for a specific user (admin only).
     */
    @Transactional
    public ApiKeyGeneratedResponseDTO createApiKeyForUser(String userId, ApiKeyCreateRequestDTO request) {
        // Validate user exists
        userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User with ID " + userId + " not found"));
        
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
            .rateLimitTier(request.getRateLimitTier() != null ? 
                          RateLimitTier.valueOf(request.getRateLimitTier()) : RateLimitTier.FREE_TIER)
            .scopes(request.getScopes() != null ? String.join(",", request.getScopes()) : null)
            .build();
        
        ApiKey savedKey = apiKeyRepository.save(apiKey);
        
        log.info("Admin created API key '{}' for user '{}'", savedKey.getName(), userId);
        
        return ApiKeyGeneratedResponseDTO.builder()
            .id(savedKey.getId())
            .name(savedKey.getName())
            .keyValue(generatedKeyValue) // Return plain key only once
            .build();
    }
    
    /**
     * Revoke any API key in the system (admin only).
     */
    @Transactional
    public boolean revokeApiKey(UUID keyId) {
        return apiKeyRepository.findById(keyId).map(apiKey -> {
            apiKey.setActive(false);
            apiKey.setRevokedAt(LocalDateTime.now());
            apiKeyRepository.save(apiKey);
            log.info("Admin revoked API key '{}' (ID: {})", apiKey.getName(), keyId);
            return true;
        }).orElse(false);
    }
    
    /**
     * Delete any API key in the system (admin only).
     */
    @Transactional
    public boolean deleteApiKey(UUID keyId) {
        if (apiKeyRepository.existsById(keyId)) {
            apiKeyRepository.deleteById(keyId);
            log.info("Admin deleted API key with ID: {}", keyId);
            return true;
        }
        return false;
    }
    
    /**
     * Get usage statistics for a specific API key.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getApiKeyUsageStats(UUID keyId) {
        return apiKeyRepository.findById(keyId).map(apiKey -> {
            Map<String, Object> stats = new HashMap<>();
            
            // Basic key information
            stats.put("id", apiKey.getId());
            stats.put("name", apiKey.getName());
            stats.put("userId", apiKey.getUserFkId());
            stats.put("isActive", apiKey.isActive());
            stats.put("createdAt", apiKey.getCreatedAt());
            stats.put("lastUsedAt", apiKey.getLastUsedAt());
            stats.put("expiresAt", apiKey.getExpiresAt());
            
            // Rate limiting information
            stats.put("rateLimitTier", apiKey.getRateLimitTier());
            
            // Rate limiting information (now handled by ProfessionalRateLimitService)
            // TODO: Integrate with ApiKeyUsageStats for admin dashboard if needed
            stats.put("totalRequests24h", 0L); // Placeholder - can be implemented later with ApiKeyUsageStats
            
            Map<String, Object> rateLimitInfo = new HashMap<>();
            rateLimitInfo.put("currentRequests", 0);
            rateLimitInfo.put("maxRequests", apiKey.getRateLimitTier() != null ? apiKey.getRateLimitTier().getRequestLimit() : 0);
            rateLimitInfo.put("remainingRequests", 0);
            rateLimitInfo.put("windowEnd", null);
            rateLimitInfo.put("isLimited", false);
            
            stats.put("rateLimit", rateLimitInfo);
            
            // Security information
            stats.put("allowedIps", apiKey.getAllowedIps());
            stats.put("allowedDomains", apiKey.getAllowedDomains());
            stats.put("scopes", apiKey.getScopes());
            
            return stats;
        }).orElse(new HashMap<>());
    }
    
    /**
     * Get system-wide API key statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Total counts
        long totalKeys = apiKeyRepository.count();
        long activeKeys = apiKeyRepository.findAll().stream()
            .mapToLong(key -> key.isActive() ? 1 : 0)
            .sum();
        long expiredKeys = apiKeyRepository.findAll().stream()
            .mapToLong(key -> 
                key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now()) ? 1 : 0)
            .sum();
        
        stats.put("totalApiKeys", totalKeys);
        stats.put("activeApiKeys", activeKeys);
        stats.put("inactiveApiKeys", totalKeys - activeKeys);
        stats.put("expiredApiKeys", expiredKeys);
        
        // Rate limit tier distribution
        Map<String, Long> tierDistribution = apiKeyRepository.findAll().stream()
            .collect(Collectors.groupingBy(
                key -> key.getRateLimitTier() != null ? key.getRateLimitTier().name() : "FREE_TIER",
                Collectors.counting()
            ));
        stats.put("rateLimitTierDistribution", tierDistribution);
        
        // Keys created in the last 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long recentKeys = apiKeyRepository.findAll().stream()
            .mapToLong(key -> 
                key.getCreatedAt() != null && key.getCreatedAt().isAfter(thirtyDaysAgo) ? 1 : 0)
            .sum();
        stats.put("keysCreatedLast30Days", recentKeys);
        
        // Keys used in the last 24 hours
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        long recentlyUsedKeys = apiKeyRepository.findAll().stream()
            .mapToLong(key -> 
                key.getLastUsedAt() != null && key.getLastUsedAt().isAfter(oneDayAgo) ? 1 : 0)
            .sum();
        stats.put("keysUsedLast24Hours", recentlyUsedKeys);
        
        return stats;
    }
    
    /**
     * Reset rate limit for a specific API key.
     * Note: Rate limiting is now handled by ProfessionalRateLimitService via ApiKeyUsageStats
     */
    @Transactional
    public boolean resetRateLimit(UUID keyId) {
        // TODO: Implement rate limit reset with ApiKeyUsageStats if needed
        log.info("Rate limit reset requested for API key ID: {} (not implemented - using new system)", keyId);
        return false; // Not implemented with new rate limiting system
    }
    
    /**
     * Update scopes for a specific API key.
     */
    @Transactional
    public ApiKeyResponseDTO updateApiKeyScopes(UUID keyId, String newScopes) {
        return apiKeyRepository.findById(keyId).map(apiKey -> {
            apiKey.setScopes(newScopes);
            ApiKey savedKey = apiKeyRepository.save(apiKey);
            log.info("Admin updated scopes for API key '{}' (ID: {}) to: {}", 
                    apiKey.getName(), keyId, newScopes);
            return ApiKeyResponseDTO.fromEntity(savedKey);
        }).orElse(null);
    }
    
    /**
     * Helper method to get rate limit tier from string.
     */
    private RateLimitTier getRateLimitTier(String tierString) {
        if (tierString == null) {
            return RateLimitTier.FREE_TIER;
        }
        
        try {
            return RateLimitTier.valueOf(tierString.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RateLimitTier.FREE_TIER;
        }
    }
}