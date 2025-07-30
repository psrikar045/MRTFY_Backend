package com.example.jwtauthenticator.filter;

import com.example.jwtauthenticator.config.DynamicAuthenticationStrategy;
import com.example.jwtauthenticator.config.DynamicAuthenticationStrategy.AuthenticationResult;
import com.example.jwtauthenticator.service.ApiKeyRequestLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Dynamic authentication filter that supports multiple authentication methods
 * based on configuration. Provides seamless switching between API key and JWT
 * authentication without code changes.
 * 
 * Features:
 * - Configuration-driven authentication strategy
 * - Multiple authentication method support
 * - Graceful fallback between methods
 * - Detailed error responses
 * - Request path exclusions
 * - Performance optimized
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicAuthenticationFilter extends OncePerRequestFilter {

    private final DynamicAuthenticationStrategy authStrategy;
    private final ObjectMapper objectMapper;
    private final ApiKeyRequestLogService requestLogService;

    @Value("${app.auth.excluded-paths:/api/v1/auth/**,/swagger-ui/**,/v3/api-docs/**,/actuator/**}")
    private String[] excludedPaths;

    @Value("${app.auth.require-auth:true}")
    private boolean requireAuthentication;

    @Value("${app.auth.detailed-errors:true}")
    private boolean detailedErrors;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        log.debug("Processing request: {} {}", method, requestPath);

        // Skip authentication for excluded paths
        if (isPathExcluded(requestPath)) {
            log.debug("Path excluded from authentication: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if authentication is not required
        if (!requireAuthentication) {
            log.debug("Authentication not required, proceeding without auth");
            filterChain.doFilter(request, response);
            return;
        }

        // Skip if already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            log.debug("Request already authenticated, skipping");
            long startTime = System.currentTimeMillis();
            filterChain.doFilter(request, response);
            long responseTime = System.currentTimeMillis() - startTime;
            AuthenticationResult authResult = authStrategy.authenticate(request);
            // Log successful authentication after request processing
            if (authResult.getApiKey() != null) {
                log.info("Logging API request - Key ID: {}, Method: {}, Path: {}, Response Time: {}ms", 
                        authResult.getApiKey().getId(), request.getMethod(), request.getRequestURI(), responseTime);
                requestLogService.logRequest(request, authResult.getApiKey(), response.getStatus(), responseTime,"No Error");
            }
            return;
        }

        try {
            // Attempt authentication using dynamic strategy
            AuthenticationResult authResult = authStrategy.authenticate(request);

            if (authResult.isSuccess()) {
                // Validate IP and domain restrictions (if API key authentication)
                if (authResult.getApiKey() != null) {
                    String clientIp = requestLogService.extractClientIp(request);
                    String domain = requestLogService.extractDomain(request);
                    
                    boolean ipAllowed = requestLogService.validateClientIp(authResult.getApiKey(), clientIp);
                    boolean domainAllowed = requestLogService.validateDomain(authResult.getApiKey(), domain);
                    
                    if (!ipAllowed || !domainAllowed) {
                        log.warn("Access denied due to IP/Domain restrictions - API Key: {}, IP: {}, Domain: {}", 
                                authResult.getApiKey().getId(), clientIp, domain);
                        
                        // Log the security violation
                        requestLogService.logRequest(request, authResult.getApiKey(), 403, null, 
                                "Access denied: IP/Domain restriction violation");
                        
                        handleSecurityViolation(response, ipAllowed, domainAllowed);
                        return;
                    }
                }
                
                // Set up security context
                setupSecurityContext(authResult, request);
                
                // Add authentication info to request attributes
                addAuthInfoToRequest(request, authResult);
                
                log.debug("Authentication successful for user: {} using method: {}", 
                         authResult.getUserId(), authResult.getMethod());
                
                // Process the request
                long startTime = System.currentTimeMillis();
                filterChain.doFilter(request, response);
                long responseTime = System.currentTimeMillis() - startTime;
                
                // Log successful authentication after request processing
                if (authResult.getApiKey() != null) {
                    log.info("Logging API request - Key ID: {}, Method: {}, Path: {}, Response Time: {}ms", 
                            authResult.getApiKey().getId(), request.getMethod(), request.getRequestURI(), responseTime);
                    requestLogService.logRequestAsync(request, authResult.getApiKey(), response.getStatus(), responseTime);
                }
            } else {
                // Authentication failed
                handleAuthenticationFailure(response, authResult);
            }

        } catch (Exception e) {
            log.error("Authentication error for request: {} {}", method, requestPath, e);
            handleAuthenticationError(response, e);
        }
    }

    /**
     * Check if request path is excluded from authentication
     */
    private boolean isPathExcluded(String requestPath) {
        return Arrays.stream(excludedPaths)
                .anyMatch(pattern -> {
                    // Simple wildcard matching
                    if (pattern.endsWith("/**")) {
                        String prefix = pattern.substring(0, pattern.length() - 3);
                        return requestPath.startsWith(prefix);
                    } else if (pattern.endsWith("/*")) {
                        String prefix = pattern.substring(0, pattern.length() - 2);
                        return requestPath.startsWith(prefix) && 
                               requestPath.indexOf('/', prefix.length()) == -1;
                    } else {
                        return requestPath.equals(pattern);
                    }
                });
    }

    /**
     * Set up Spring Security context with authentication
     */
    private void setupSecurityContext(AuthenticationResult authResult, HttpServletRequest request) {
        // Determine user roles/authorities
        List<SimpleGrantedAuthority> authorities = determineAuthorities(authResult);

        // Create authentication token
        UsernamePasswordAuthenticationToken authToken = 
            new UsernamePasswordAuthenticationToken(
                authResult.getUserId(),
                null, // No credentials needed
                authorities
            );

        // Set authentication details
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Set in security context
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    /**
     * Determine user authorities based on authentication result
     */
    private List<SimpleGrantedAuthority> determineAuthorities(AuthenticationResult authResult) {
        // Use ArrayList to allow modifications
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        
        // Default authorities
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        // Add API key specific authorities
        if (authResult.getApiKey() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_API_USER"));
            
            // Add tier-based authorities
            String tier = authResult.getApiKey().getRateLimitTier();
            if (tier != null && !tier.trim().isEmpty()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_TIER_" + tier.toUpperCase()));
            }
        }

        // Add JWT specific authorities
        if (authResult.getMethod() == DynamicAuthenticationStrategy.AuthMethod.JWT) {
            authorities.add(new SimpleGrantedAuthority("ROLE_JWT_USER"));
        }

        return authorities;
    }

    /**
     * Add authentication information to request attributes
     */
    private void addAuthInfoToRequest(HttpServletRequest request, AuthenticationResult authResult) {
        request.setAttribute("auth.userId", authResult.getUserId());
        request.setAttribute("auth.method", authResult.getMethod().name());
        request.setAttribute("auth.timestamp", LocalDateTime.now());
        
        if (authResult.getApiKey() != null) {
            request.setAttribute("auth.apiKey", authResult.getApiKey());
            request.setAttribute("auth.apiKeyId", authResult.getApiKey().getId().toString());
            request.setAttribute("auth.rateLimitTier", authResult.getApiKey().getRateLimitTier());
        }
    }

    /**
     * Handle authentication failure
     */
    private void handleAuthenticationFailure(HttpServletResponse response, 
                                           AuthenticationResult authResult) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Authentication failed");
        errorResponse.put("status", 401);
        errorResponse.put("timestamp", LocalDateTime.now());

        if (detailedErrors) {
            errorResponse.put("message", authResult.getMessage());
            errorResponse.put("method", authResult.getMethod() != null ? 
                            authResult.getMethod().getDisplayName() : "Unknown");
            
            // Add configuration hints
            Map<String, Object> hints = new HashMap<>();
            DynamicAuthenticationStrategy.AuthConfig config = authStrategy.getAuthConfig();
            
            if (authStrategy.isApiKeyAuthEnabled()) {
                hints.put("apiKeyHeader", config.getApiKeyHeader());
                hints.put("apiKeyExample", config.getApiKeyHeader() + ": sk-your-api-key-here");
            }
            
            if (authStrategy.isJwtAuthEnabled()) {
                hints.put("jwtHeader", config.getJwtHeader());
                hints.put("jwtExample", config.getJwtHeader() + ": " + config.getJwtPrefix() + "your-jwt-token-here");
            }
            
            errorResponse.put("authenticationHints", hints);
        }

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Handle authentication error (exceptions)
     */
    private void handleAuthenticationError(HttpServletResponse response, Exception e) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Authentication error");
        errorResponse.put("status", 500);
        errorResponse.put("timestamp", LocalDateTime.now());

        if (detailedErrors) {
            errorResponse.put("message", e.getMessage());
        } else {
            errorResponse.put("message", "Internal authentication error");
        }

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Handle security violation (IP/Domain restriction)
     */
    private void handleSecurityViolation(HttpServletResponse response, 
                                       boolean ipAllowed, boolean domainAllowed) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Access Forbidden");
        errorResponse.put("status", 403);
        errorResponse.put("timestamp", LocalDateTime.now());

        if (detailedErrors) {
            StringBuilder message = new StringBuilder("Access denied due to security restrictions: ");
            if (!ipAllowed) {
                message.append("IP address not allowed");
            }
            if (!domainAllowed) {
                if (!ipAllowed) message.append(", ");
                message.append("Domain not allowed");
            }
            
            errorResponse.put("message", message.toString());
            errorResponse.put("details", Map.of(
                "ipAllowed", ipAllowed,
                "domainAllowed", domainAllowed
            ));
        } else {
            errorResponse.put("message", "Access denied");
        }

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Check if filter should be applied to this request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Additional logic to skip filter if needed
        String userAgent = request.getHeader("User-Agent");
        
        // Skip for health checks
        if (userAgent != null && userAgent.contains("HealthCheck")) {
            return true;
        }

        // Skip for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        return false;
    }
}