package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing quota reset audit records
 * Tracks all monthly quota reset operations for compliance and monitoring
 * 
 * @author RIVO9 Development Team
 * @version 1.0
 * @since Java 21
 */
@Entity
@Table(name = "quota_reset_audit", indexes = {
    @Index(name = "idx_quota_reset_audit_date", columnList = "resetDate"),
    @Index(name = "idx_quota_reset_audit_month", columnList = "monthYear"),
    @Index(name = "idx_quota_reset_audit_status", columnList = "executionStatus"),
    @Index(name = "idx_quota_reset_audit_triggered_by", columnList = "triggeredBy")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class QuotaResetAudit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "reset_date", nullable = false)
    private LocalDate resetDate;
    
    @Column(name = "execution_timestamp", nullable = false)
    @CreationTimestamp
    private LocalDateTime executionTimestamp;
    
    @Column(name = "records_processed", nullable = false)
    private Integer recordsProcessed = 0;
    
    @Column(name = "records_successful", nullable = false)
    private Integer recordsSuccessful = 0;
    
    @Column(name = "records_failed", nullable = false)
    private Integer recordsFailed = 0;
    
    @Column(name = "records_skipped", nullable = false)
    private Integer recordsSkipped = 0;
    
    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;
    
    @Column(name = "triggered_by", length = 50)
    private String triggeredBy;
    
    @Column(name = "month_year", nullable = false, length = 7)
    private String monthYear;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 20)
    private ExecutionStatus executionStatus;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // Calculated fields
    @Transient
    public double getSuccessRate() {
        if (recordsProcessed == 0) return 100.0;
        return (recordsSuccessful * 100.0) / recordsProcessed;
    }
    
    @Transient
    public boolean isSuccessful() {
        return executionStatus == ExecutionStatus.COMPLETED && recordsFailed == 0;
    }
    
    @Transient
    public String getExecutionDurationFormatted() {
        if (executionDurationMs == null) return "N/A";
        if (executionDurationMs < 1000) return executionDurationMs + "ms";
        return String.format("%.2fs", executionDurationMs / 1000.0);
    }
    
    /**
     * Execution status enumeration
     */
    public enum ExecutionStatus {
        STARTED("Execution Started"),
        IN_PROGRESS("In Progress"),
        COMPLETED("Completed Successfully"),
        COMPLETED_WITH_ERRORS("Completed with Errors"),
        FAILED("Failed"),
        CANCELLED("Cancelled");
        
        private final String description;
        
        ExecutionStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}