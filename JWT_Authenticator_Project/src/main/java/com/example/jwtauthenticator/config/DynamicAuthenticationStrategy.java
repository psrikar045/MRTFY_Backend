package com.example.jwtauthenticator.config;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.service.ApiKeyService;
import com.example.jwtauthenticator.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Dynamic authentication strategy that supports multiple authentication methods
 * without requiring code changes. Configuration-driven approach for maximum flexibility.
 * 
 * Supports:
 * - API Key authentication (X-API-Key header or Authorization header)
 * - JWT Bearer token authentication
 * - Configurable fallback between methods
 * - Environment-specific authentication strategies
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicAuthenticationStrategy {

    private final ApiKeyService apiKeyService;
    private final JwtUtil jwtUtil;

    @Value("${app.auth.method:both}")
    private String authMethod;

    @Value("${app.auth.api-key-header:X-API-Key}")
    private String apiKeyHeader;

    @Value("${app.auth.jwt-header:Authorization}")
    private String jwtHeader;

    @Value("${app.auth.fallback:true}")
    private boolean allowFallback;

    @Value("${app.auth.api-key-prefix:}")
    private String apiKeyPrefix;

    @Value("${app.auth.jwt-prefix:Bearer }")
    private String jwtPrefix;

    /**
     * Authenticate request using configured strategy
     */
    public AuthenticationResult authenticate(HttpServletRequest request) {
        List<AuthMethod> enabledMethods = getEnabledAuthMethods();
        
        log.debug("Attempting authentication with methods: {} (fallback: {})", 
                 enabledMethods, allowFallback);

        AuthenticationResult lastError = null;

        for (AuthMethod method : enabledMethods) {
            try {
                AuthenticationResult result = executeAuthMethod(method, request);
                if (result.isSuccess()) {
                    log.debug("Authentication successful using method: {}", method);
                    return result;
                }
                lastError = result;
            } catch (Exception e) {
                log.debug("Authentication method {} failed: {}", method, e.getMessage());
                lastError = AuthenticationResult.failure(
                    "Authentication failed with method: " + method, 
                    method
                );
                
                if (!allowFallback) {
                    log.debug("Fallback disabled, stopping at first failure");
                    break;
                }
            }
        }

        log.debug("All authentication methods failed");
        return lastError != null ? lastError : 
               AuthenticationResult.failure("No valid authentication found", null);
    }

    /**
     * Get enabled authentication methods based on configuration
     */
    private List<AuthMethod> getEnabledAuthMethods() {
        return switch (authMethod.toLowerCase()) {
            case "api_key", "apikey" -> Arrays.asList(AuthMethod.API_KEY);
            case "jwt", "bearer" -> Arrays.asList(AuthMethod.JWT);
            case "both", "all" -> Arrays.asList(AuthMethod.API_KEY, AuthMethod.JWT);
            case "jwt_first" -> Arrays.asList(AuthMethod.JWT, AuthMethod.API_KEY);
            default -> {
                log.warn("Unknown auth method '{}', defaulting to 'both'", authMethod);
                yield Arrays.asList(AuthMethod.API_KEY, AuthMethod.JWT);
            }
        };
    }

    /**
     * Execute specific authentication method
     */
    private AuthenticationResult executeAuthMethod(AuthMethod method, HttpServletRequest request) {
        return switch (method) {
            case API_KEY -> authenticateWithApiKey(request);
            case JWT -> authenticateWithJWT(request);
        };
    }

    /**
     * Authenticate using API key
     */
    private AuthenticationResult authenticateWithApiKey(HttpServletRequest request) {
        String apiKey = extractApiKey(request);
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return AuthenticationResult.failure("API key not found", AuthMethod.API_KEY);
        }

        try {
            Optional<ApiKey> apiKeyEntity = apiKeyService.validateApiKey(apiKey);
            
            if (apiKeyEntity.isEmpty()) {
                return AuthenticationResult.failure("Invalid API key", AuthMethod.API_KEY);
            }

            ApiKey key = apiKeyEntity.get();
            
            if (!key.isActive()) {
                return AuthenticationResult.failure("API key is inactive", AuthMethod.API_KEY);
            }

            if (key.isExpired()) {
                return AuthenticationResult.failure("API key has expired", AuthMethod.API_KEY);
            }

            if (key.getRevokedAt() != null) {
                return AuthenticationResult.failure("API key has been revoked", AuthMethod.API_KEY);
            }

            return AuthenticationResult.success(
                key.getUserFkId(),
                key,
                AuthMethod.API_KEY,
                "API key authentication successful"
            );

        } catch (Exception e) {
            log.error("API key authentication error", e);
            return AuthenticationResult.failure("API key authentication failed", AuthMethod.API_KEY);
        }
    }

    /**
     * Authenticate using JWT token
     */
    private AuthenticationResult authenticateWithJWT(HttpServletRequest request) {
        String token = extractJwtToken(request);
        
        if (token == null || token.trim().isEmpty()) {
            return AuthenticationResult.failure("JWT token not found", AuthMethod.JWT);
        }

        try {
            // Check if token is expired
            if (jwtUtil.isTokenExpired(token)) {
                return AuthenticationResult.failure("JWT token has expired", AuthMethod.JWT);
            }

            // Extract user ID from token
            String userId = jwtUtil.extractUserId(token);
            
            if (userId == null || userId.trim().isEmpty()) {
                // Try alternative extraction method
                userId = jwtUtil.extractUserID(token);
                if (userId == null || userId.trim().isEmpty()) {
                    return AuthenticationResult.failure("Unable to extract user ID from JWT token", AuthMethod.JWT);
                }
            }

            return AuthenticationResult.success(
                userId,
                null, // No API key for JWT auth
                AuthMethod.JWT,
                "JWT authentication successful"
            );

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
            return AuthenticationResult.failure("JWT token has expired", AuthMethod.JWT);
        } catch (io.jsonwebtoken.SignatureException e) {
            log.debug("JWT signature validation failed: {}", e.getMessage());
            return AuthenticationResult.failure("Invalid JWT token signature", AuthMethod.JWT);
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.debug("Malformed JWT token: {}", e.getMessage());
            return AuthenticationResult.failure("Malformed JWT token", AuthMethod.JWT);
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            log.debug("Unsupported JWT token: {}", e.getMessage());
            return AuthenticationResult.failure("Unsupported JWT token", AuthMethod.JWT);
        } catch (IllegalArgumentException e) {
            log.debug("JWT token validation error: {}", e.getMessage());
            return AuthenticationResult.failure("Invalid JWT token format", AuthMethod.JWT);
        } catch (Exception e) {
            log.error("Unexpected JWT authentication error", e);
            return AuthenticationResult.failure("JWT authentication failed", AuthMethod.JWT);
        }
    }

    /**
     * Extract API key from request headers
     */
    private String extractApiKey(HttpServletRequest request) {
        // Try configured API key header first
        String apiKey = request.getHeader(apiKeyHeader);
        
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return removePrefix(apiKey, apiKeyPrefix);
        }

        // Fallback to Authorization header if no prefix is expected for JWT
        if (apiKeyHeader.equals("Authorization") || "Authorization".equals(apiKeyHeader)) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && !authHeader.startsWith(jwtPrefix)) {
                return removePrefix(authHeader, apiKeyPrefix);
            }
        }

        return null;
    }

    /**
     * Extract JWT token from request headers
     */
    private String extractJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader(jwtHeader);
        
        if (authHeader != null && authHeader.startsWith(jwtPrefix)) {
            return authHeader.substring(jwtPrefix.length()).trim();
        }

        return null;
    }

    /**
     * Remove prefix from token if present
     */
    private String removePrefix(String token, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return token;
        }
        
        if (token.startsWith(prefix)) {
            return token.substring(prefix.length()).trim();
        }
        
        return token;
    }

    /**
     * Check if API key authentication is enabled
     */
    public boolean isApiKeyAuthEnabled() {
        return getEnabledAuthMethods().contains(AuthMethod.API_KEY);
    }

    /**
     * Check if JWT authentication is enabled
     */
    public boolean isJwtAuthEnabled() {
        return getEnabledAuthMethods().contains(AuthMethod.JWT);
    }

    /**
     * Get current authentication configuration
     */
    public AuthConfig getAuthConfig() {
        return AuthConfig.builder()
                .authMethod(authMethod)
                .apiKeyHeader(apiKeyHeader)
                .jwtHeader(jwtHeader)
                .allowFallback(allowFallback)
                .apiKeyPrefix(apiKeyPrefix)
                .jwtPrefix(jwtPrefix)
                .enabledMethods(getEnabledAuthMethods())
                .build();
    }

    /**
     * Authentication methods enum
     */
    public enum AuthMethod {
        API_KEY("API Key"),
        JWT("JWT Bearer Token");

        private final String displayName;

        AuthMethod(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Authentication result class
     */
    public static class AuthenticationResult {
        private final boolean success;
        private final String userId;
        private final ApiKey apiKey;
        private final AuthMethod method;
        private final String message;

        private AuthenticationResult(boolean success, String userId, ApiKey apiKey, 
                                   AuthMethod method, String message) {
            this.success = success;
            this.userId = userId;
            this.apiKey = apiKey;
            this.method = method;
            this.message = message;
        }

        public static AuthenticationResult success(String userId, ApiKey apiKey, 
                                                 AuthMethod method, String message) {
            return new AuthenticationResult(true, userId, apiKey, method, message);
        }

        public static AuthenticationResult failure(String message, AuthMethod method) {
            return new AuthenticationResult(false, null, null, method, message);
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getUserId() { return userId; }
        public ApiKey getApiKey() { return apiKey; }
        public AuthMethod getMethod() { return method; }
        public String getMessage() { return message; }
    }

    /**
     * Authentication configuration class
     */
    @lombok.Builder
    @lombok.Data
    public static class AuthConfig {
        private String authMethod;
        private String apiKeyHeader;
        private String jwtHeader;
        private boolean allowFallback;
        private String apiKeyPrefix;
        private String jwtPrefix;
        private List<AuthMethod> enabledMethods;
    }
}