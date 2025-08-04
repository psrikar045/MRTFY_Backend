package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.enums.ApiKeyEnvironment;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_keys", schema = "public")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "key_hash", unique = true, nullable = false, length = 255)
    private String keyHash; // Store SHA-256 hash of the actual key

    // --- UPDATED: userId type is String, and column name matches the FK ---
    @Column(name = "user_fk_id", nullable = false, length = 11) // Matches public.users.id (varchar(11))
    private String userFkId; // Renamed to clearly indicate it's the FK to users.id
    // --- END UPDATED ---

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "prefix", length = 10)
    private String prefix;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "allowed_ips", columnDefinition = "TEXT")
    private String allowedIps;

    @Column(name = "registered_domain", length = 255)
    private String registeredDomain; // Primary domain for this API key (unique)

    @Column(name = "allowed_domains", columnDefinition = "TEXT")
    private String allowedDomains; // Additional domains (for future hybrid approach)

    @Column(name = "rate_limit_tier", length = 50)
    @Enumerated(EnumType.STRING)
    private RateLimitTier rateLimitTier;
    
    @Column(name = "scopes", columnDefinition = "TEXT")
    private String scopes; // Comma-separated list of granted permissions/scopes
    
    // Environment and subdomain support
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false)
    private ApiKeyEnvironment environment = ApiKeyEnvironment.PRODUCTION;
    
    @Column(name = "subdomain_pattern")
    private String subdomainPattern; // e.g., "*.xamplyfy.com" or specific patterns
    
    @Column(name = "main_domain")
    private String mainDomain; // Extracted main domain (e.g., "xamplyfy.com" from "dev.xamplyfy.com")
    
    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Helper methods for allowed_ips/domains remain the same
    public List<String> getAllowedIpsAsList() {
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(allowedIps.split(","))
                     .map(String::trim)
                     .collect(Collectors.toList());
    }

    public void setAllowedIpsAsList(List<String> ips) {
        if (ips == null || ips.isEmpty()) {
            this.allowedIps = null;
        } else {
            this.allowedIps = ips.stream().collect(Collectors.joining(","));
        }
    }

    public List<String> getAllowedDomainsAsList() {
        if (allowedDomains == null || allowedDomains.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(allowedDomains.split(","))
                     .map(String::trim)
                     .collect(Collectors.toList());
    }

    public void setAllowedDomainsAsList(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            this.allowedDomains = null;
        } else {
            this.allowedDomains = domains.stream().collect(Collectors.joining(","));
        }
    }
    
    // --- NEW HELPER FOR SCOPES ---
    public List<String> getScopesAsList() {
        if (scopes == null || scopes.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(scopes.split(","))
                     .map(String::trim)
                     .map(String::toUpperCase) // Conventionally scopes are uppercase
                     .collect(Collectors.toList());
    }

    public void setScopesAsList(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            this.scopes = null;
        } else {
            this.scopes = scopes.stream()
                                .map(String::trim)
                                .map(String::toUpperCase)
                                .collect(Collectors.joining(","));
        }
    }
    
    /**
     * Check if the API key has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * Check if the API key is currently valid (active, not expired, not revoked)
     */
    public boolean isValid() {
        return isActive && !isExpired() && revokedAt == null;
    }
    
    /**
     * Get the active status (for compatibility)
     */
    public Boolean getIsActive() {
        return isActive;
    }
    
    /**
     * Check if the API key is active (standard boolean getter)
     */
    public boolean isActive() {
        return isActive;
    }
    
    // --- NEW DOMAIN MANAGEMENT METHODS ---
    
    /**
     * Get the registered domain (primary domain for this API key)
     */
    public String getRegisteredDomain() {
        return registeredDomain;
    }
    
    /**
     * Set the registered domain with normalization
     */
    public void setRegisteredDomain(String domain) {
        this.registeredDomain = normalizeDomain(domain);
    }
    
    /**
     * Check if a request domain is allowed for this API key
     * Phase 1: Exact match only for registered domain and additional allowed domains
     * Future: Will support configurable subdomain matching per API key
     */
    public boolean isDomainAllowed(String requestDomain) {
        if (requestDomain == null || requestDomain.trim().isEmpty()) {
            return false;
        }
        
        String normalizedRequestDomain = normalizeDomain(requestDomain);
        
        // Check primary registered domain first
        if (isExactDomainMatch(normalizedRequestDomain, registeredDomain)) {
            return true;
        }
        
        // Check additional allowed domains (existing functionality preserved)
        return getAllowedDomainsAsList().stream()
                .anyMatch(domain -> isExactDomainMatch(normalizedRequestDomain, domain));
    }
    
    /**
     * Normalize domain for consistent comparison
     * - Convert to lowercase
     * - Remove www prefix
     * - Trim whitespace
     */
    private String normalizeDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }
        
        return domain.toLowerCase()
                    .replaceFirst("^www\\.", "") // Remove www prefix
                    .trim();
    }
    
    /**
     * Check for exact domain match (Phase 1 implementation)
     * Future: Will be enhanced to support configurable wildcard matching
     */
    private boolean isExactDomainMatch(String requestDomain, String allowedDomain) {
        if (allowedDomain == null || allowedDomain.trim().isEmpty()) {
            return false;
        }
        
        String normalizedAllowedDomain = normalizeDomain(allowedDomain);
        return normalizedAllowedDomain.equalsIgnoreCase(requestDomain);
    }
    
    /**
     * Get domain validation info for debugging/logging
     */
    public String getDomainValidationInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Registered Domain: ").append(registeredDomain != null ? registeredDomain : "None");
        
        List<String> additionalDomains = getAllowedDomainsAsList();
        if (!additionalDomains.isEmpty()) {
            info.append(", Additional Domains: ").append(String.join(", ", additionalDomains));
        }
        
        return info.toString();
    }
    
    // Domain validation methods
    public boolean isValidDomain(String requestDomain) {
        if (requestDomain == null || requestDomain.trim().isEmpty()) {
            return false;
        }
        
        requestDomain = requestDomain.trim().toLowerCase();
        
        // 1. Exact match with registered domain
        if (registeredDomain != null && registeredDomain.equalsIgnoreCase(requestDomain)) {
            return true;
        }
        
        // 2. Check main domain match
        if (mainDomain != null && mainDomain.equalsIgnoreCase(requestDomain)) {
            return true;
        }
        
        // 3. Subdomain pattern matching
        if (subdomainPattern != null && matchesSubdomainPattern(requestDomain)) {
            return true;
        }
        
        // 4. Check allowed domains list
        if (getAllowedDomainsAsList().contains(requestDomain)) {
            return true;
        }
        
        // 5. Environment-specific validation for development domains
        if (environment == ApiKeyEnvironment.DEVELOPMENT && isDevelopmentDomain(requestDomain)) {
            return true;
        }
        
        return false;
    }
    
    private boolean matchesSubdomainPattern(String requestDomain) {
        if (subdomainPattern == null || subdomainPattern.trim().isEmpty()) {
            return false;
        }
        
        String pattern = subdomainPattern.trim().toLowerCase();
        requestDomain = requestDomain.toLowerCase();
        
        // Handle wildcard patterns like "*.xamplyfy.com"
        if (pattern.startsWith("*.")) {
            String baseDomain = pattern.substring(2);
            if (requestDomain.endsWith("." + baseDomain)) {
                String subdomain = requestDomain.substring(0, requestDomain.length() - baseDomain.length() - 1);
                return isValidSubdomainForEnvironment(subdomain);
            }
            // Also allow the base domain itself
            return requestDomain.equals(baseDomain);
        }
        
        // Exact pattern match
        return pattern.equals(requestDomain);
    }
    
    private boolean isValidSubdomainForEnvironment(String subdomain) {
        if (environment == null) {
            return true; // Allow any subdomain if environment is not set
        }
        
        return environment.isValidSubdomainPrefix(subdomain);
    }
    
    private boolean isDevelopmentDomain(String domain) {
        if (domain == null) return false;
        
        domain = domain.toLowerCase();
        return domain.equals("localhost") || 
               domain.startsWith("127.0.0.1") || 
               domain.equals("::1") || 
               domain.endsWith(".local") ||
               domain.endsWith(".dev") ||
               domain.contains("localhost");
    }
    
    public boolean isSubdomainOf(String potentialSubdomain) {
        if (mainDomain == null || potentialSubdomain == null) {
            return false;
        }
        
        String subdomain = potentialSubdomain.toLowerCase();
        String main = mainDomain.toLowerCase();
        
        return subdomain.endsWith("." + main) && subdomain.length() > main.length() + 1;
    }
    
    public String getSubdomainPrefix(String fullDomain) {
        if (!isSubdomainOf(fullDomain)) {
            return null;
        }
        
        return fullDomain.substring(0, fullDomain.length() - mainDomain.length() - 1);
    }
    
    // Helper method to check if this API key allows subdomain creation
    public boolean allowsSubdomainCreation() {
        return subdomainPattern != null && subdomainPattern.contains("*");
    }
    
    // Get all valid domains for this API key
    public List<String> getAllValidDomains() {
        List<String> validDomains = new ArrayList<>();
        
        if (registeredDomain != null) {
            validDomains.add(registeredDomain);
        }
        
        if (mainDomain != null && !mainDomain.equals(registeredDomain)) {
            validDomains.add(mainDomain);
        }
        
        validDomains.addAll(getAllowedDomainsAsList());
        
        if (subdomainPattern != null) {
            validDomains.add(subdomainPattern);
        }
        
        return validDomains.stream().distinct().collect(Collectors.toList());
    }
    
}