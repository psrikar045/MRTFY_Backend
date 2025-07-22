package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity to track API key rate limiting in database.
 * This provides persistent rate limiting that survives application restarts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_key_rate_limits", indexes = {
    @Index(name = "idx_rate_limits_api_key_hash", columnList = "apiKeyHash"),
    @Index(name = "idx_rate_limits_window", columnList = "windowStart, windowEnd")
})
public class ApiKeyRateLimit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "api_key_hash", nullable = false, length = 255)
    private String apiKeyHash; // Reference to the API key hash
    
    @Column(name = "request_count", nullable = false)
    private Integer requestCount = 0;
    
    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;
    
    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (windowStart == null) {
            windowStart = LocalDateTime.now();
        }
        if (windowEnd == null) {
            // Default 1-hour window
            windowEnd = windowStart.plusHours(1);
        }
    }
    
    /**
     * Increment the request count for this rate limit window.
     */
    public void incrementRequestCount() {
        this.requestCount++;
    }
    
    /**
     * Check if this rate limit window is currently active.
     * @param now The current time
     * @return true if the window is active, false if expired
     */
    public boolean isActiveWindow(LocalDateTime now) {
        return now.isAfter(windowStart) && now.isBefore(windowEnd);
    }
    
    /**
     * Check if this rate limit window has expired.
     * @param now The current time
     * @return true if the window has expired, false otherwise
     */
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(windowEnd);
    }
}