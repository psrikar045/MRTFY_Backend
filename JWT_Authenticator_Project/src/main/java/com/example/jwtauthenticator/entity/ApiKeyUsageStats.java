package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to track API key usage statistics and rate limiting
 * Professional approach with time-window based rate limiting
 */
@Entity
@Table(name = "api_key_usage_stats", indexes = {
    @Index(name = "idx_usage_stats_api_key_id", columnList = "apiKeyId"),
    @Index(name = "idx_usage_stats_window_start", columnList = "windowStart"),
    @Index(name = "idx_usage_stats_api_key_window", columnList = "apiKeyId, windowStart")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyUsageStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "api_key_id", nullable = false)
    private String apiKeyId;

    @Column(name = "user_fk_id", nullable = false)
    private String userFkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_limit_tier", nullable = false)
    private RateLimitTier rateLimitTier;

    // Time window tracking
    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;

    // Request tracking
    @Column(name = "request_count", nullable = false)
    private Integer requestCount = 0;

    @Column(name = "request_limit", nullable = false)
    private Integer requestLimit;

    @Column(name = "remaining_requests", nullable = false)
    private Integer remainingRequests;

    // Timestamps
    @Column(name = "first_request_at")
    private LocalDateTime firstRequestAt;

    @Column(name = "last_request_at")
    private LocalDateTime lastRequestAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Statistics
    @Column(name = "total_requests_lifetime")
    private Long totalRequestsLifetime = 0L;

    @Column(name = "blocked_requests")
    private Integer blockedRequests = 0;

    @Column(name = "peak_requests_per_minute")
    private Integer peakRequestsPerMinute = 0;

    // Status tracking
    @Column(name = "is_rate_limited")
    private Boolean isRateLimited = false;

    @Column(name = "rate_limit_reset_at")
    private LocalDateTime rateLimitResetAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Check if the current window is still valid
     */
    public boolean isCurrentWindowValid() {
        LocalDateTime now = LocalDateTime.now();
        return now.isBefore(windowEnd) && now.isAfter(windowStart);
    }

    /**
     * Check if rate limit is exceeded
     */
    public boolean isRateLimitExceeded() {
        return requestCount >= requestLimit && !rateLimitTier.isUnlimited();
    }

    /**
     * Get usage percentage
     */
    public double getUsagePercentage() {
        if (rateLimitTier.isUnlimited()) {
            return 0.0;
        }
        return (double) requestCount / requestLimit * 100.0;
    }

    /**
     * Increment request count
     */
    public void incrementRequestCount() {
        this.requestCount++;
        this.totalRequestsLifetime++;
        this.remainingRequests = Math.max(0, requestLimit - requestCount);
        this.lastRequestAt = LocalDateTime.now();
        
        if (this.firstRequestAt == null) {
            this.firstRequestAt = LocalDateTime.now();
        }
        
        // Update rate limit status
        this.isRateLimited = isRateLimitExceeded();
        if (this.isRateLimited) {
            this.rateLimitResetAt = this.windowEnd;
        }
    }

    /**
     * Increment blocked request count
     */
    public void incrementBlockedRequests() {
        this.blockedRequests++;
    }

    /**
     * Reset for new window
     */
    public void resetForNewWindow(LocalDateTime newWindowStart, LocalDateTime newWindowEnd) {
        this.windowStart = newWindowStart;
        this.windowEnd = newWindowEnd;
        this.requestCount = 0;
        this.remainingRequests = requestLimit;
        this.isRateLimited = false;
        this.rateLimitResetAt = null;
        this.firstRequestAt = null;
    }
}