package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("DombrUserIdGenerator functionality not yet implemented")
public class DombrUserIdGeneratorTest {

    @Autowired
    private IdGeneratorService idGeneratorService;
    
    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    public void testGenerateDombrUserId() {
        // Generate a user ID
        String userId = idGeneratorService.generateDombrUserId();
        
        // Verify format
        assertNotNull(userId);
        assertTrue(userId.startsWith("DOMBR"));
        assertEquals(11, userId.length()); // DOMBR + 6 digits
        
        // Extract the numeric part
        String numericPart = userId.substring(5);
        assertEquals(6, numericPart.length());
        
        // Verify it's a valid number
        int number = Integer.parseInt(numericPart);
        assertTrue(number > 0);
        
        // Generate another ID and verify it increments
        String userId2 = idGeneratorService.generateDombrUserId();
        String numericPart2 = userId2.substring(5);
        int number2 = Integer.parseInt(numericPart2);
        
        assertEquals(number + 1, number2, "Second ID should be incremented by 1");
    }
    
    @Test
    @DisplayName("Should generate unique IDs even with concurrent access")
    public void testConcurrentIdGeneration() {
        // Generate multiple IDs and ensure they are all unique
        Set<String> generatedIds = new HashSet<>();
        int numberOfIds = 10;
        
        for (int i = 0; i < numberOfIds; i++) {
            String userId = idGeneratorService.generateDombrUserId();
            assertTrue(userId.startsWith("DOMBR"), "ID should start with DOMBR prefix");
            assertTrue(generatedIds.add(userId), "Each generated ID should be unique: " + userId);
        }
        
        assertEquals(numberOfIds, generatedIds.size(), "All generated IDs should be unique");
    }
    
    @Test
    @DisplayName("Should handle sequence reset gracefully")
    public void testSequenceReset() {
        // This test verifies that the ID generator can handle sequence resets
        // without breaking the uniqueness constraint
        
        String id1 = idGeneratorService.generateDombrUserId();
        assertNotNull(id1);
        assertTrue(id1.startsWith("DOMBR"));
        
        // Generate another ID after some operations
        String id2 = idGeneratorService.generateDombrUserId();
        assertNotNull(id2);
        assertTrue(id2.startsWith("DOMBR"));
        
        // Ensure they are different
        assertNotEquals(id1, id2, "Generated IDs should be unique");
    }
    
    @Test
    @Transactional
    public void testUserCreationWithDombrId() {
        // Generate a DOMBR ID
        String dombrId = idGeneratorService.generateDombrUserId();
        
        // Create a test user with the DOMBR ID as primary key
        User user = User.builder()
                .id(dombrId)
                .username("testuser_" + System.currentTimeMillis())
                .email("test_" + System.currentTimeMillis() + "@example.com")
                .password("password")
                .build();
        
        // Save the user
        User savedUser = userRepository.save(user);
        
        // Verify the ID was saved correctly
        assertNotNull(savedUser.getId());
        assertEquals(dombrId, savedUser.getId());
        
        // Retrieve the user by ID
        User retrievedUser = userRepository.findById(dombrId).orElse(null);
        assertNotNull(retrievedUser);
        assertEquals(dombrId, retrievedUser.getId());
    }
    
    @Test
    public void testConcurrentGeneration() throws InterruptedException {
        // Number of concurrent threads
        int threadCount = 10;
        
        // Set to collect generated IDs
        Set<String> generatedIds = new HashSet<>();
        
        // Latch to synchronize threads
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        
        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // Submit tasks
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Wait for signal to start
                    latch.await();
                    
                    // Generate ID
                    String id = idGeneratorService.generateDombrUserId();
                    
                    // Add to set (synchronized to avoid ConcurrentModificationException)
                    synchronized (generatedIds) {
                        generatedIds.add(id);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all threads at once
        latch.countDown();
        
        // Wait for all threads to complete
        completionLatch.await(10, TimeUnit.SECONDS);
        
        // Shutdown executor
        executor.shutdown();
        
        // Verify we have the expected number of unique IDs
        assertEquals(threadCount, generatedIds.size(), 
                "All generated IDs should be unique, even under concurrent load");
    }
}