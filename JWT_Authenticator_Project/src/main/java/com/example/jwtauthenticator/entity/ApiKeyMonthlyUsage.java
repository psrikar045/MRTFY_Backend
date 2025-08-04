package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Entity for tracking monthly API usage per API key
 * Used for quota enforcement and usage analytics
 */
@Entity
@Table(name = "api_key_monthly_usage", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"api_key_id", "month_year"}),
       indexes = {
           @Index(name = "idx_api_key_month", columnList = "api_key_id, month_year"),
           @Index(name = "idx_user_month", columnList = "user_id, month_year"),
           @Index(name = "idx_month_year", columnList = "month_year")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyMonthlyUsage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(name = "api_key_id", nullable = false)
    private UUID apiKeyId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "month_year", nullable = false, length = 7)
    private String monthYear; // Format: "2024-01"
    
    @Column(name = "total_calls", nullable = false)
    private Integer totalCalls = 0;
    
    @Column(name = "successful_calls", nullable = false)
    private Integer successfulCalls = 0;
    
    @Column(name = "failed_calls", nullable = false)
    private Integer failedCalls = 0;
    
    @Column(name = "quota_exceeded_calls", nullable = false)
    private Integer quotaExceededCalls = 0;
    
    @Column(name = "last_reset_date", nullable = false)
    private LocalDate lastResetDate;
    
    @Column(name = "quota_limit")
    private Integer quotaLimit; // Store the limit at time of creation
    
    @Column(name = "grace_limit")
    private Integer graceLimit; // Store the grace limit
    
    @Column(name = "first_call_at")
    private LocalDateTime firstCallAt;
    
    @Column(name = "last_call_at")
    private LocalDateTime lastCallAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Helper methods for incrementing counters
    public void incrementTotalCalls() {
        this.totalCalls = (this.totalCalls == null ? 0 : this.totalCalls) + 1;
        this.lastCallAt = LocalDateTime.now();
        
        if (this.firstCallAt == null) {
            this.firstCallAt = this.lastCallAt;
        }
    }
    
    public void incrementSuccessfulCalls() {
        this.successfulCalls = (this.successfulCalls == null ? 0 : this.successfulCalls) + 1;
        incrementTotalCalls();
    }
    
    public void incrementFailedCalls() {
        this.failedCalls = (this.failedCalls == null ? 0 : this.failedCalls) + 1;
        incrementTotalCalls();
    }
    
    public void incrementQuotaExceededCalls() {
        this.quotaExceededCalls = (this.quotaExceededCalls == null ? 0 : this.quotaExceededCalls) + 1;
        incrementTotalCalls();
    }
    
    // Calculation methods
    public double getSuccessRate() {
        if (totalCalls == null || totalCalls == 0) return 0.0;
        return (successfulCalls.doubleValue() / totalCalls.doubleValue()) * 100.0;
    }
    
    public double getFailureRate() {
        if (totalCalls == null || totalCalls == 0) return 0.0;
        return (failedCalls.doubleValue() / totalCalls.doubleValue()) * 100.0;
    }
    
    public int getRemainingCalls() {
        if (quotaLimit == null || quotaLimit == -1) return -1; // Unlimited
        return Math.max(0, quotaLimit - totalCalls);
    }
    
    public int getRemainingGraceCalls() {
        if (graceLimit == null || graceLimit == -1) return -1; // Unlimited
        return Math.max(0, graceLimit - totalCalls);
    }
    
    public boolean isQuotaExceeded() {
        if (quotaLimit == null || quotaLimit == -1) return false; // Unlimited
        return totalCalls >= quotaLimit;
    }
    
    public boolean isGraceExceeded() {
        if (graceLimit == null || graceLimit == -1) return false; // Unlimited
        return totalCalls >= graceLimit;
    }
    
    public double getQuotaUsagePercentage() {
        if (quotaLimit == null || quotaLimit == -1 || quotaLimit == 0) return 0.0;
        return (totalCalls.doubleValue() / quotaLimit.doubleValue()) * 100.0;
    }
    
    // Reset logic
    public boolean needsReset(LocalDate currentDate) {
        if (lastResetDate == null) return true;
        
        // Check if we're in a new month
        LocalDate currentMonthStart = currentDate.withDayOfMonth(1);
        return lastResetDate.isBefore(currentMonthStart);
    }
    
    public void resetForNewMonth(LocalDate resetDate, Integer newQuotaLimit, Integer newGraceLimit) {
        this.totalCalls = 0;
        this.successfulCalls = 0;
        this.failedCalls = 0;
        this.quotaExceededCalls = 0;
        this.lastResetDate = resetDate;
        this.monthYear = resetDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        this.quotaLimit = newQuotaLimit;
        this.graceLimit = newGraceLimit;
        this.firstCallAt = null;
        this.lastCallAt = null;
    }
    
    // Static factory methods
    public static ApiKeyMonthlyUsage createForCurrentMonth(UUID apiKeyId, String userId, 
                                                          Integer quotaLimit, Integer graceLimit) {
        LocalDate now = LocalDate.now();
        return ApiKeyMonthlyUsage.builder()
                .apiKeyId(apiKeyId)
                .userId(userId)
                .monthYear(now.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .totalCalls(0)
                .successfulCalls(0)
                .failedCalls(0)
                .quotaExceededCalls(0)
                .lastResetDate(now.withDayOfMonth(1))
                .quotaLimit(quotaLimit)
                .graceLimit(graceLimit)
                .build();
    }
    
    public static String getCurrentMonthYear() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
    
    public static String getMonthYear(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}