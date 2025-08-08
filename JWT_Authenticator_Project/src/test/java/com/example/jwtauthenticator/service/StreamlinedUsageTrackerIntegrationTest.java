package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKeyMonthlyUsage;
import com.example.jwtauthenticator.repository.ApiKeyMonthlyUsageRepository;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🧪 INTEGRATION TESTS for StreamlinedUsageTracker
 * 
 * Tests with real database to verify duplicate prevention works in practice
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StreamlinedUsageTrackerIntegrationTest {

    @Autowired
    private StreamlinedUsageTracker usageTracker;
    
    @Autowired
    private ApiKeyMonthlyUsageRepository quotaRepository;
    
    @Autowired
    private ApiKeyRequestLogRepository auditRepository;
    
    private final UUID testApiKeyId = UUID.randomUUID();
    private final String testUserId = "INTEGRATION_TEST_USER";
    private final String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        quotaRepository.findByApiKeyIdAndMonthYear(testApiKeyId, currentMonth)
            .ifPresent(usage -> quotaRepository.delete(usage));
    }

    /**
     * 🚨 CRITICAL TEST: Verify no duplicate records are created under high concurrency
     */
    @Test
    void testHighConcurrency_NoDuplicateRecords() throws InterruptedException, ExecutionException, TimeoutException {
        // ARRANGE: Setup high concurrency scenario
        int numberOfThreads = 20;
        int callsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulCalls = new AtomicInteger(0);
        AtomicInteger failedCalls = new AtomicInteger(0);

        // ACT: Simulate high concurrent load
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        boolean isSuccessful = (j % 2 == 0); // Alternate success/failure
                        
                        CompletableFuture<Void> future = usageTracker.trackRivofetchCall(
                            testApiKeyId,
                            testUserId,
                            "192.168.1." + (threadId + 1),
                            "test-domain-" + threadId + ".com",
                            "TestAgent/1.0",
                            isSuccessful ? 200 : 500,
                            100L + j,
                            isSuccessful ? null : "Test error"
                        );
                        
                        // Wait for completion
                        future.get(5, TimeUnit.SECONDS);
                        
                        if (isSuccessful) {
                            successfulCalls.incrementAndGet();
                        } else {
                            failedCalls.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        // ASSERT: Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should complete within 30 seconds");
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Give async operations time to complete
        Thread.sleep(2000);

        // CRITICAL ASSERTION: Only ONE monthly usage record should exist
        List<ApiKeyMonthlyUsage> usageRecords = quotaRepository.findByApiKeyIdOrderByMonthYearDesc(testApiKeyId);
        assertEquals(1, usageRecords.size(), 
            "Should have exactly ONE monthly usage record, but found: " + usageRecords.size());

        ApiKeyMonthlyUsage usage = usageRecords.get(0);
        
        // Verify the counts are correct
        int expectedTotal = numberOfThreads * callsPerThread;
        assertEquals(expectedTotal, usage.getTotalCalls().intValue(), 
            "Total calls should match expected: " + expectedTotal);
        
        assertEquals(successfulCalls.get(), usage.getSuccessfulCalls().intValue(),
            "Successful calls should match: " + successfulCalls.get());
        
        assertEquals(failedCalls.get(), usage.getFailedCalls().intValue(),
            "Failed calls should match: " + failedCalls.get());

        System.out.println("✅ High concurrency test passed:");
        System.out.println("   - Total calls: " + usage.getTotalCalls());
        System.out.println("   - Successful: " + usage.getSuccessfulCalls());
        System.out.println("   - Failed: " + usage.getFailedCalls());
        System.out.println("   - Records created: " + usageRecords.size());
    }

    /**
     * 🧪 TEST: Verify atomic increments work correctly
     */
    @Test
    void testAtomicIncrements_CorrectCounts() throws InterruptedException, ExecutionException, TimeoutException {
        // ARRANGE: Create initial usage record
        ApiKeyMonthlyUsage initialUsage = ApiKeyMonthlyUsage.createForCurrentMonth(
            testApiKeyId, testUserId, 1000, 1100);
        quotaRepository.save(initialUsage);

        // ACT: Perform multiple concurrent increments
        int numberOfIncrements = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfIncrements);

        for (int i = 0; i < numberOfIncrements; i++) {
            final boolean isSuccessful = (i % 3 != 0); // 2/3 successful, 1/3 failed
            executor.submit(() -> {
                try {
                    CompletableFuture<Void> future = usageTracker.trackRivofetchCall(
                        testApiKeyId,
                        testUserId,
                        "192.168.1.100",
                        "atomic-test.com",
                        "TestAgent/1.0",
                        isSuccessful ? 200 : 400,
                        50L,
                        isSuccessful ? null : "Test error"
                    );
                    
                    future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.err.println("Increment failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // ASSERT: Wait for completion and verify counts
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Give async operations time to complete
        Thread.sleep(1000);

        ApiKeyMonthlyUsage finalUsage = quotaRepository.findByApiKeyIdAndMonthYear(testApiKeyId, currentMonth)
            .orElseThrow(() -> new AssertionError("Usage record should exist"));

        assertEquals(numberOfIncrements, finalUsage.getTotalCalls().intValue(),
            "Total calls should equal number of increments");
        
        // Verify successful vs failed counts
        long expectedSuccessful = numberOfIncrements - (numberOfIncrements / 3);
        long expectedFailed = numberOfIncrements / 3;
        
        assertEquals(expectedSuccessful, finalUsage.getSuccessfulCalls().intValue(),
            "Successful calls count should be correct");
        
        assertEquals(expectedFailed, finalUsage.getFailedCalls().intValue(),
            "Failed calls count should be correct");

        System.out.println("✅ Atomic increments test passed:");
        System.out.println("   - Expected total: " + numberOfIncrements);
        System.out.println("   - Actual total: " + finalUsage.getTotalCalls());
        System.out.println("   - Expected successful: " + expectedSuccessful);
        System.out.println("   - Actual successful: " + finalUsage.getSuccessfulCalls());
        System.out.println("   - Expected failed: " + expectedFailed);
        System.out.println("   - Actual failed: " + finalUsage.getFailedCalls());
    }

    /**
     * 🧪 TEST: Verify quota warnings work correctly
     */
    @Test
    void testQuotaWarnings_CorrectThresholds() throws InterruptedException, ExecutionException, TimeoutException {
        // ARRANGE: Create usage record with low quota limit
        ApiKeyMonthlyUsage usage = ApiKeyMonthlyUsage.createForCurrentMonth(
            testApiKeyId, testUserId, 10, 11); // Very low limit for testing
        quotaRepository.save(usage);

        // ACT: Make calls to approach quota limit
        for (int i = 0; i < 9; i++) { // 9 calls = 90% of 10
            CompletableFuture<Void> future = usageTracker.trackRivofetchCall(
                testApiKeyId,
                testUserId,
                "192.168.1.200",
                "quota-test.com",
                "TestAgent/1.0",
                200,
                25L,
                null
            );
            future.get(2, TimeUnit.SECONDS);
        }

        // Give async operations time to complete
        Thread.sleep(500);

        // ASSERT: Verify usage is at warning threshold
        ApiKeyMonthlyUsage updatedUsage = quotaRepository.findByApiKeyIdAndMonthYear(testApiKeyId, currentMonth)
            .orElseThrow(() -> new AssertionError("Usage record should exist"));

        assertEquals(9, updatedUsage.getTotalCalls().intValue());
        assertTrue(updatedUsage.getQuotaUsagePercentage() >= 80.0,
            "Should be at warning threshold (>80%)");

        System.out.println("✅ Quota warnings test passed:");
        System.out.println("   - Usage: " + updatedUsage.getTotalCalls() + "/" + updatedUsage.getQuotaLimit());
        System.out.println("   - Percentage: " + String.format("%.1f", updatedUsage.getQuotaUsagePercentage()) + "%");
    }

    /**
     * 🧪 TEST: Verify error handling doesn't corrupt data
     */
    @Test
    void testErrorHandling_DataIntegrity() throws InterruptedException, ExecutionException, TimeoutException {
        // ARRANGE: Create initial usage record
        ApiKeyMonthlyUsage initialUsage = ApiKeyMonthlyUsage.createForCurrentMonth(
            testApiKeyId, testUserId, 100, 110);
        quotaRepository.save(initialUsage);

        // ACT: Mix successful calls with some that might cause errors
        int successfulCalls = 0;
        int failedCalls = 0;
        
        for (int i = 0; i < 20; i++) {
            try {
                boolean shouldSucceed = (i % 4 != 0); // 3/4 succeed, 1/4 fail
                
                CompletableFuture<Void> future = usageTracker.trackRivofetchCall(
                    testApiKeyId,
                    testUserId,
                    "192.168.1.300",
                    "error-test.com",
                    "TestAgent/1.0",
                    shouldSucceed ? 200 : 500,
                    100L,
                    shouldSucceed ? null : "Simulated error"
                );
                
                future.get(3, TimeUnit.SECONDS);
                
                if (shouldSucceed) {
                    successfulCalls++;
                } else {
                    failedCalls++;
                }
                
            } catch (Exception e) {
                System.out.println("Expected error for test case " + i + ": " + e.getMessage());
            }
        }

        // Give async operations time to complete
        Thread.sleep(1000);

        // ASSERT: Verify data integrity
        ApiKeyMonthlyUsage finalUsage = quotaRepository.findByApiKeyIdAndMonthYear(testApiKeyId, currentMonth)
            .orElseThrow(() -> new AssertionError("Usage record should exist"));

        assertEquals(20, finalUsage.getTotalCalls().intValue(),
            "Total calls should be 20 regardless of success/failure");
        
        assertEquals(successfulCalls, finalUsage.getSuccessfulCalls().intValue(),
            "Successful calls should match expected");
        
        assertEquals(failedCalls, finalUsage.getFailedCalls().intValue(),
            "Failed calls should match expected");

        System.out.println("✅ Error handling test passed:");
        System.out.println("   - Total calls: " + finalUsage.getTotalCalls());
        System.out.println("   - Successful: " + finalUsage.getSuccessfulCalls());
        System.out.println("   - Failed: " + finalUsage.getFailedCalls());
    }
}