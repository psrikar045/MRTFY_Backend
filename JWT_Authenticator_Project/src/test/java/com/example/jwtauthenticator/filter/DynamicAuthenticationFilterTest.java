package com.example.jwtauthenticator.filter;

import com.example.jwtauthenticator.config.DynamicAuthenticationStrategy;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.service.ApiKeyRequestLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled("Dynamic Authentication Filter tests - remove @Disabled when needed")
class DynamicAuthenticationFilterTest {

    @Mock
    private DynamicAuthenticationStrategy authStrategy;

    @Mock
    private ApiKeyRequestLogService requestLogService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private DynamicAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new DynamicAuthenticationFilter(authStrategy, objectMapper, requestLogService);
        
        // Set default configuration values
        ReflectionTestUtils.setField(filter, "excludedPaths", 
            new String[]{"/api/v1/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**"});
        ReflectionTestUtils.setField(filter, "requireAuthentication", true);
        ReflectionTestUtils.setField(filter, "detailedErrors", true);
        
        // Clear security context before each test
        SecurityContextHolder.clearContext();
    }

    @Test
    void testExcludedPath_ShouldSkipAuthentication() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getMethod()).thenReturn("POST");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(authStrategy, never()).authenticate(any());
    }

    @Test
    void testAuthenticationNotRequired_ShouldSkipAuthentication() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(filter, "requireAuthentication", false);
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getMethod()).thenReturn("GET");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(authStrategy, never()).authenticate(any());
    }

    @Test
    void testSuccessfulAuthentication() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getMethod()).thenReturn("GET");
        
        ApiKey mockApiKey = createMockApiKey();
        DynamicAuthenticationStrategy.AuthenticationResult successResult = 
            DynamicAuthenticationStrategy.AuthenticationResult.success(
                "user123", mockApiKey, DynamicAuthenticationStrategy.AuthMethod.API_KEY, "Success");
        
        when(authStrategy.authenticate(request)).thenReturn(successResult);
        
        // Mock request log service calls for IP/Domain validation
        when(requestLogService.extractClientIp(request)).thenReturn("192.168.1.1");
        when(requestLogService.extractDomain(request)).thenReturn("example.com");
        when(requestLogService.validateClientIp(mockApiKey, "192.168.1.1")).thenReturn(true);
        when(requestLogService.validateDomain(mockApiKey, "example.com")).thenReturn(true);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user123", SecurityContextHolder.getContext().getAuthentication().getName());
        
        // Verify request attributes are set
        verify(request).setAttribute("auth.userId", "user123");
        verify(request).setAttribute("auth.method", "API_KEY");
        verify(request).setAttribute(eq("auth.timestamp"), any(LocalDateTime.class));
        verify(request).setAttribute("auth.apiKey", mockApiKey);
        
        // Verify request logging service was called
        verify(requestLogService).logRequestAsync(request, mockApiKey, 200, null);
    }

    @Test
    void testFailedAuthentication() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getMethod()).thenReturn("GET");
        
        DynamicAuthenticationStrategy.AuthenticationResult failureResult = 
            DynamicAuthenticationStrategy.AuthenticationResult.failure(
                "Invalid API key", DynamicAuthenticationStrategy.AuthMethod.API_KEY);
        
        when(authStrategy.authenticate(request)).thenReturn(failureResult);
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        
        DynamicAuthenticationStrategy.AuthConfig mockConfig = 
            DynamicAuthenticationStrategy.AuthConfig.builder()
                .apiKeyHeader("X-API-Key")
                .jwtHeader("Authorization")
                .jwtPrefix("Bearer ")
                .build();
        when(authStrategy.getAuthConfig()).thenReturn(mockConfig);
        when(authStrategy.isApiKeyAuthEnabled()).thenReturn(true);
        when(authStrategy.isJwtAuthEnabled()).thenReturn(false);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        
        printWriter.flush();
        String responseBody = stringWriter.toString();
        assertTrue(responseBody.contains("Authentication failed"));
        assertTrue(responseBody.contains("Invalid API key"));
    }

    @Test
    void testAuthenticationException() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getMethod()).thenReturn("GET");
        
        when(authStrategy.authenticate(request)).thenThrow(new RuntimeException("Database error"));
        
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
        
        printWriter.flush();
        String responseBody = stringWriter.toString();
        assertTrue(responseBody.contains("Authentication error"));
    }

    @Test
    void testShouldNotFilter_OptionsRequest() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("OPTIONS");

        // Act
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Assert
        assertTrue(shouldNotFilter);
    }

    @Test
    void testShouldNotFilter_HealthCheckUserAgent() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("HealthCheck/1.0");

        // Act
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Assert
        assertTrue(shouldNotFilter);
    }

    @Test
    void testShouldNotFilter_RegularRequest() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        // Act
        boolean shouldNotFilter = filter.shouldNotFilter(request);

        // Assert
        assertFalse(shouldNotFilter);
    }

    @Test
    void testPathExclusion_ExactMatch() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getMethod()).thenReturn("GET");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(authStrategy, never()).authenticate(any());
    }

    @Test
    void testPathExclusion_WildcardMatch() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");
        when(request.getMethod()).thenReturn("GET");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        verify(authStrategy, never()).authenticate(any());
    }

    @Test
    void testJwtAuthentication_AuthoritiesAssignment() throws Exception {
        // Arrange
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getMethod()).thenReturn("GET");
        
        DynamicAuthenticationStrategy.AuthenticationResult successResult = 
            DynamicAuthenticationStrategy.AuthenticationResult.success(
                "user456", null, DynamicAuthenticationStrategy.AuthMethod.JWT, "JWT Success");
        
        when(authStrategy.authenticate(request)).thenReturn(successResult);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        
        // Check that JWT-specific authority is added
        boolean hasJwtRole = SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_JWT_USER"));
        assertTrue(hasJwtRole);
    }

    private ApiKey createMockApiKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID());
        apiKey.setUserFkId("user123");
        apiKey.setName("Test API Key");
        apiKey.setActive(true);
        apiKey.setRateLimitTier("BASIC");
        apiKey.setCreatedAt(LocalDateTime.now());
        apiKey.setUpdatedAt(LocalDateTime.now());
        return apiKey;
    }
}