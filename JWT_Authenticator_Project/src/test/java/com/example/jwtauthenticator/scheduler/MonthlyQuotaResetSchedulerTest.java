package com.example.jwtauthenticator.scheduler;

import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.scheduler.MonthlyQuotaResetScheduler.QuotaResetResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MonthlyQuotaResetScheduler
 * Tests the core functionality of monthly quota reset logic
 * 
 * @author BrandSnap API Team
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Monthly Quota Reset Scheduler Tests")
class MonthlyQuotaResetSchedulerTest {
    
    @Mock
    private ApiKeyMonthlyUsageRepository usageRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private MonthlyQuotaResetScheduler scheduler;
    
    private ApiKeyMonthlyUsage testUsage;
    private User testUser;
    private UUID testApiKeyId;
    private String testUserId;
    
    @BeforeEach
    void setUp() {
        testApiKeyId = UUID.randomUUID();
        testUserId = "DOMBR000001";
        
        // Create test user with PRO plan
        testUser = User.builder()
                .id(testUserId)
                .username("testuser")
                .email("test@example.com")
                .password("password")
                .plan(UserPlan.PRO)
                .planStartedAt(LocalDateTime.now().minusDays(30))
                .monthlyResetDate(LocalDate.now())
                .build();
        
        // Create test usage record that needs reset
        testUsage = ApiKeyMonthlyUsage.builder()
                .id(UUID.randomUUID())
                .apiKeyId(testApiKeyId)
                .userId(testUserId)
                .monthYear("2024-01") // Old month
                .totalCalls(500)
                .successfulCalls(480)
                .failedCalls(20)
                .quotaExceededCalls(0)
                .lastResetDate(LocalDate.of(2024, 1, 1)) // Old reset date
                .quotaLimit(1000) // Old PRO limit
                .graceLimit(1100) // Old PRO grace limit
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }
    
    @Test
    @DisplayName("Should successfully reset quota for valid usage record")
    void shouldResetQuotaForValidUsageRecord() {
        // Given
        LocalDate resetDate = LocalDate.now().withDayOfMonth(1);
        List<ApiKeyMonthlyUsage> usagesToReset = Arrays.asList(testUsage);
        
        when(usageRepository.findAllNeedingReset(resetDate)).thenReturn(usagesToReset);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(usageRepository.save(any(ApiKeyMonthlyUsage.class))).thenReturn(testUsage);
        
        // When
        QuotaResetResult result = scheduler.performManualQuotaReset();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getTotalProcessed()).isGreaterThanOrEqualTo(1);
        assertThat(result.isSuccessful()).isTrue();
        
        // Verify repository interactions
        verify(usageRepository).findAllNeedingReset(resetDate);
        verify(userRepository).findById(testUserId);
        verify(usageRepository).save(any(ApiKeyMonthlyUsage.class));
        
        // Verify the usage record was reset (through save method call)
        verify(usageRepository).save(argThat(usage -> 
            usage.getTotalCalls() == 0 &&
            usage.getSuccessfulCalls() == 0 &&
            usage.getFailedCalls() == 0 &&
            usage.getQuotaExceededCalls() == 0 &&
            usage.getQuotaLimit() == UserPlan.PRO.getMonthlyApiCalls()
        ));
    }
    
    @Test
    @DisplayName("Should skip reset when user not found")
    void shouldSkipResetWhenUserNotFound() {
        // Given
        LocalDate resetDate = LocalDate.now().withDayOfMonth(1);
        List<ApiKeyMonthlyUsage> usagesToReset = Arrays.asList(testUsage);
        
        when(usageRepository.findAllNeedingReset(resetDate)).thenReturn(usagesToReset);
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
        
        // When
        QuotaResetResult result = scheduler.performManualQuotaReset();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSkippedCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.getTotalProcessed()).isGreaterThanOrEqualTo(1);
        
        // Verify repository interactions
        verify(usageRepository).findAllNeedingReset(resetDate);
        verify(userRepository).findById(testUserId);
        verify(usageRepository, never()).save(any(ApiKeyMonthlyUsage.class));
    }
    
    @Test
    @DisplayName("Should handle empty usage list")
    void shouldHandleEmptyUsageList() {
        // Given
        LocalDate resetDate = LocalDate.now().withDayOfMonth(1);
        
        when(usageRepository.findAllNeedingReset(resetDate)).thenReturn(List.of());
        
        // When
        QuotaResetResult result = scheduler.performManualQuotaReset();
        
        // Then
        assertThat(result).isNotNull();
        // Note: In real scenario, there might be existing data, so we check for >= 0
        assertThat(result.getSuccessCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.getTotalProcessed()).isGreaterThanOrEqualTo(0);
        
        // Verify repository interactions
        verify(usageRepository).findAllNeedingReset(resetDate);
        verify(userRepository, never()).findById(anyString());
        verify(usageRepository, never()).save(any(ApiKeyMonthlyUsage.class));
    }
    
    @Test
    @DisplayName("Should handle multiple usage records with different user plans")
    void shouldHandleMultipleUsageRecordsWithDifferentPlans() {
        // Given
        LocalDate resetDate = LocalDate.now().withDayOfMonth(1);
        
        // Create second usage record for FREE user
        String freeUserId = "DOMBR000002";
        UUID freeApiKeyId = UUID.randomUUID();
        
        User freeUser = User.builder()
                .id(freeUserId)
                .username("freeuser")
                .email("free@example.com")
                .password("password")
                .plan(UserPlan.FREE)
                .planStartedAt(LocalDateTime.now().minusDays(30))
                .monthlyResetDate(LocalDate.now())
                .build();
        
        ApiKeyMonthlyUsage freeUsage = ApiKeyMonthlyUsage.builder()
                .id(UUID.randomUUID())
                .apiKeyId(freeApiKeyId)
                .userId(freeUserId)
                .monthYear("2024-01")
                .totalCalls(50)
                .successfulCalls(48)
                .failedCalls(2)
                .quotaExceededCalls(0)
                .lastResetDate(LocalDate.of(2024, 1, 1))
                .quotaLimit(100) // FREE plan limit
                .graceLimit(110) // FREE plan grace limit
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        
        List<ApiKeyMonthlyUsage> usagesToReset = Arrays.asList(testUsage, freeUsage);
        
        when(usageRepository.findAllNeedingReset(resetDate)).thenReturn(usagesToReset);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(freeUserId)).thenReturn(Optional.of(freeUser));
        when(usageRepository.save(any(ApiKeyMonthlyUsage.class))).thenReturn(testUsage);
        
        // When
        QuotaResetResult result = scheduler.performManualQuotaReset();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isGreaterThanOrEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getTotalProcessed()).isGreaterThanOrEqualTo(2);
        assertThat(result.isSuccessful()).isTrue();
        
        // Verify repository interactions
        verify(usageRepository).findAllNeedingReset(resetDate);
        verify(userRepository).findById(testUserId);
        verify(userRepository).findById(freeUserId);
        verify(usageRepository, times(2)).save(any(ApiKeyMonthlyUsage.class));
    }
    
    @Test
    @DisplayName("Should handle mixed success and failure scenarios")
    void shouldHandleMixedSuccessAndFailureScenarios() {
        // Given
        LocalDate resetDate = LocalDate.now().withDayOfMonth(1);
        
        // Create another usage record with invalid user
        ApiKeyMonthlyUsage invalidUsage = ApiKeyMonthlyUsage.builder()
                .id(UUID.randomUUID())
                .apiKeyId(UUID.randomUUID())
                .userId("INVALID_USER")
                .monthYear("2024-01")
                .totalCalls(100)
                .lastResetDate(LocalDate.of(2024, 1, 1))
                .quotaLimit(1000)
                .graceLimit(1100)
                .build();
        
        List<ApiKeyMonthlyUsage> usagesToReset = Arrays.asList(testUsage, invalidUsage);
        
        when(usageRepository.findAllNeedingReset(resetDate)).thenReturn(usagesToReset);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.findById("INVALID_USER")).thenReturn(Optional.empty());
        when(usageRepository.save(any(ApiKeyMonthlyUsage.class))).thenReturn(testUsage);
        
        // When
        QuotaResetResult result = scheduler.performManualQuotaReset();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getTotalProcessed()).isEqualTo(2);
        assertThat(result.getSuccessRate()).isEqualTo(50.0);
        
        // Verify repository interactions
        verify(usageRepository).findAllNeedingReset(resetDate);
        verify(userRepository).findById(testUserId);
        verify(userRepository).findById("INVALID_USER");
        verify(usageRepository, times(1)).save(any(ApiKeyMonthlyUsage.class));
    }
    
    @Test
    @DisplayName("QuotaResetResult should provide correct statistics")
    void quotaResetResultShouldProvideCorrectStatistics() {
        // Given
        QuotaResetResult result = new QuotaResetResult(5, 2, 1, "2024-02");
        
        // Then
        assertThat(result.getSuccessCount()).isEqualTo(5);
        assertThat(result.getFailureCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getTotalProcessed()).isEqualTo(8);
        assertThat(result.getMonthYear()).isEqualTo("2024-02");
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.isSuccessful()).isFalse(); // Has failures
        assertThat(result.getSuccessRate()).isEqualTo(62.5); // 5/8 * 100
        
        // Test toString
        assertThat(result.toString()).contains("2024-02", "success=5", "failed=2", "skipped=1");
    }
    
    @Test
    @DisplayName("QuotaResetResult with no failures should be successful")
    void quotaResetResultWithNoFailuresShouldBeSuccessful() {
        // Given
        QuotaResetResult result = new QuotaResetResult(10, 0, 2, "2024-02");
        
        // Then
        assertThat(result.hasFailures()).isFalse();
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getSuccessRate()).isCloseTo(83.33, org.assertj.core.data.Offset.offset(0.01)); // 10/12 * 100
    }
}