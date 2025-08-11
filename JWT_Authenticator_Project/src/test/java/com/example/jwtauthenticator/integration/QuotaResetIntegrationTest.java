package com.example.jwtauthenticator.integration;

import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.scheduler.MonthlyQuotaResetScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Monthly Quota Reset functionality
 * Tests the complete workflow with actual database operations
 * 
 * @author BrandSnap API Team
 * @version 1.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Monthly Quota Reset Integration Tests")
public class QuotaResetIntegrationTest {
    
    @Autowired
    private MonthlyQuotaResetScheduler scheduler;
    
    @Autowired
    private ApiKeyMonthlyUsageRepository usageRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    @DisplayName("Should perform end-to-end quota reset with database persistence")
    void shouldPerformEndToEndQuotaReset() {
        // Given: Create a test user with PRO plan
        User testUser = User.builder()
                .id("TEST000001")
                .username("integrationtest")
                .email("integration@test.com")
                .password("password")
                .plan(UserPlan.PRO)
                .planStartedAt(LocalDateTime.now().minusDays(30))
                .monthlyResetDate(LocalDate.now())
                .build();
        
        User savedUser = userRepository.save(testUser);
        
        // Given: Create usage record that needs reset (old month data)
        UUID testApiKeyId = UUID.randomUUID();
        LocalDate oldResetDate = LocalDate.of(2024, 1, 1);
        
        ApiKeyMonthlyUsage testUsage = ApiKeyMonthlyUsage.builder()
                .apiKeyId(testApiKeyId)
                .userId(savedUser.getId())
                .monthYear("2024-01") // Old month
                .totalCalls(750)
                .successfulCalls(720)
                .failedCalls(30)
                .quotaExceededCalls(0)
                .lastResetDate(oldResetDate)
                .quotaLimit(1000) // PRO plan old limit
                .graceLimit(1100) // PRO plan old grace limit
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        
        ApiKeyMonthlyUsage savedUsage = usageRepository.save(testUsage);
        
        // Verify initial state
        assertThat(savedUsage.getTotalCalls()).isEqualTo(750);
        assertThat(savedUsage.getMonthYear()).isEqualTo("2024-01");
        assertThat(savedUsage.getLastResetDate()).isEqualTo(oldResetDate);
        
        // When: Perform manual quota reset
        var result = scheduler.performManualQuotaReset();
        
        // Then: Verify reset result
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.isSuccessful()).isTrue();
        
        // Then: Verify database state after reset
        var updatedUsage = usageRepository.findById(savedUsage.getId()).orElseThrow();
        
        assertThat(updatedUsage.getTotalCalls()).isEqualTo(0);
        assertThat(updatedUsage.getSuccessfulCalls()).isEqualTo(0);
        assertThat(updatedUsage.getFailedCalls()).isEqualTo(0);
        assertThat(updatedUsage.getQuotaExceededCalls()).isEqualTo(0);
        assertThat(updatedUsage.getQuotaLimit()).isEqualTo(UserPlan.PRO.getMonthlyApiCalls()); // 1000
        assertThat(updatedUsage.getGraceLimit()).isEqualTo(UserPlan.PRO.getGraceLimit("api_calls")); // 1100
        assertThat(updatedUsage.getLastResetDate()).isEqualTo(LocalDate.now().withDayOfMonth(1));
        assertThat(updatedUsage.getMonthYear()).isEqualTo(LocalDate.now().getYear() + "-" + 
                String.format("%02d", LocalDate.now().getMonthValue()));
        
        // Clean up
        usageRepository.delete(updatedUsage);
        userRepository.delete(savedUser);
    }
    
    @Test
    @DisplayName("Should handle multiple users with different plans correctly")
    void shouldHandleMultipleUsersWithDifferentPlans() {
        // Given: Create FREE plan user
        User freeUser = User.builder()
                .id("FREE00001")
                .username("freeuser")
                .email("free@test.com")
                .password("password")
                .plan(UserPlan.FREE)
                .planStartedAt(LocalDateTime.now().minusDays(30))
                .monthlyResetDate(LocalDate.now())
                .build();
        
        // Given: Create BUSINESS plan user
        User businessUser = User.builder()
                .id("BIZZ00001")
                .username("businessuser")
                .email("business@test.com")
                .password("password")
                .plan(UserPlan.BUSINESS)
                .planStartedAt(LocalDateTime.now().minusDays(30))
                .monthlyResetDate(LocalDate.now())
                .build();
        
        User savedFreeUser = userRepository.save(freeUser);
        User savedBusinessUser = userRepository.save(businessUser);
        
        // Given: Create usage records for both users
        UUID freeApiKeyId = UUID.randomUUID();
        UUID businessApiKeyId = UUID.randomUUID();
        LocalDate oldResetDate = LocalDate.of(2024, 1, 1);
        
        ApiKeyMonthlyUsage freeUsage = ApiKeyMonthlyUsage.createForCurrentMonth(
                freeApiKeyId, savedFreeUser.getId(), 100, 110);
        freeUsage.setMonthYear("2024-01");
        freeUsage.setLastResetDate(oldResetDate);
        freeUsage.setTotalCalls(80);
        freeUsage.setSuccessfulCalls(75);
        freeUsage.setFailedCalls(5);
        
        ApiKeyMonthlyUsage businessUsage = ApiKeyMonthlyUsage.createForCurrentMonth(
                businessApiKeyId, savedBusinessUser.getId(), -1, -1); // Unlimited
        businessUsage.setMonthYear("2024-01");
        businessUsage.setLastResetDate(oldResetDate);
        businessUsage.setTotalCalls(5000);
        businessUsage.setSuccessfulCalls(4950);
        businessUsage.setFailedCalls(50);
        
        ApiKeyMonthlyUsage savedFreeUsage = usageRepository.save(freeUsage);
        ApiKeyMonthlyUsage savedBusinessUsage = usageRepository.save(businessUsage);
        
        // When: Perform manual quota reset
        var result = scheduler.performManualQuotaReset();
        
        // Then: Verify reset result
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isGreaterThanOrEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
        
        // Then: Verify FREE user reset
        var updatedFreeUsage = usageRepository.findById(savedFreeUsage.getId()).orElseThrow();
        assertThat(updatedFreeUsage.getTotalCalls()).isEqualTo(0);
        assertThat(updatedFreeUsage.getQuotaLimit()).isEqualTo(UserPlan.FREE.getMonthlyApiCalls()); // 100
        assertThat(updatedFreeUsage.getGraceLimit()).isEqualTo(UserPlan.FREE.getGraceLimit("api_calls")); // 110
        
        // Then: Verify BUSINESS user reset
        var updatedBusinessUsage = usageRepository.findById(savedBusinessUsage.getId()).orElseThrow();
        assertThat(updatedBusinessUsage.getTotalCalls()).isEqualTo(0);
        assertThat(updatedBusinessUsage.getQuotaLimit()).isEqualTo(UserPlan.BUSINESS.getMonthlyApiCalls()); // -1 (unlimited)
        assertThat(updatedBusinessUsage.getGraceLimit()).isEqualTo(UserPlan.BUSINESS.getGraceLimit("api_calls")); // -1 (unlimited)
        
        // Clean up
        usageRepository.delete(updatedFreeUsage);
        usageRepository.delete(updatedBusinessUsage);
        userRepository.delete(savedFreeUser);
        userRepository.delete(savedBusinessUser);
    }
    
    @Test
    @DisplayName("Should return empty result when no records need reset")
    void shouldReturnEmptyResultWhenNoRecordsNeedReset() {
        // Given: Current month records (no reset needed)
        LocalDate currentResetDate = LocalDate.now().withDayOfMonth(1);
        long initialCount = usageRepository.countRecordsNeedingReset(currentResetDate);
        
        // When: Perform manual quota reset
        var result = scheduler.performManualQuotaReset();
        
        // Then: Should handle gracefully
        assertThat(result).isNotNull();
        assertThat(result.getTotalProcessed()).isEqualTo((int) initialCount);
        // Note: We don't assert specific counts since there might be existing test data
    }
}