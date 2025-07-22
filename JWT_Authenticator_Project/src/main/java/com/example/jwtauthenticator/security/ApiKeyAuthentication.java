package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.enums.ApiKeyScope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Spring Security Authentication implementation for API key authentication.
 * This represents an authenticated API key with its associated scopes and permissions.
 */
public class ApiKeyAuthentication implements Authentication {
    
    private final String apiKeyHash;
    private final String userId;
    private final String keyName;
    private final ApiKeyScope[] scopes;
    private final Collection<? extends GrantedAuthority> authorities;
    private boolean authenticated = true;
    
    public ApiKeyAuthentication(String apiKeyHash, String userId, String keyName, ApiKeyScope[] scopes) {
        this.apiKeyHash = apiKeyHash;
        this.userId = userId;
        this.keyName = keyName;
        this.scopes = scopes != null ? scopes : new ApiKeyScope[0];
        
        // Convert scopes to Spring Security authorities
        this.authorities = Arrays.stream(this.scopes)
            .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.name()))
            .collect(Collectors.toList());
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public Object getCredentials() {
        return null; // We don't expose the actual API key after authentication
    }
    
    @Override
    public Object getDetails() {
        return new ApiKeyDetails(apiKeyHash, userId, keyName, scopes);
    }
    
    @Override
    public Object getPrincipal() {
        return userId; // The user ID who owns this API key
    }
    
    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }
    
    @Override
    public String getName() {
        return keyName != null ? keyName : "API Key";
    }
    
    /**
     * Check if this authentication has a specific scope.
     * @param scope The scope to check
     * @return true if this API key has the specified scope
     */
    public boolean hasScope(ApiKeyScope scope) {
        return Arrays.asList(scopes).contains(scope) || 
               Arrays.asList(scopes).contains(ApiKeyScope.FULL_ACCESS);
    }
    
    /**
     * Check if this authentication has any of the specified scopes.
     * @param requiredScopes Array of scopes to check
     * @return true if this API key has at least one of the specified scopes
     */
    public boolean hasAnyScope(ApiKeyScope... requiredScopes) {
        return Arrays.stream(requiredScopes)
            .anyMatch(this::hasScope);
    }
    
    /**
     * Check if this authentication has all of the specified scopes.
     * @param requiredScopes Array of scopes to check
     * @return true if this API key has all of the specified scopes
     */
    public boolean hasAllScopes(ApiKeyScope... requiredScopes) {
        return Arrays.stream(requiredScopes)
            .allMatch(this::hasScope);
    }
    
    /**
     * Get the user ID associated with this API key.
     * @return The user ID
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Get the API key hash (for logging/tracking purposes).
     * @return The API key hash
     */
    public String getApiKeyHash() {
        return apiKeyHash;
    }
    
    /**
     * Get all scopes associated with this API key.
     * @return Array of scopes
     */
    public ApiKeyScope[] getScopes() {
        return scopes.clone(); // Return defensive copy
    }
    
    /**
     * Inner class containing API key details.
     */
    public static class ApiKeyDetails {
        private final String apiKeyHash;
        private final String userId;
        private final String keyName;
        private final ApiKeyScope[] scopes;
        
        public ApiKeyDetails(String apiKeyHash, String userId, String keyName, ApiKeyScope[] scopes) {
            this.apiKeyHash = apiKeyHash;
            this.userId = userId;
            this.keyName = keyName;
            this.scopes = scopes;
        }
        
        // Getters
        public String getApiKeyHash() { return apiKeyHash; }
        public String getUserId() { return userId; }
        public String getKeyName() { return keyName; }
        public ApiKeyScope[] getScopes() { return scopes; }
    }
}