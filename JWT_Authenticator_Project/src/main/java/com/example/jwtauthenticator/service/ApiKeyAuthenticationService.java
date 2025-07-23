package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service for API Key authentication and validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyHashUtil apiKeyHashUtil;

    /**
     * Authenticate and validate an API key
     * 
     * @param apiKeyValue The raw API key value from the request
     * @return ApiKeyAuthResult containing authentication result and user info
     */
    public ApiKeyAuthResult authenticateApiKey(String apiKeyValue) {
        if (apiKeyValue == null || apiKeyValue.trim().isEmpty()) {
            log.debug("API key is null or empty");
            return ApiKeyAuthResult.failed("API key is required");
        }

        try {
            // Hash the provided API key to compare with stored hash
            String keyHash = apiKeyHashUtil.hashApiKey(apiKeyValue.trim());
            
            // Find the API key by hash
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(keyHash);
            
            if (apiKeyOpt.isEmpty()) {
                log.debug("API key not found for hash: {}", keyHash.substring(0, 10) + "...");
                return ApiKeyAuthResult.failed("Invalid API key");
            }

            ApiKey apiKey = apiKeyOpt.get();

            // Check if API key is active
            if (!apiKey.isActive()) {
                log.debug("API key {} is inactive", apiKey.getId());
                return ApiKeyAuthResult.failed("API key is inactive");
            }

            // Check if API key is expired
            if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.debug("API key {} is expired. Expired at: {}", apiKey.getId(), apiKey.getExpiresAt());
                return ApiKeyAuthResult.failed("API key is expired");
            }

            // Get the user associated with this API key
            Optional<User> userOpt = userRepository.findById(apiKey.getUserFkId());
            if (userOpt.isEmpty()) {
                log.error("User not found for API key {}. User ID: {}", apiKey.getId(), apiKey.getUserFkId());
                return ApiKeyAuthResult.failed("Associated user not found");
            }

            User user = userOpt.get();

            // Check if user is active (if User entity has isActive field)
            // Assuming User entity might have an active status
            // if (!user.isActive()) {
            //     log.debug("User {} associated with API key {} is inactive", user.getId(), apiKey.getId());
            //     return ApiKeyAuthResult.failed("Associated user is inactive");
            // }

            log.debug("API key {} authenticated successfully for user {}", apiKey.getId(), user.getId());
            
            return ApiKeyAuthResult.success(apiKey, user);

        } catch (Exception e) {
            log.error("Error during API key authentication", e);
            return ApiKeyAuthResult.failed("Authentication error");
        }
    }

    /**
     * Validate API key scopes against required scopes
     * 
     * @param apiKey The authenticated API key
     * @param requiredScopes List of required scopes for the operation
     * @return true if API key has all required scopes, false otherwise
     */
    public boolean validateScopes(ApiKey apiKey, List<String> requiredScopes) {
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true; // No scopes required
        }

        if (apiKey.getScopes() == null || apiKey.getScopes().trim().isEmpty()) {
            log.debug("API key {} has no scopes but scopes are required: {}", apiKey.getId(), requiredScopes);
            return false; // API key has no scopes but scopes are required
        }

        List<String> apiKeyScopes = Arrays.asList(apiKey.getScopes().split(","));
        
        for (String requiredScope : requiredScopes) {
            if (!apiKeyScopes.contains(requiredScope.trim())) {
                log.debug("API key {} missing required scope: {}", apiKey.getId(), requiredScope);
                return false;
            }
        }

        return true;
    }

    /**
     * Validate IP restrictions for the API key
     * 
     * @param apiKey The authenticated API key
     * @param clientIp The client's IP address
     * @return true if IP is allowed, false otherwise
     */
    public boolean validateIpRestriction(ApiKey apiKey, String clientIp) {
        if (apiKey.getAllowedIps() == null || apiKey.getAllowedIps().trim().isEmpty()) {
            return true; // No IP restrictions
        }

        if (clientIp == null || clientIp.trim().isEmpty()) {
            log.debug("Client IP is null but API key {} has IP restrictions", apiKey.getId());
            return false;
        }

        List<String> allowedIps = Arrays.asList(apiKey.getAllowedIps().split(","));
        boolean isAllowed = allowedIps.stream()
                .anyMatch(ip -> ip.trim().equals(clientIp.trim()));

        if (!isAllowed) {
            log.debug("Client IP {} not allowed for API key {}. Allowed IPs: {}", 
                    clientIp, apiKey.getId(), apiKey.getAllowedIps());
        }

        return isAllowed;
    }

    /**
     * Result class for API key authentication
     */
    public static class ApiKeyAuthResult {
        private final boolean success;
        private final String errorMessage;
        private final ApiKey apiKey;
        private final User user;

        private ApiKeyAuthResult(boolean success, String errorMessage, ApiKey apiKey, User user) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.apiKey = apiKey;
            this.user = user;
        }

        public static ApiKeyAuthResult success(ApiKey apiKey, User user) {
            return new ApiKeyAuthResult(true, null, apiKey, user);
        }

        public static ApiKeyAuthResult failed(String errorMessage) {
            return new ApiKeyAuthResult(false, errorMessage, null, null);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public ApiKey getApiKey() { return apiKey; }
        public User getUser() { return user; }
        public String getUserId() { return user != null ? user.getId() : null; }
    }
}
