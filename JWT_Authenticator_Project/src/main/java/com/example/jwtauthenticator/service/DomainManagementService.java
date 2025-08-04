package com.example.jwtauthenticator.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.ApiKeyEnvironment;
import com.example.jwtauthenticator.exception.DomainValidationException;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.util.DomainExtractionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing domain registration and validation
 * Handles main domain extraction, subdomain validation, and domain hierarchy
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainManagementService {
    
    private final ApiKeyRepository apiKeyRepository;
    private final DomainExtractionUtil domainExtractionUtil;
    private final PlanValidationService planValidationService;
    
    /**
     * Process domain for API key creation
     * Handles main domain extraction and validation
     */
    @Transactional
    public DomainProcessingResult processDomainForApiKey(String requestedDomain, User user, ApiKeyEnvironment environment) {
        log.info("Processing domain '{}' for user '{}' in environment '{}'", requestedDomain, user.getId(), environment);
        
        // Validate domain format
        if (!domainExtractionUtil.isValidDomainFormat(requestedDomain)) {
            throw new DomainValidationException("Invalid domain format: " + requestedDomain);
        }
        
        // Extract main domain
        String mainDomain = domainExtractionUtil.extractMainDomain(requestedDomain);
        log.debug("Extracted main domain: '{}' from requested domain: '{}'", mainDomain, requestedDomain);
        
        // Check if this is a subdomain
        boolean isSubdomain = domainExtractionUtil.isSubdomainOf(requestedDomain, mainDomain);
        
        DomainProcessingResult result = new DomainProcessingResult();
        result.setRequestedDomain(requestedDomain);
        result.setMainDomain(mainDomain);
        result.setIsSubdomain(isSubdomain);
        result.setEnvironment(environment);
        
        if (isSubdomain) {
            // Handle subdomain creation
            result = handleSubdomainCreation(requestedDomain, mainDomain, user, environment);
        } else {
            // Handle main domain creation
            result = handleMainDomainCreation(requestedDomain, user, environment);
        }
        
        log.info("Domain processing completed for '{}': {}", requestedDomain, result);
        return result;
    }
    
    /**
     * Handle creation of API key with subdomain
     * Ensures main domain is registered first
     */
    private DomainProcessingResult handleSubdomainCreation(String subdomainRequest, String mainDomain, 
                                                          User user, ApiKeyEnvironment environment) {
        log.debug("Handling subdomain creation: '{}' with main domain: '{}'", subdomainRequest, mainDomain);
        
        // Check if main domain is already registered by this user
        Optional<ApiKey> existingMainDomainKey = findMainDomainApiKey(mainDomain, user.getId());
        
        DomainProcessingResult result = new DomainProcessingResult();
        result.setRequestedDomain(subdomainRequest);
        result.setMainDomain(mainDomain);
        result.setIsSubdomain(true);
        result.setEnvironment(environment);
        
        if (existingMainDomainKey.isPresent()) {
            // Main domain exists, validate subdomain against existing key
            ApiKey mainDomainKey = existingMainDomainKey.get();
            
            if (!mainDomainKey.allowsSubdomainCreation()) {
                // Update existing key to allow subdomains
                updateApiKeyForSubdomainSupport(mainDomainKey, mainDomain);
                log.info("Updated existing API key to support subdomains for main domain: '{}'", mainDomain);
            }
            
            result.setMainDomainApiKey(mainDomainKey);
            result.setMainDomainExists(true);
            result.setRegisteredDomain(subdomainRequest);
            result.setSubdomainPattern("*." + mainDomain);
            
        } else {
            // Main domain doesn't exist, need to register it first
            log.info("Main domain '{}' not found. Auto-registering main domain for subdomain request.", mainDomain);
            
            // Validate user can create another domain
            planValidationService.validateDomainClaim(user, mainDomain);
            
            result.setMainDomainExists(false);
            result.setNeedsMainDomainRegistration(true);
            result.setRegisteredDomain(mainDomain); // Register main domain
            result.setSubdomainPattern("*." + mainDomain); // Allow all subdomains
            result.setAutoRegisteredMainDomain(true);
        }
        
        // Validate subdomain prefix for environment
        String subdomainPrefix = domainExtractionUtil.getSubdomainPrefix(subdomainRequest, mainDomain);
        if (subdomainPrefix != null && !environment.isValidSubdomainPrefix(subdomainPrefix)) {
            log.warn("Subdomain prefix '{}' may not be suitable for environment '{}'", subdomainPrefix, environment);
            result.addWarning("Subdomain prefix '" + subdomainPrefix + "' may not be suitable for " + environment + " environment");
        }
        
        return result;
    }
    
    /**
     * Handle creation of API key with main domain
     */
    private DomainProcessingResult handleMainDomainCreation(String mainDomain, User user, ApiKeyEnvironment environment) {
        log.debug("Handling main domain creation: '{}'", mainDomain);
        
        // Validate user can create this domain
        planValidationService.validateDomainClaim(user, mainDomain);
        
        // Check if domain is already registered
        if (isDomainAlreadyRegistered(mainDomain, user.getId())) {
            throw new DomainValidationException("Domain '" + mainDomain + "' is already registered");
        }
        
        DomainProcessingResult result = new DomainProcessingResult();
        result.setRequestedDomain(mainDomain);
        result.setMainDomain(mainDomain);
        result.setRegisteredDomain(mainDomain);
        result.setIsSubdomain(false);
        result.setMainDomainExists(false);
        result.setEnvironment(environment);
        
        // For main domain, suggest subdomain pattern for future use
        result.setSubdomainPattern(domainExtractionUtil.generateSubdomainPattern(mainDomain));
        
        return result;
    }
    
    /**
     * Find API key that owns the main domain for a user
     */
    private Optional<ApiKey> findMainDomainApiKey(String mainDomain, String userId) {
        // Look for API key with this main domain
        List<ApiKey> userKeys = apiKeyRepository.findByUserFkId(userId);
        
        return userKeys.stream()
                .filter(key -> mainDomain.equalsIgnoreCase(key.getMainDomain()) || 
                              mainDomain.equalsIgnoreCase(key.getRegisteredDomain()))
                .findFirst();
    }
    
    /**
     * Check if domain is already registered by user
     */
    private boolean isDomainAlreadyRegistered(String domain, String userId) {
        List<ApiKey> userKeys = apiKeyRepository.findByUserFkId(userId);
        
        return userKeys.stream()
                .anyMatch(key -> domain.equalsIgnoreCase(key.getRegisteredDomain()) ||
                               domain.equalsIgnoreCase(key.getMainDomain()) ||
                               key.getAllowedDomainsAsList().stream()
                                   .anyMatch(allowedDomain -> domain.equalsIgnoreCase(allowedDomain)));
    }
    
    /**
     * Update existing API key to support subdomains
     */
    @Transactional
    public void updateApiKeyForSubdomainSupport(ApiKey apiKey, String mainDomain) {
        if (apiKey.getSubdomainPattern() == null || !apiKey.getSubdomainPattern().contains("*")) {
            apiKey.setSubdomainPattern("*." + mainDomain);
            apiKey.setMainDomain(mainDomain);
            apiKeyRepository.save(apiKey);
            log.info("Updated API key '{}' to support subdomains with pattern: '{}'", 
                    apiKey.getId(), apiKey.getSubdomainPattern());
        }
    }
    
    /**
     * Get all domains claimed by user
     */
    public List<String> getUserClaimedDomains(String userId) {
        List<ApiKey> userKeys = apiKeyRepository.findByUserFkId(userId);
        
        Set<String> claimedDomains = new HashSet<>();
        
        for (ApiKey key : userKeys) {
            if (key.getMainDomain() != null) {
                claimedDomains.add(key.getMainDomain());
            }
            if (key.getRegisteredDomain() != null) {
                claimedDomains.add(key.getRegisteredDomain());
            }
            claimedDomains.addAll(key.getAllowedDomainsAsList());
        }
        
        return new ArrayList<>(claimedDomains);
    }
    
    /**
     * Get domain suggestions for user
     */
    public List<String> getDomainSuggestions(String baseDomain, ApiKeyEnvironment environment) {
        List<String> suggestions = new ArrayList<>();
        
        if (domainExtractionUtil.isDevelopmentDomain(baseDomain)) {
            suggestions.addAll(Arrays.asList("localhost", "127.0.0.1", "localhost.com"));
            return suggestions;
        }
        
        String mainDomain = domainExtractionUtil.extractMainDomain(baseDomain);
        
        // Environment-specific suggestions
        switch (environment) {
            case DEVELOPMENT -> {
                suggestions.add("dev." + mainDomain);
                suggestions.add("local." + mainDomain);
                suggestions.add("localhost." + mainDomain);
            }
            case TESTING -> {
                suggestions.add("test." + mainDomain);
                suggestions.add("staging." + mainDomain);
                suggestions.add("qa." + mainDomain);
            }
            case PRODUCTION -> {
                suggestions.add("api." + mainDomain);
                suggestions.add("www." + mainDomain);
                suggestions.add("app." + mainDomain);
            }
        }
        
        // Common suggestions
        suggestions.addAll(Arrays.asList(
            mainDomain,
            "api." + mainDomain,
            "app." + mainDomain,
            "admin." + mainDomain
        ));
        
        return suggestions.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * Validate domain ownership for API key usage
     */
    public boolean validateDomainOwnership(String requestDomain, String userId) {
        List<ApiKey> userKeys = apiKeyRepository.findByUserFkId(userId);
        
        return userKeys.stream()
                .anyMatch(key -> key.isValidDomain(requestDomain));
    }
    
    /**
     * Result class for domain processing
     */
    public static class DomainProcessingResult {
        private String requestedDomain;
        private String mainDomain;
        private String registeredDomain;
        private String subdomainPattern;
        private boolean isSubdomain;
        private boolean mainDomainExists;
        private boolean needsMainDomainRegistration;
        private boolean autoRegisteredMainDomain;
        private ApiKey mainDomainApiKey;
        private ApiKeyEnvironment environment;
        private List<String> warnings = new ArrayList<>();
        
        // Getters and setters
        public String getRequestedDomain() { return requestedDomain; }
        public void setRequestedDomain(String requestedDomain) { this.requestedDomain = requestedDomain; }
        
        public String getMainDomain() { return mainDomain; }
        public void setMainDomain(String mainDomain) { this.mainDomain = mainDomain; }
        
        public String getRegisteredDomain() { return registeredDomain; }
        public void setRegisteredDomain(String registeredDomain) { this.registeredDomain = registeredDomain; }
        
        public String getSubdomainPattern() { return subdomainPattern; }
        public void setSubdomainPattern(String subdomainPattern) { this.subdomainPattern = subdomainPattern; }
        
        public boolean isSubdomain() { return isSubdomain; }
        public void setIsSubdomain(boolean isSubdomain) { this.isSubdomain = isSubdomain; }
        
        public boolean isMainDomainExists() { return mainDomainExists; }
        public void setMainDomainExists(boolean mainDomainExists) { this.mainDomainExists = mainDomainExists; }
        
        public boolean isNeedsMainDomainRegistration() { return needsMainDomainRegistration; }
        public void setNeedsMainDomainRegistration(boolean needsMainDomainRegistration) { 
            this.needsMainDomainRegistration = needsMainDomainRegistration; 
        }
        
        public boolean isAutoRegisteredMainDomain() { return autoRegisteredMainDomain; }
        public void setAutoRegisteredMainDomain(boolean autoRegisteredMainDomain) { 
            this.autoRegisteredMainDomain = autoRegisteredMainDomain; 
        }
        
        public ApiKey getMainDomainApiKey() { return mainDomainApiKey; }
        public void setMainDomainApiKey(ApiKey mainDomainApiKey) { this.mainDomainApiKey = mainDomainApiKey; }
        
        public ApiKeyEnvironment getEnvironment() { return environment; }
        public void setEnvironment(ApiKeyEnvironment environment) { this.environment = environment; }
        
        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }
        
        @Override
        public String toString() {
            return String.format("DomainProcessingResult{requested='%s', main='%s', registered='%s', isSubdomain=%s, mainExists=%s}", 
                    requestedDomain, mainDomain, registeredDomain, isSubdomain, mainDomainExists);
        }
    }
}