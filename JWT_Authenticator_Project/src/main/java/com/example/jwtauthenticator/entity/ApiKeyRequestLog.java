package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity to track API key usage statistics including IP addresses and domains
 * for analytics and security monitoring purposes.
 */
@Entity
@Table(name = "api_key_request_logs", indexes = {
    @Index(name = "idx_request_logs_api_key_id", columnList = "api_key_id"),
    @Index(name = "idx_request_logs_timestamp", columnList = "request_timestamp"),
    @Index(name = "idx_request_logs_client_ip", columnList = "client_ip"),
    @Index(name = "idx_request_logs_domain", columnList = "domain"),
    @Index(name = "idx_request_logs_api_key_timestamp", columnList = "api_key_id, request_timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "api_key_id", nullable = false)
    private UUID apiKeyId;

    @Column(name = "user_fk_id", nullable = false, length = 11)
    private String userFkId;

    @Column(name = "client_ip", length = 45) // IPv6 support
    private String clientIp;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "request_timestamp", nullable = false)
    private LocalDateTime requestTimestamp;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "request_size_bytes")
    private Long requestSizeBytes;

    @Column(name = "response_size_bytes")
    private Long responseSizeBytes;

    @Column(name = "rate_limit_tier", length = 50)
    private String rateLimitTier;

    @Column(name = "rate_limit_remaining")
    private Integer rateLimitRemaining;

    @Column(name = "addon_used")
    private Boolean addonUsed;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "is_allowed_ip")
    private Boolean isAllowedIp;

    @Column(name = "is_allowed_domain")
    private Boolean isAllowedDomain;

    @PrePersist
    protected void onCreate() {
        if (requestTimestamp == null) {
            requestTimestamp = LocalDateTime.now();
        }
    }

    /**
     * Check if the request was from an allowed source
     */
    public boolean isFromAllowedSource() {
        return Boolean.TRUE.equals(isAllowedIp) && Boolean.TRUE.equals(isAllowedDomain);
    }

    /**
     * Check if the request had any security violations
     */
    public boolean hasSecurityViolation() {
        return Boolean.FALSE.equals(isAllowedIp) || Boolean.FALSE.equals(isAllowedDomain);
    }

    /**
     * Get location string for display
     */
    public String getLocationString() {
        StringBuilder location = new StringBuilder();
        if (city != null && !city.trim().isEmpty()) {
            location.append(city);
        }
        if (region != null && !region.trim().isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(region);
        }
        if (countryCode != null && !countryCode.trim().isEmpty()) {
            if (location.length() > 0) location.append(", ");
            location.append(countryCode);
        }
        return location.length() > 0 ? location.toString() : "Unknown";
    }
}