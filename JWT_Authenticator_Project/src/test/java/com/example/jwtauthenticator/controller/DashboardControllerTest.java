package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.dashboard.UserDashboardCardsDTO;
import com.example.jwtauthenticator.dto.dashboard.SingleApiKeyDashboardDTO;
import com.example.jwtauthenticator.service.UserDashboardService;
import com.example.jwtauthenticator.service.ApiKeyDashboardService;
import com.example.jwtauthenticator.util.JwtUtil;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.entity.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test class for Dashboard Controller
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserDashboardService userDashboardService;

    @MockBean
    private ApiKeyDashboardService apiKeyDashboardService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    private UserDashboardCardsDTO mockUserDashboard;
    private SingleApiKeyDashboardDTO mockApiKeyDashboard;
    private User mockUser;

    @BeforeEach
    void setUp() {
        // Setup mock user
        mockUser = new User();
        mockUser.setId("test-user-123");
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@example.com");

        // Setup mock user dashboard
        mockUserDashboard = UserDashboardCardsDTO.builder()
                .totalApiCalls(UserDashboardCardsDTO.ApiCallsCardDTO.builder()
                        .totalCalls(15420L)
                        .percentageChange(12.5)
                        .trend("up")
                        .previousPeriodCalls(13750L)
                        .dailyAverage(514.0)
                        .status("healthy")
                        .build())
                .activeDomains(UserDashboardCardsDTO.ActiveDomainsCardDTO.builder()
                        .activeDomains(8)
                        .percentageChange(25.0)
                        .trend("up")
                        .previousPeriodDomains(6)
                        .newDomainsThisPeriod(3)
                        .status("growing")
                        .build())
                .domainsAdded(UserDashboardCardsDTO.DomainsAddedCardDTO.builder()
                        .domainsAdded(3)
                        .percentageChange(50.0)
                        .trend("up")
                        .previousMonthAdded(2)
                        .monthlyTarget(5)
                        .status("on_track")
                        .build())
                .remainingQuota(UserDashboardCardsDTO.RemainingQuotaCardDTO.builder()
                        .remainingQuota(84580L)
                        .percentageChange(-15.2)
                        .trend("down")
                        .totalQuota(100000L)
                        .usedQuota(15420L)
                        .usagePercentage(15.42)
                        .estimatedDaysRemaining(45)
                        .status("healthy")
                        .build())
                .lastUpdated(LocalDateTime.now())
                .successRate(97.5)
                .totalApiKeys(3)
                .build();

        // Setup mock API key dashboard
        UUID testApiKeyId = UUID.randomUUID();
        mockApiKeyDashboard = SingleApiKeyDashboardDTO.builder()
                .apiKeyId(testApiKeyId)
                .apiKeyName("Test API Key")
                .registeredDomain("example.com")
                .requestsToday(245L)
                .requestsYesterday(198L)
                .todayVsYesterdayChange(23.7)
                .pendingRequests(3L)
                .usagePercentage(67.5)
                .lastUsed(LocalDateTime.now().minusHours(2))
                .status("active")
                .monthlyMetrics(SingleApiKeyDashboardDTO.MonthlyMetricsDTO.builder()
                        .totalCalls(6750L)
                        .successfulCalls(6580L)
                        .failedCalls(170L)
                        .quotaLimit(10000L)
                        .remainingQuota(3250L)
                        .successRate(97.5)
                        .estimatedDaysToQuotaExhaustion(12)
                        .quotaStatus("healthy")
                        .build())
                .performanceMetrics(SingleApiKeyDashboardDTO.PerformanceMetricsDTO.builder()
                        .averageResponseTime(245.5)
                        .errorRate24h(2.1)
                        .uptime(99.8)
                        .performanceStatus("good")
                        .consecutiveSuccessfulCalls(1250L)
                        .build())
                .rateLimitInfo(SingleApiKeyDashboardDTO.RateLimitInfoDTO.builder()
                        .tier("PRO_TIER")
                        .currentWindowRequests(45)
                        .windowLimit(1000)
                        .windowResetTime(LocalDateTime.now().plusMinutes(30))
                        .rateLimitStatus("normal")
                        .rateLimitUtilization(4.5)
                        .build())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserDashboardCards_Success() throws Exception {
        // Mock JWT extraction
        when(jwtUtil.extractUserID(anyString())).thenReturn("test-user-123");
        when(userDashboardService.getUserDashboardCards("test-user-123")).thenReturn(mockUserDashboard);

        mockMvc.perform(get("/api/v1/dashboard/user/cards")
                        .header("Authorization", "Bearer mock-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApiCalls.totalCalls").value(15420))
                .andExpect(jsonPath("$.totalApiCalls.percentageChange").value(12.5))
                .andExpect(jsonPath("$.totalApiCalls.trend").value("up"))
                .andExpect(jsonPath("$.activeDomains.activeDomains").value(8))
                .andExpect(jsonPath("$.domainsAdded.domainsAdded").value(3))
                .andExpect(jsonPath("$.remainingQuota.remainingQuota").value(84580))
                .andExpect(jsonPath("$.successRate").value(97.5))
                .andExpect(jsonPath("$.totalApiKeys").value(3));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserDashboardCards_WithRefresh() throws Exception {
        // Mock JWT extraction
        when(jwtUtil.extractUserID(anyString())).thenReturn("test-user-123");
        when(userDashboardService.refreshUserDashboardCards("test-user-123")).thenReturn(mockUserDashboard);

        mockMvc.perform(get("/api/v1/dashboard/user/cards")
                        .param("refresh", "true")
                        .header("Authorization", "Bearer mock-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApiCalls.totalCalls").value(15420));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getApiKeyDashboard_Success() throws Exception {
        UUID testApiKeyId = UUID.randomUUID();
        
        // Mock JWT extraction
        when(jwtUtil.extractUserID(anyString())).thenReturn("test-user-123");
        when(apiKeyDashboardService.getApiKeyDashboard(testApiKeyId, "test-user-123")).thenReturn(mockApiKeyDashboard);

        mockMvc.perform(get("/api/v1/dashboard/api-key/{apiKeyId}", testApiKeyId)
                        .header("Authorization", "Bearer mock-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyName").value("Test API Key"))
                .andExpect(jsonPath("$.registeredDomain").value("example.com"))
                .andExpect(jsonPath("$.requestsToday").value(245))
                .andExpect(jsonPath("$.requestsYesterday").value(198))
                .andExpect(jsonPath("$.todayVsYesterdayChange").value(23.7))
                .andExpect(jsonPath("$.pendingRequests").value(3))
                .andExpect(jsonPath("$.usagePercentage").value(67.5))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.monthlyMetrics.totalCalls").value(6750))
                .andExpect(jsonPath("$.monthlyMetrics.successRate").value(97.5))
                .andExpect(jsonPath("$.performanceMetrics.averageResponseTime").value(245.5))
                .andExpect(jsonPath("$.rateLimitInfo.tier").value("PRO_TIER"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getApiKeyDashboard_NotFound() throws Exception {
        UUID testApiKeyId = UUID.randomUUID();
        
        // Mock JWT extraction
        when(jwtUtil.extractUserID(anyString())).thenReturn("test-user-123");
        when(apiKeyDashboardService.getApiKeyDashboard(testApiKeyId, "test-user-123")).thenReturn(null);

        mockMvc.perform(get("/api/v1/dashboard/api-key/{apiKeyId}", testApiKeyId)
                        .header("Authorization", "Bearer mock-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("API key not found"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getDashboardHealth_Success() throws Exception {
        // Mock JWT extraction
        when(jwtUtil.extractUserID(anyString())).thenReturn("test-user-123");

        mockMvc.perform(get("/api/v1/dashboard/health")
                        .header("Authorization", "Bearer mock-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.userId").value("test-user-123"))
                .andExpect(jsonPath("$.services.userDashboardService").value("available"))
                .andExpect(jsonPath("$.services.apiKeyDashboardService").value("available"));
    }

    @Test
    void getUserDashboardCards_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/user/cards")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserDashboardCards_NoData() throws Exception {
        // Mock JWT extraction
        when(jwtUtil.extractUserID(anyString())).thenReturn("test-user-123");
        when(userDashboardService.getUserDashboardCards("test-user-123")).thenReturn(null);

        mockMvc.perform(get("/api/v1/dashboard/user/cards")
                        .header("Authorization", "Bearer mock-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("No dashboard data available"));
    }
}