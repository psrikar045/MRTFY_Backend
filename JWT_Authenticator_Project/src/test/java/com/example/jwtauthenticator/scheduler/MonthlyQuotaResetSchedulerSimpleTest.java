package com.example.jwtauthenticator.scheduler;

import com.example.jwtauthenticator.scheduler.MonthlyQuotaResetScheduler.QuotaResetResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test for Monthly Quota Reset functionality
 * Tests the actual implementation with real database
 * 
 * @author BrandSnap API Team
 * @version 1.0
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Monthly Quota Reset Scheduler Simple Tests")
public class MonthlyQuotaResetSchedulerSimpleTest {
    
    @Autowired
    private MonthlyQuotaResetScheduler scheduler;
    
    @Test
    @DisplayName("Should execute manual quota reset without errors")
    void shouldExecuteManualQuotaResetWithoutErrors() {
        // When
        QuotaResetResult result = scheduler.performManualQuotaReset();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMonthYear()).isNotNull();
        assertThat(result.getTotalProcessed()).isGreaterThanOrEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0); // Should have no failures
        
        // Log the result for verification
        System.out.println("✅ Manual reset test completed: " + result.toString());
    }
    
    @Test
    @DisplayName("Should have valid result structure")
    void shouldHaveValidResultStructure() {
        // When
        QuotaResetResult result = scheduler.performManualQuotaReset();
        
        // Then - Verify result structure
        assertThat(result.getSuccessCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getFailureCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getSkippedCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getTotalProcessed()).isEqualTo(
            result.getSuccessCount() + result.getFailureCount() + result.getSkippedCount()
        );
        assertThat(result.getSuccessRate()).isBetween(0.0, 100.0);
        
        // Log detailed results
        System.out.println("📊 Reset Statistics:");
        System.out.println("   Success: " + result.getSuccessCount());
        System.out.println("   Failed: " + result.getFailureCount());
        System.out.println("   Skipped: " + result.getSkippedCount());
        System.out.println("   Total: " + result.getTotalProcessed());
        System.out.println("   Success Rate: " + result.getSuccessRate() + "%");
        System.out.println("   Month: " + result.getMonthYear());
    }
}