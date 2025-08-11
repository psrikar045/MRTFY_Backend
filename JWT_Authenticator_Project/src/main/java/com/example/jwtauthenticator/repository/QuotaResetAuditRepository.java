package com.example.jwtauthenticator.repository;

import com.example.jwtauthenticator.entity.QuotaResetAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for QuotaResetAudit entity
 * Provides enterprise-grade data access methods for quota reset audit operations
 * 
 * @author RIVO9 Development Team
 * @version 1.0
 * @since Java 21
 */
@Repository
public interface QuotaResetAuditRepository extends JpaRepository<QuotaResetAudit, UUID> {
    
    /**
     * Find audit records by month-year
     */
    List<QuotaResetAudit> findByMonthYearOrderByExecutionTimestampDesc(String monthYear);
    
    /**
     * Find audit records by reset date
     */
    List<QuotaResetAudit> findByResetDateOrderByExecutionTimestampDesc(LocalDate resetDate);
    
    /**
     * Find audit records by execution status
     */
    List<QuotaResetAudit> findByExecutionStatusOrderByExecutionTimestampDesc(QuotaResetAudit.ExecutionStatus status);
    
    /**
     * Find audit records triggered by specific source
     */
    List<QuotaResetAudit> findByTriggeredByOrderByExecutionTimestampDesc(String triggeredBy);
    
    /**
     * Find recent audit records with pagination
     */
    Page<QuotaResetAudit> findAllByOrderByExecutionTimestampDesc(Pageable pageable);
    
    /**
     * Find audit records within date range
     */
    @Query("SELECT qa FROM QuotaResetAudit qa WHERE qa.executionTimestamp BETWEEN :startDate AND :endDate ORDER BY qa.executionTimestamp DESC")
    List<QuotaResetAudit> findByExecutionTimestampBetween(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Find the most recent successful reset for a specific month
     */
    @Query("SELECT qa FROM QuotaResetAudit qa WHERE qa.monthYear = :monthYear AND qa.executionStatus = 'COMPLETED' ORDER BY qa.executionTimestamp DESC LIMIT 1")
    Optional<QuotaResetAudit> findMostRecentSuccessfulReset(@Param("monthYear") String monthYear);
    
    /**
     * Find the most recent reset attempt (any status)
     */
    Optional<QuotaResetAudit> findFirstByOrderByExecutionTimestampDesc();
    
    /**
     * Count failed resets in the last N days
     */
    @Query("SELECT COUNT(qa) FROM QuotaResetAudit qa WHERE qa.executionStatus IN ('FAILED', 'COMPLETED_WITH_ERRORS') AND qa.executionTimestamp >= :sinceDate")
    long countFailedResetsSince(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Get execution statistics for the last N resets
     */
    @Query("""
        SELECT new map(
            AVG(qa.executionDurationMs) as avgDuration,
            MIN(qa.executionDurationMs) as minDuration,
            MAX(qa.executionDurationMs) as maxDuration,
            AVG(qa.recordsProcessed) as avgRecordsProcessed,
            SUM(qa.recordsProcessed) as totalRecordsProcessed,
            COUNT(qa) as totalExecutions
        )
        FROM QuotaResetAudit qa 
        WHERE qa.executionTimestamp >= :sinceDate
        """)
    Optional<Object> getExecutionStatistics(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Check if reset was already performed for current month
     */
    @Query("SELECT COUNT(qa) > 0 FROM QuotaResetAudit qa WHERE qa.monthYear = :monthYear AND qa.executionStatus = 'COMPLETED'")
    boolean existsSuccessfulResetForMonth(@Param("monthYear") String monthYear);
    
    /**
     * Get monthly reset history summary
     */
    @Query("""
        SELECT new map(
            qa.monthYear as monthYear,
            qa.resetDate as resetDate,
            qa.executionStatus as status,
            qa.recordsProcessed as recordsProcessed,
            qa.recordsSuccessful as recordsSuccessful,
            qa.recordsFailed as recordsFailed,
            qa.executionDurationMs as durationMs,
            qa.executionTimestamp as executionTime
        )
        FROM QuotaResetAudit qa 
        ORDER BY qa.resetDate DESC
        """)
    List<Object> getMonthlyResetSummary();
    
    /**
     * Delete old audit records (for cleanup)
     */
    void deleteByExecutionTimestampBefore(LocalDateTime cutoffDate);
}