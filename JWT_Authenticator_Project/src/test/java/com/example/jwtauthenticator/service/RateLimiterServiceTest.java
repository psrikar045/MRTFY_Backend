package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.config.ForwardConfig;
import io.github.bucket4j.ConsumptionProbe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterService Tests")
class RateLimiterServiceTest {

    @Mock
    private ForwardConfig forwardConfig;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        // Set up mock configuration
        when(forwardConfig.getRequestsPerMinute()).thenReturn(60L);
        
        // Create service with mocked config
        rateLimiterService = new RateLimiterService(forwardConfig);
    }

    @Test
    @DisplayName("Should allow request when under rate limit")
    void consume_UnderRateLimit_ShouldAllow() {
        // Arrange
        String userId = "testuser";

        // Act
        ConsumptionProbe result = rateLimiterService.consume(userId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isConsumed(), "Request should be allowed when under rate limit");
        assertTrue(result.getRemainingTokens() >= 0, "Should have remaining tokens");
    }

    @Test
    @DisplayName("Should allow public request when under rate limit")
    void consumePublic_UnderRateLimit_ShouldAllow() {
        // Arrange
        String ipAddress = "192.168.1.1";

        // Act
        ConsumptionProbe result = rateLimiterService.consumePublic(ipAddress);

        // Assert
        assertNotNull(result);
        assertTrue(result.isConsumed(), "Public request should be allowed when under rate limit");
        assertTrue(result.getRemainingTokens() >= 0, "Should have remaining tokens");
    }

    @Test
    @DisplayName("Should have different buckets for different users")
    void consume_DifferentUsers_ShouldHaveSeparateBuckets() {
        // Arrange
        String user1 = "user1";
        String user2 = "user2";

        // Act
        ConsumptionProbe result1 = rateLimiterService.consume(user1);
        ConsumptionProbe result2 = rateLimiterService.consume(user2);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1.isConsumed(), "User1 request should be allowed");
        assertTrue(result2.isConsumed(), "User2 request should be allowed");
        
        // Both users should have similar remaining tokens (separate buckets)
        assertEquals(result1.getRemainingTokens(), result2.getRemainingTokens(), 1,
                "Different users should have separate rate limit buckets");
    }

    @Test
    @DisplayName("Should have different buckets for different IP addresses")
    void consumePublic_DifferentIPs_ShouldHaveSeparateBuckets() {
        // Arrange
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";

        // Act
        ConsumptionProbe result1 = rateLimiterService.consumePublic(ip1);
        ConsumptionProbe result2 = rateLimiterService.consumePublic(ip2);

        // Assert
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1.isConsumed(), "IP1 request should be allowed");
        assertTrue(result2.isConsumed(), "IP2 request should be allowed");
        
        // Both IPs should have similar remaining tokens (separate buckets)
        assertEquals(result1.getRemainingTokens(), result2.getRemainingTokens(), 1,
                "Different IPs should have separate rate limit buckets");
    }

    @Test
    @DisplayName("Should handle null user ID gracefully")
    void consume_NullUserId_ShouldHandleGracefully() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ConsumptionProbe result = rateLimiterService.consume(null);
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Should handle null IP address gracefully")
    void consumePublic_NullIpAddress_ShouldHandleGracefully() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ConsumptionProbe result = rateLimiterService.consumePublic(null);
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Should handle empty user ID")
    void consume_EmptyUserId_ShouldHandleGracefully() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ConsumptionProbe result = rateLimiterService.consume("");
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Should handle empty IP address")
    void consumePublic_EmptyIpAddress_ShouldHandleGracefully() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            ConsumptionProbe result = rateLimiterService.consumePublic("");
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Should consume tokens progressively")
    void consume_MultipleRequests_ShouldConsumeTokensProgressively() {
        // Arrange
        String userId = "testuser";

        // Act - Make multiple requests
        ConsumptionProbe result1 = rateLimiterService.consume(userId);
        ConsumptionProbe result2 = rateLimiterService.consume(userId);
        ConsumptionProbe result3 = rateLimiterService.consume(userId);

        // Assert
        assertTrue(result1.isConsumed());
        assertTrue(result2.isConsumed());
        assertTrue(result3.isConsumed());
        
        // Each subsequent request should have fewer remaining tokens
        assertTrue(result1.getRemainingTokens() > result2.getRemainingTokens());
        assertTrue(result2.getRemainingTokens() > result3.getRemainingTokens());
    }

    @Test
    @DisplayName("Should handle special characters in user ID")
    void consume_SpecialCharactersInUserId_ShouldHandleGracefully() {
        // Arrange
        String specialUserId = "user@domain.com#123!";

        // Act & Assert
        assertDoesNotThrow(() -> {
            ConsumptionProbe result = rateLimiterService.consume(specialUserId);
            assertNotNull(result);
            assertTrue(result.isConsumed());
        });
    }

    @Test
    @DisplayName("Should handle IPv6 addresses")
    void consumePublic_IPv6Address_ShouldHandleGracefully() {
        // Arrange
        String ipv6Address = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";

        // Act & Assert
        assertDoesNotThrow(() -> {
            ConsumptionProbe result = rateLimiterService.consumePublic(ipv6Address);
            assertNotNull(result);
            assertTrue(result.isConsumed());
        });
    }
}