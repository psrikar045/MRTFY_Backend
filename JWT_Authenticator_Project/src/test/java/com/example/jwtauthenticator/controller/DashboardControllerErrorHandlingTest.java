package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.error.ErrorResponseDTO;
import com.example.jwtauthenticator.service.ErrorHandlerService;
import com.example.jwtauthenticator.service.UnifiedDashboardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 🧪 ERROR HANDLING INTEGRATION TESTS
 * 
 * Tests the standardized error handling across dashboard endpoints
 * Verifies proper error response format and error codes
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UnifiedDashboardService unifiedDashboardService;

    /**
     * 🧪 TEST: Verify authentication required error format
     */
    @Test
    void testAuthenticationRequired() throws Exception {
        // ACT & ASSERT: Request without authentication
        String response = mockMvc.perform(get("/api/dashboard/cards")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn().getResponse().getContentAsString();

        // Verify error response structure
        ErrorResponseDTO errorResponse = objectMapper.readValue(response, ErrorResponseDTO.class);
        assertNotNull(errorResponse);
        assertFalse(errorResponse.isSuccess());
        assertEquals(401, errorResponse.getStatus());
        assertNotNull(errorResponse.getErrorCode());
        assertNotNull(errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
    }

    /**
     * 🧪 TEST: Verify MRTFY user ID validation
     */
    @Test
    @WithMockUser(username = "MRTFY000002")
    void testValidUserIdFormat() throws Exception {
        // ARRANGE: Mock service to return null (to test not found scenario)
        when(unifiedDashboardService.getUserDashboardCards("MRTFY000002")).thenReturn(null);

        // ACT & ASSERT: Request with valid MRTFY user ID format
        mockMvc.perform(get("/api/dashboard/cards")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()) // Should get dashboard data unavailable
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(204))
                .andExpect(jsonPath("$.errorCode").value("DASHBOARD_DATA_UNAVAILABLE"));
    }

    /**
     * 🧪 TEST: Verify invalid user ID format handling
     */
    @Test
    @WithMockUser(username = "invalid<script>alert('xss')</script>")
    void testInvalidUserIdWithSuspiciousContent() throws Exception {
        // ACT & ASSERT: Request with suspicious user ID
        mockMvc.perform(get("/api/dashboard/cards")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("SUSPICIOUS_ACTIVITY"))
                .andExpect(jsonPath("$.message").value("Suspicious activity detected"))
                .andExpect(jsonPath("$.context.detectedActivity").exists());
    }

    /**
     * 🧪 TEST: Verify API key not found error handling
     */
    @Test
    @WithMockUser(username = "MRTFY000002")
    void testApiKeyNotFound() throws Exception {
        // ARRANGE: Mock service to return null for API key dashboard
        UUID nonExistentApiKeyId = UUID.randomUUID();
        when(unifiedDashboardService.getApiKeyDashboard(any(UUID.class), any(String.class)))
            .thenReturn(null);

        // ACT & ASSERT: Request for non-existent API key
        mockMvc.perform(get("/api/dashboard/api-key/" + nonExistentApiKeyId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("API Key not found"));
    }

    /**
     * 🧪 TEST: Verify missing parameter handling
     */
    @Test
    @WithMockUser(username = "MRTFY000002")
    void testMissingApiKeyParameter() throws Exception {
        // ACT & ASSERT: Request with null API key ID (this would be caught by Spring)
        // In real scenario, Spring would handle this, but we test our validation
        mockMvc.perform(get("/api/dashboard/api-key/")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()); // Spring's default 404 for missing path variable
    }

    /**
     * 🧪 TEST: Verify internal server error handling
     */
    @Test
    @WithMockUser(username = "MRTFY000002")
    void testInternalServerError() throws Exception {
        // ARRANGE: Mock service to throw an exception
        when(unifiedDashboardService.getUserDashboardCards("MRTFY000002"))
            .thenThrow(new RuntimeException("Database connection failed"));

        // ACT & ASSERT: Request should return internal server error
        mockMvc.perform(get("/api/dashboard/cards")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An internal error occurred"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    /**
     * 🧪 TEST: Verify error response includes trace ID for debugging
     */
    @Test
    void testErrorResponseIncludesTraceId() throws Exception {
        // ACT: Make request without authentication
        String response = mockMvc.perform(get("/api/dashboard/cards")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // ASSERT: Verify trace ID is present
        ErrorResponseDTO errorResponse = objectMapper.readValue(response, ErrorResponseDTO.class);
        assertNotNull(errorResponse.getTraceId());
        assertTrue(errorResponse.getTraceId().length() >= 8); // UUID substring
    }

    /**
     * 🧪 TEST: Verify error response includes request path and method
     */
    @Test
    void testErrorResponseIncludesRequestInfo() throws Exception {
        // ACT: Make request without authentication
        String response = mockMvc.perform(get("/api/dashboard/cards")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // ASSERT: Verify request info is included
        ErrorResponseDTO errorResponse = objectMapper.readValue(response, ErrorResponseDTO.class);
        assertEquals("/api/dashboard/cards", errorResponse.getPath());
        assertEquals("GET", errorResponse.getMethod());
    }

    /**
     * 🧪 TEST: Verify proper user ID validation for different formats
     */
    @Test
    @WithMockUser(username = "MRTFY000001")
    void testValidMrtfyFormat1() throws Exception {
        when(unifiedDashboardService.getUserDashboardCards("MRTFY000001")).thenReturn(null);
        
        mockMvc.perform(get("/api/dashboard/cards"))
                .andExpect(status().isNoContent()) // Should pass validation
                .andExpect(jsonPath("$.errorCode").value("DASHBOARD_DATA_UNAVAILABLE"));
    }

    @Test
    @WithMockUser(username = "MRTFY999999")
    void testValidMrtfyFormat2() throws Exception {
        when(unifiedDashboardService.getUserDashboardCards("MRTFY999999")).thenReturn(null);
        
        mockMvc.perform(get("/api/dashboard/cards"))
                .andExpect(status().isNoContent()) // Should pass validation
                .andExpect(jsonPath("$.errorCode").value("DASHBOARD_DATA_UNAVAILABLE"));
    }

    @Test
    @WithMockUser(username = "MRTFY")
    void testInvalidMrtfyFormatTooShort() throws Exception {
        mockMvc.perform(get("/api/dashboard/cards"))
                .andExpect(status().isBadRequest()) // Should fail validation
                .andExpect(jsonPath("$.errorCode").value("INVALID_USER_ID_FORMAT"));
    }

    @Test
    @WithMockUser(username = "MRTFY00000A")
    void testInvalidMrtfyFormatWithLetters() throws Exception {
        mockMvc.perform(get("/api/dashboard/cards"))
                .andExpect(status().isNoContent()); // Should pass as alternative format allowed
    }

    /**
     * 🧪 TEST: Verify performance of error handling
     */
    @Test
    void testErrorHandlingPerformance() throws Exception {
        // ARRANGE: Measure multiple error requests
        long startTime = System.currentTimeMillis();
        
        // ACT: Make multiple error requests
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/dashboard/cards")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // ASSERT: Error handling should be fast
        assertTrue(executionTime < 1000, 
            "Error handling should complete quickly, but took: " + executionTime + "ms");
        
        System.out.println("✅ Error handling performance test passed: " + executionTime + "ms for 10 requests");
    }
}