package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

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

    @Column(name = "allowed_domains", columnDefinition = "TEXT")
    private String allowedDomains;

    @Column(name = "rate_limit_tier", length = 50)
    private String rateLimitTier;
    
    @Column(name = "scopes", columnDefinition = "TEXT")
    private String scopes; // Comma-separated list of granted permissions/scopes
    
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
    
}