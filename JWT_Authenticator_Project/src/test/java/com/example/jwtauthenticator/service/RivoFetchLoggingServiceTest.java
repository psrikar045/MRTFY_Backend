package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.RivoFetchRequestLog;
import com.example.jwtauthenticator.repository.RivoFetchRequestLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 🧪 Test RivoFetch Logging Service Database Operations
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class RivoFetchLoggingServiceTest {

    @Autowired
    private RivoFetchLoggingService rivoFetchLoggingService;
    
    @Autowired
    private RivoFetchRequestLogRepository rivoFetchRepository;
    
    @Autowired
    private RivoFetchIdGeneratorService idGeneratorService;

    @Test
    public void testIdGeneration() {
        // Test ID generation
        String id1 = idGeneratorService.generateRivoFetchId();
        String id2 = idGeneratorService.generateRivoFetchId();
        
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("RIVO9"));
        assertTrue(id2.startsWith("RIVO9"));
        
        System.out.println("✅ Generated ID 1: " + id1);
        System.out.println("✅ Generated ID 2: " + id2);
    }

    @Test
    public void testDirectDatabaseSave() {
        // Create a test log entry directly
        RivoFetchRequestLog logEntry = RivoFetchRequestLog.builder()
                .rivoFetchLogId("RIVO9TEST1")
                .universalRequestUuid(UUID.randomUUID())
                .rivoFetchTimestamp(LocalDateTime.now())
                .rivoFetchTotalDurationMs(100L)
                .rivoFetchTargetUrl("https://test.com")
                .rivoFetchClientIp("127.0.0.1")
                .rivoFetchSuccess(true)
                .rivoFetchResponseStatus(200)
                .rivoFetchCacheHitType("MISS")
                .build();

        // Save directly to repository
        RivoFetchRequestLog saved = rivoFetchRepository.save(logEntry);
        
        assertNotNull(saved);
        assertNotNull(saved.getRivoFetchLogId());
        assertEquals("RIVO9TEST1", saved.getRivoFetchLogId());
        
        System.out.println("✅ Direct save successful: " + saved.getRivoFetchLogId());
    }

    @Test
    public void testSynchronousLoggingMethod() throws Exception {
        // Mock request and response
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/api/secure/rivofetch");
        when(mockRequest.getHeader("User-Agent")).thenReturn("Test-Agent");
        when(mockRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(mockResponse.getStatus()).thenReturn(200);

        // Test SYNCHRONOUS logging (the real method used)
        boolean result = rivoFetchLoggingService.logSuccessfulRivoFetchSync(
                mockRequest, mockResponse, null, System.currentTimeMillis() - 100, 
                "{\"test\": \"data\"}", "MISS", "https://test.com");

        assertTrue(result, "Synchronous logging should succeed");
        System.out.println("✅ Synchronous logging completed successfully: " + result);
        
        // Verify record was actually saved
        long count = rivoFetchRepository.count();
        assertTrue(count > 0, "At least one record should be saved");
        System.out.println("✅ Database contains " + count + " log records");
    }
}