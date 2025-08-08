package com.example.jwtauthenticator.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jwtauthenticator.dto.ApiKeyCreateRequestDTO;
import com.example.jwtauthenticator.dto.ApiKeyGeneratedResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyResponseDTO;
import com.example.jwtauthenticator.dto.ApiKeyWithUsageDTO;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.ApiKeyEnvironment;
import com.example.jwtauthenticator.enums.ApiKeyScope;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.ApiKeyEncryptionUtil;
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import com.example.jwtauthenticator.util.DomainExtractionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced API Key Service with domain-based access control and business logic.
 * Supports all domain types including .com, .org, .io, .in, .co, etc.
 * This service provides higher-level business operations for API key management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedApiKeyService {
    
    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyHashUtil apiKeyHashUtil;
    private final ApiKeyEncryptionUtil apiKeyEncryptionUtil;
    private final DomainValidationService domainValidationService;
    private final DomainManagementService domainManagementService;
    private final PlanValidationService planValidationService;
    private final MonthlyUsageTrackingService monthlyUsageService;
    private final DomainExtractionUtil domainExtractionUtil;
    
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String API_KEY_PREFIX = "sk-";
    private static final int API_KEY_LENGTH = 48;
    
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
            .rateLimitTier(RateLimitTier.FREE_TIER.name())
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
            .rateLimitTier(RateLimitTier.BUSINESS_TIER.name())
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
     * Internal method to create API key with proper hashing and encryption.
     * 
     * SECURITY FLOW:
     * 1. Generate cryptographically secure API key
     * 2. Create SHA-256 hash for authentication (irreversible)
     * 3. Create AES-256-GCM encryption for secure retrieval
     * 4. Store both hash and encrypted value
     * 5. Return plain key only once during creation
     */
    private ApiKeyGeneratedResponseDTO createApiKeyInternal(String userId, ApiKeyCreateRequestDTO request) {
        // Step 1: Generate secure API key (SAME KEY for both hash and encryption)
        String generatedKeyValue = apiKeyHashUtil.generateSecureApiKey(request.getPrefix());
        // Step 2: Create SHA-256 hash for authentication
        String keyHash = apiKeyHashUtil.hashApiKey(generatedKeyValue);
        
        // Step 3: Create encrypted version for secure retrieval
        String encryptedKeyValue = apiKeyEncryptionUtil.encryptApiKey(generatedKeyValue, userId);
        
        // Step 4: Generate preview for UI display
        String keyPreview = ApiKey.generateKeyPreview(generatedKeyValue);
        
        // Step 5: Build API key entity with both hash and encrypted value
        ApiKey apiKey = ApiKey.builder()
            .userFkId(userId)
            .keyHash(keyHash)                    // For authentication
            .encryptedKeyValue(encryptedKeyValue) // For secure retrieval
            .keyPreview(keyPreview)              // For UI display
            .name(request.getName())
            .description(request.getDescription())
            .prefix(request.getPrefix())
            .registeredDomain(request.getRegisteredDomain())
            .isActive(true)
            .expiresAt(request.getExpiresAt())
            .allowedIps(request.getAllowedIps() != null ? String.join(",", request.getAllowedIps()) : null)
            .allowedDomains(request.getAllowedDomains() != null ? String.join(",", request.getAllowedDomains()) : null)
            .rateLimitTier(request.getRateLimitTier() != null ? 
                          RateLimitTier.valueOf(request.getRateLimitTier()) : RateLimitTier.FREE_TIER)
            .scopes(request.getScopes() != null ? String.join(",", request.getScopes()) : null)
            .build();
            
        ApiKey savedKey = apiKeyRepository.save(apiKey);
        
        log.info("Created API key '{}' for user '{}' with scopes: {} (encrypted: {})", 
                savedKey.getName(), userId, savedKey.getScopes(), 
                savedKey.getEncryptedKeyValue() != null ? "YES" : "NO");
        
        return ApiKeyGeneratedResponseDTO.builder()
            .id(savedKey.getId())
            .name(savedKey.getName())
            .keyValue(generatedKeyValue) // Return plain key only once during creation
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
            RateLimitTier oldTier = apiKey.getRateLimitTier();
            apiKey.setRateLimitTier(newTier);
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
     * Get API keys by scope - PERFORMANCE OPTIMIZED.
     * Uses database query instead of loading all keys into memory.
     */
    @Transactional(readOnly = true)
    public List<ApiKey> getApiKeysByScope(ApiKeyScope scope) {
        // Use database query with LIKE to find keys containing the scope
        return apiKeyRepository.findByScopesContaining(scope.name());
    }
    
    /**
     * Check if user has reached API key limit - PERFORMANCE OPTIMIZED.
     * Uses COUNT query instead of loading all keys into memory.
     */
    @Transactional(readOnly = true)
    public boolean hasReachedApiKeyLimit(String userId, int limit) {
        long userKeyCount = apiKeyRepository.countActiveApiKeysByUserFkId(userId);
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
            
            // Create new key value, hash, and encryption
            String newKeyValue = apiKeyHashUtil.generateSecureApiKey(prefix);
            String newKeyHash = apiKeyHashUtil.hashApiKey(newKeyValue);
            String newEncryptedKeyValue = apiKeyEncryptionUtil.encryptApiKey(newKeyValue, userId);
            
            // Update the existing key with both hash and encrypted value
            existingKey.setKeyHash(newKeyHash);
            existingKey.setEncryptedKeyValue(newEncryptedKeyValue);
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
    
    // --- NEW DOMAIN-BASED METHODS ---
    
    /**
     * Create API key with registered domain (supports all TLD types)
     */
    @Transactional
    public ApiKeyCreateResult createApiKeyWithDomain(String userId, ApiKeyCreateRequestDTO request) {
        log.info("Creating API key with domain for user: {} - Domain: {}", userId, request.getRegisteredDomain());

        try {
            // Step 1: Validate user exists
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ApiKeyCreateResult.failure("User not found: " + userId, "USER_NOT_FOUND");
            }

            // Step 2: Validate and normalize domain
            String normalizedDomain = domainValidationService.normalizeDomain(request.getRegisteredDomain());
            if (normalizedDomain == null || normalizedDomain.trim().isEmpty()) {
                return ApiKeyCreateResult.failure("Invalid domain format", "INVALID_DOMAIN_FORMAT");
            }

            if (!domainValidationService.isValidDomainFormat(normalizedDomain)) {
                return ApiKeyCreateResult.failure(
                    "Invalid domain format. Supported formats: xamply.com, xamplyfy.co, xamplyfy.in, etc.",
                    "INVALID_DOMAIN_FORMAT"
                );
            }

            // Step 3: Check domain uniqueness
            if (apiKeyRepository.existsByRegisteredDomain(normalizedDomain)) {
                return ApiKeyCreateResult.failure(
                    "Domain already registered to another API key: " + normalizedDomain,
                    "DOMAIN_ALREADY_EXISTS"
                );
            }

            // Step 4: Check API key name uniqueness for user
            if (apiKeyRepository.existsByNameAndUserFkId(request.getName(), userId)) {
                return ApiKeyCreateResult.failure(
                    "API key name already exists for this user: " + request.getName(),
                    "NAME_ALREADY_EXISTS"
                );
            }

            // Step 5: Generate API key
            String rawApiKey = apiKeyHashUtil.generateSecureApiKey(request.getPrefix() != null ? request.getPrefix() : "sk-");
            String keyHash = apiKeyHashUtil.hashApiKey(rawApiKey);

            // Step 6: Create API key entity
            ApiKey apiKey = ApiKey.builder()
                .userFkId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .prefix(request.getPrefix() != null ? request.getPrefix() : "sk-")
                .keyHash(keyHash)
                .registeredDomain(normalizedDomain)
                .isActive(true)
                .expiresAt(request.getExpiresAt())
                .allowedIps(request.getAllowedIps() != null ? String.join(",", request.getAllowedIps()) : null)
                .allowedDomains(request.getAllowedDomains() != null ? String.join(",", request.getAllowedDomains()) : null)
                .rateLimitTier(request.getRateLimitTier() != null ? 
                              RateLimitTier.valueOf(request.getRateLimitTier()) : RateLimitTier.FREE_TIER)
                .scopes(request.getScopes() != null ? String.join(",", request.getScopes()) : null)
                .build();

            // Step 7: Save API key
            ApiKey savedApiKey = apiKeyRepository.save(apiKey);

            log.info("API key created successfully: {} for user: {} with domain: {}", 
                    savedApiKey.getId(), userId, normalizedDomain);

            return ApiKeyCreateResult.success(savedApiKey, rawApiKey);

        } catch (Exception e) {
            log.error("Error creating API key for user: {} with domain: {}", userId, request.getRegisteredDomain(), e);
            return ApiKeyCreateResult.failure("Failed to create API key: " + e.getMessage(), "CREATION_ERROR");
        }
    }

    /**
     * Update API key's registered domain
     */
    @Transactional
    public ApiKeyUpdateResult updateRegisteredDomain(UUID apiKeyId, String userId, String newDomain) {
        log.info("Updating registered domain for API key: {} - New domain: {}", apiKeyId, newDomain);

        try {
            // Find API key
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByIdAndUserFkId(apiKeyId, userId);
            if (apiKeyOpt.isEmpty()) {
                return ApiKeyUpdateResult.failure("API key not found or access denied", "API_KEY_NOT_FOUND");
            }

            ApiKey apiKey = apiKeyOpt.get();

            // Validate and normalize new domain
            String normalizedDomain = domainValidationService.normalizeDomain(newDomain);
            if (!domainValidationService.isValidDomainFormat(normalizedDomain)) {
                return ApiKeyUpdateResult.failure("Invalid domain format", "INVALID_DOMAIN_FORMAT");
            }

            // Check if domain is already taken by another API key
            Optional<ApiKey> existingApiKey = apiKeyRepository.findByRegisteredDomain(normalizedDomain);
            if (existingApiKey.isPresent() && !existingApiKey.get().getId().equals(apiKeyId)) {
                return ApiKeyUpdateResult.failure(
                    "Domain already registered to another API key",
                    "DOMAIN_ALREADY_EXISTS"
                );
            }

            // Update domain
            String oldDomain = apiKey.getRegisteredDomain();
            apiKey.setRegisteredDomain(normalizedDomain);
            ApiKey updatedApiKey = apiKeyRepository.save(apiKey);

            log.info("API key domain updated successfully: {} - Old: {}, New: {}", 
                    apiKeyId, oldDomain, normalizedDomain);

            return ApiKeyUpdateResult.success(updatedApiKey);

        } catch (Exception e) {
            log.error("Error updating domain for API key: {}", apiKeyId, e);
            return ApiKeyUpdateResult.failure("Failed to update domain: " + e.getMessage(), "UPDATE_ERROR");
        }
    }

    /**
     * Get all API keys for a user with domain information
     */
    public List<ApiKeyResponseDTO> getUserApiKeysWithDomains(String userId) {
        log.debug("Fetching API keys with domains for user: {}", userId);

        List<ApiKey> apiKeys = apiKeyRepository.findByUserFkIdOrderByCreatedAtDesc(userId);
        return apiKeys.stream()
                .map(ApiKeyResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Get API keys with usage stats in single query
     * Eliminates N+1 queries by fetching all data at once
     */
    public List<ApiKeyWithUsageDTO> getUserApiKeysWithUsageOptimized(String userId) {
        log.debug("🚀 Fetching optimized API keys with usage for user: {}", userId);
        
        long startTime = System.currentTimeMillis();
        
        // Get data from the last 30 days for current usage calculation
        LocalDateTime fromDate = LocalDateTime.now().minusDays(30);
        
        List<Object[]> results = apiKeyRepository.findApiKeysWithUsageByUserFkId(userId, fromDate);
        
        List<ApiKeyWithUsageDTO> apiKeys = results.stream()
                .map(ApiKeyWithUsageDTO::fromQueryResult)
                .collect(Collectors.toList());
        
        long executionTime = System.currentTimeMillis() - startTime;
        log.debug("✅ Optimized API keys fetch completed in {}ms for user: {} (found {} keys)", 
                 executionTime, userId, apiKeys.size());
        
        return apiKeys;
    }
    
    /**
     * 🚀 PERFORMANCE OPTIMIZED: Batch process API key operations
     * Eliminates N+1 queries by processing multiple API keys in batches
     */
    @Transactional(readOnly = true)
    public Map<UUID, ApiKeyWithUsageDTO> getBatchApiKeyUsage(List<UUID> apiKeyIds) {
        log.debug("🚀 Batch processing {} API keys for usage data", apiKeyIds.size());
        
        if (apiKeyIds.isEmpty()) {
            return Map.of();
        }
        
        long startTime = System.currentTimeMillis();
        
        // Batch query instead of N individual queries
        LocalDateTime fromDate = LocalDateTime.now().minusDays(30);
        
        // This would require a new repository method for batch processing
        // For now, we'll use the existing optimized method per user
        Map<UUID, ApiKeyWithUsageDTO> result = new HashMap<>();
        
        // Group API keys by user to minimize queries
        Map<String, List<UUID>> keysByUser = apiKeyIds.stream()
            .collect(Collectors.groupingBy(keyId -> {
                // This is a simplified approach - in practice, you'd batch this lookup too
                return apiKeyRepository.findById(keyId)
                    .map(ApiKey::getUserFkId)
                    .orElse("unknown");
            }));
        
        // Process each user's API keys in batch
        keysByUser.forEach((userId, userKeyIds) -> {
            if (!"unknown".equals(userId)) {
                List<ApiKeyWithUsageDTO> userKeys = getUserApiKeysWithUsageOptimized(userId);
                userKeys.stream()
                    .filter(key -> userKeyIds.contains(key.getId()))
                    .forEach(key -> result.put(key.getId(), key));
            }
        });
        
        long executionTime = System.currentTimeMillis() - startTime;
        log.debug("✅ Batch API key processing completed in {}ms for {} keys", 
                 executionTime, apiKeyIds.size());
        
        return result;
    }

    /**
     * Check if domain is available for registration
     */
    public boolean isDomainAvailable(String domain) {
        String normalizedDomain = domainValidationService.normalizeDomain(domain);
        if (normalizedDomain == null) {
            return false;
        }

        return !apiKeyRepository.existsByRegisteredDomain(normalizedDomain);
    }

    /**
     * Check if domain is available for a specific API key (for updates)
     */
    public boolean isDomainAvailableForApiKey(String domain, UUID excludeApiKeyId) {
        String normalizedDomain = domainValidationService.normalizeDomain(domain);
        if (normalizedDomain == null) {
            return false;
        }

        Optional<ApiKey> existingApiKey = apiKeyRepository.findByRegisteredDomain(normalizedDomain);
        return existingApiKey.isEmpty() || existingApiKey.get().getId().equals(excludeApiKeyId);
    }

    /**
     * Get domain suggestions for user
     */
    public List<String> getDomainSuggestions(String baseDomain) {
        String normalizedBase = domainValidationService.normalizeDomain(baseDomain);
        if (normalizedBase == null) {
            return List.of();
        }

        // Extract domain name without TLD
        String[] parts = normalizedBase.split("\\.");
        if (parts.length < 2) {
            return List.of();
        }

        String domainName = parts[0];
        
        // Generate suggestions with different TLDs
        List<String> suggestions = List.of(
            domainName + ".com",
            domainName + ".org",
            domainName + ".io",
            domainName + ".co",
            domainName + ".in",
            domainName + ".net",
            "api." + normalizedBase,
            "app." + normalizedBase,
            "www." + normalizedBase
        );

        // Filter out already taken domains
        return suggestions.stream()
                .filter(this::isDomainAvailable)
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Validate domain format and provide suggestions
     */
    public DomainValidationInfo validateDomainForRegistration(String domain) {
        String normalizedDomain = domainValidationService.normalizeDomain(domain);
        
        if (normalizedDomain == null || normalizedDomain.trim().isEmpty()) {
            return new DomainValidationInfo(false, "Domain cannot be empty", List.of());
        }

        if (!domainValidationService.isValidDomainFormat(normalizedDomain)) {
            return new DomainValidationInfo(
                false, 
                "Invalid domain format. Use format like: xamply.com, xamplyfy.co, xamplyfy.in",
                getDomainSuggestions(domain)
            );
        }

        if (!isDomainAvailable(normalizedDomain)) {
            return new DomainValidationInfo(
                false,
                "Domain already registered to another API key",
                getDomainSuggestions(domain)
            );
        }

        return new DomainValidationInfo(true, "Domain is available", List.of());
    }

    /**
     * Create API key with comprehensive plan and domain validation
     * This is the main method that should be used for all new API key creation
     * ✅ DEBUGGING: Extended timeout for debugging sessions (8 minutes)
     */
    @Transactional(timeout = 480, rollbackFor = Exception.class)
    public ApiKeyCreateResult createApiKeyWithPlanValidation(ApiKeyCreateRequestDTO request, String userId, 
                                                           ApiKeyEnvironment environment) {
        log.info("Creating API key with plan validation for user '{}', domain '{}', environment '{}'", 
                userId, request.getRegisteredDomain(), environment);
        
        try {
            // Step 1: Get user and validate
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            
            // Step 2: Validate plan limits
            planValidationService.validateApiKeyCreation(user);
            
            // Step 3: Process domain with comprehensive validation
            DomainManagementService.DomainProcessingResult domainResult = 
                    domainManagementService.processDomainForApiKey(request.getRegisteredDomain(), user, environment);
            
            // Step 4: Create API key with processed domain information
            ApiKey apiKey = buildApiKeyFromRequest(request, user, domainResult, environment);
            
            // Step 5: Set rate limit tier - use requested tier if valid, otherwise use user plan
            RateLimitTier rateLimitTier;
            if (request.getRateLimitTier() != null && !request.getRateLimitTier().trim().isEmpty()) {
                try {
                    RateLimitTier requestedTier = RateLimitTier.valueOf(request.getRateLimitTier().toUpperCase().trim());
                    // Validate that requested tier is allowed for user's plan
                    RateLimitTier maxAllowedTier = RateLimitTier.fromUserPlan(user.getPlan());
                    if (isValidTierForPlan(requestedTier, maxAllowedTier)) {
                        rateLimitTier = requestedTier;
                        log.info("Using requested rate limit tier '{}' for user '{}' with plan '{}'", 
                                requestedTier, user.getId(), user.getPlan());
                    } else {
                        rateLimitTier = maxAllowedTier;
                        log.warn("Requested tier '{}' not allowed for plan '{}', using '{}'", 
                                requestedTier, user.getPlan(), maxAllowedTier);
                    }
                } catch (IllegalArgumentException e) {
                    rateLimitTier = RateLimitTier.fromUserPlan(user.getPlan());
                    log.warn("Invalid rate limit tier '{}', using plan-based tier '{}'", 
                            request.getRateLimitTier(), rateLimitTier);
                }
            } else {
                rateLimitTier = RateLimitTier.fromUserPlan(user.getPlan());
            }
            apiKey.setRateLimitTier(rateLimitTier);
            
            // Step 6: Generate and hash API key with custom prefix
            String rawApiKey = generateApiKey(request.getPrefix());
            String hashedApiKey = apiKeyHashUtil.hashApiKey(rawApiKey);
            apiKey.setKeyHash(hashedApiKey);
            
            // Step 6.1: Generate and store key preview for display purposes
            String keyPreview = ApiKey.generateKeyPreview(rawApiKey);
            apiKey.setKeyPreview(keyPreview);
            log.info("🔑 Generated key preview: {}", keyPreview);
            
            // Step 6.2: Encrypt API key for secure storage and retrieval
            String encryptedKeyValue = apiKeyEncryptionUtil.encryptApiKey(rawApiKey, userId);
            apiKey.setEncryptedKeyValue(encryptedKeyValue);
            log.info("🔐 Generated encrypted key value for secure storage");
            
            // Step 7: Save API key
            ApiKey savedApiKey = apiKeyRepository.save(apiKey);
            
            // Step 8: Initialize monthly usage tracking
            monthlyUsageService.createMonthlyUsageRecord(
                    savedApiKey.getId(), 
                    userId, 
                    com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage.getCurrentMonthYear(),
                    user.getPlan()
            );
            
            log.info("✅ API key created successfully: ID={}, Domain={}, Plan={}, Environment={}", 
                    savedApiKey.getId(), domainResult.getRegisteredDomain(), user.getPlan(), environment);
            
            // Add warnings if any
            if (!domainResult.getWarnings().isEmpty()) {
                log.info("Domain processing warnings for API key '{}': {}", 
                        savedApiKey.getId(), domainResult.getWarnings());
            }
            
            return ApiKeyCreateResult.success(savedApiKey, rawApiKey);
            
        } catch (Exception e) {
            log.error("❌ Error creating API key for user '{}': {}", userId, e.getMessage(), e);
            return ApiKeyCreateResult.failure("Failed to create API key: " + e.getMessage(), "CREATION_ERROR");
        }
    }
    
    /**
     * Build API key entity from request and domain processing result
     */
    private ApiKey buildApiKeyFromRequest(ApiKeyCreateRequestDTO request, User user, 
                                        DomainManagementService.DomainProcessingResult domainResult,
                                        ApiKeyEnvironment environment) {
        
        ApiKey apiKey = ApiKey.builder()
                .name(request.getName())
                .description(request.getDescription())
                .prefix(request.getPrefix())
                .userFkId(user.getId())
                .registeredDomain(domainResult.getRegisteredDomain())
                .mainDomain(domainResult.getMainDomain())
                .subdomainPattern(domainResult.getSubdomainPattern())
                .environment(environment)
                .allowedDomains(convertListToString(request.getAllowedDomains()))
                .allowedIps(convertListToString(request.getAllowedIps()))
                .scopes(convertListToString(request.getScopes()))
                .isActive(true)
                .expiresAt(request.getExpiresAt())
                .build();
        
        // Set default scopes if none provided
        if (apiKey.getScopes() == null || apiKey.getScopes().trim().isEmpty()) {
            apiKey.setScopes(getDefaultScopesForPlan(user.getPlan()));
        }
        
        return apiKey;
    }
    
    /**
     * Get default scopes based on user plan
     */
    private String getDefaultScopesForPlan(com.example.jwtauthenticator.enums.UserPlan plan) {
        return switch (plan) {
            case FREE -> "READ_BASIC,DOMAIN_HEALTH,READ_BRANDS";
            case PRO -> "READ_BASIC,READ_ADVANCED,DOMAIN_HEALTH,DOMAIN_INSIGHTS,READ_BRANDS,READ_CATEGORIES,ANALYTICS_READ";
            case BUSINESS -> "READ_BASIC,READ_ADVANCED,WRITE_BASIC,DOMAIN_HEALTH,DOMAIN_INSIGHTS,AI_SUMMARIES,READ_BRANDS,READ_CATEGORIES,WRITE_BRANDS,ANALYTICS_READ,ANALYTICS_WRITE";
        };
    }
    
    /**
     * Check if requested rate limit tier is valid for user's plan
     */
    private boolean isValidTierForPlan(RateLimitTier requestedTier, RateLimitTier maxAllowedTier) {
        // Define tier hierarchy: FREE_TIER < PRO_TIER < BUSINESS_TIER
        int requestedLevel = getTierLevel(requestedTier);
        int maxAllowedLevel = getTierLevel(maxAllowedTier);
        return requestedLevel <= maxAllowedLevel;
    }
    
    /**
     * Get numeric level for rate limit tier comparison
     */
    private int getTierLevel(RateLimitTier tier) {
        return switch (tier) {
            case FREE_TIER -> 1;
            case PRO_TIER -> 2;
            case BUSINESS_TIER -> 3;
        };
    }
    
    /**
     * Check if user can create API key for specific domain
     */
    public boolean canUserCreateApiKeyForDomain(String userId, String domain) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return false;
            
            // Check plan limits
            planValidationService.validateApiKeyCreation(user);
            planValidationService.validateDomainClaim(user, domain);
            
            return true;
        } catch (Exception e) {
            log.debug("User '{}' cannot create API key for domain '{}': {}", userId, domain, e.getMessage());
            return false;
        }
    }
    
    /**
     * Get user's current plan usage
     */
    public UserPlanUsage getUserPlanUsage(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        
        int currentApiKeys = apiKeyRepository.countByUserFkId(userId);
        int currentDomains = domainManagementService.getUserClaimedDomains(userId).size();
        
        // Set current counts in user for helper methods
        user.setCurrentApiKeyCount(currentApiKeys);
        user.setCurrentDomainCount(currentDomains);
        
        return UserPlanUsage.builder()
                .plan(user.getPlan())
                .currentApiKeys(currentApiKeys)
                .maxApiKeys(user.getPlan().getMaxApiKeys())
                .currentDomains(currentDomains)
                .maxDomains(user.getPlan().getMaxDomains())
                .remainingApiKeys(user.getRemainingApiKeys())
                .remainingDomains(user.getRemainingDomains())
                .canCreateApiKey(user.canCreateApiKey())
                .canClaimDomain(user.canClaimDomain())
                .build();
    }
    
    /**
     * Generate a secure API key
     */
    private String generateApiKey() {
        return generateApiKey(null);
    }
    
    /**
     * Generate a secure API key with custom prefix
     */
    public String generateApiKey(String prefix) {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        secureRandom.nextBytes(randomBytes);
        
        // Use custom prefix if provided, otherwise use default
        String actualPrefix;
        if (prefix != null && !prefix.trim().isEmpty()) {
            String cleanPrefix = prefix.trim();
            actualPrefix = cleanPrefix.endsWith("-") ? cleanPrefix : cleanPrefix + "-";
        } else {
            actualPrefix = API_KEY_PREFIX;
        }
        
        StringBuilder apiKey = new StringBuilder(actualPrefix);
        for (byte b : randomBytes) {
            apiKey.append(String.format("%02x", b & 0xff));
        }
        
        return apiKey.toString();
    }
    
    /**
     * Convert list to comma-separated string
     */
    private String convertListToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join(",", list);
    }
    
    /**
     * Validate that user exists.
     */
    private void validateUser(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User with ID " + userId + " not found");
        }
    }
    
    /**
     * User plan usage information
     */
    @lombok.Builder
    @lombok.Data
    public static class UserPlanUsage {
        private com.example.jwtauthenticator.enums.UserPlan plan;
        private int currentApiKeys;
        private int maxApiKeys;
        private int currentDomains;
        private int maxDomains;
        private int remainingApiKeys;
        private int remainingDomains;
        private boolean canCreateApiKey;
        private boolean canClaimDomain;
    }

    // --- RESULT CLASSES ---
    
    public static class ApiKeyCreateResult {
        private final boolean success;
        private final String errorMessage;
        private final String errorCode;
        private final ApiKey apiKey;
        private final String rawApiKey;

        private ApiKeyCreateResult(boolean success, String errorMessage, String errorCode, 
                                 ApiKey apiKey, String rawApiKey) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.apiKey = apiKey;
            this.rawApiKey = rawApiKey;
        }

        public static ApiKeyCreateResult success(ApiKey apiKey, String rawApiKey) {
            return new ApiKeyCreateResult(true, null, null, apiKey, rawApiKey);
        }

        public static ApiKeyCreateResult failure(String errorMessage, String errorCode) {
            return new ApiKeyCreateResult(false, errorMessage, errorCode, null, null);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorCode() { return errorCode; }
        public ApiKey getApiKey() { return apiKey; }
        public String getRawApiKey() { return rawApiKey; }
    }

    public static class ApiKeyUpdateResult {
        private final boolean success;
        private final String errorMessage;
        private final String errorCode;
        private final ApiKey apiKey;

        private ApiKeyUpdateResult(boolean success, String errorMessage, String errorCode, ApiKey apiKey) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.apiKey = apiKey;
        }

        public static ApiKeyUpdateResult success(ApiKey apiKey) {
            return new ApiKeyUpdateResult(true, null, null, apiKey);
        }

        public static ApiKeyUpdateResult failure(String errorMessage, String errorCode) {
            return new ApiKeyUpdateResult(false, errorMessage, errorCode, null);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorCode() { return errorCode; }
        public ApiKey getApiKey() { return apiKey; }
    }
    public static class DomainValidationInfo {
        private final boolean valid;
        private final String message;
        private final List<String> suggestions;

        public DomainValidationInfo(boolean valid, String message, List<String> suggestions) {
            this.valid = valid;
            this.message = message;
            this.suggestions = suggestions;
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public List<String> getSuggestions() { return suggestions; }
    }
}