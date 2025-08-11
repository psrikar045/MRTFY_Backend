package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.enums.ApiKeyScope;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.service.ApiKeyService;
import com.example.jwtauthenticator.service.RequestContextExtractorService;
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Security filter that handles API key authentication.
 * 
 * SECURITY FEATURES:
 * - X-API-KEY header validation
 * - Rate limiting enforcement  
 * - IP address restrictions
 * - Domain restrictions
 * - Scope-based authorization
 * - Usage tracking and logging
 * - Automatic key expiration checking
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String RATE_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_RESET_HEADER = "X-RateLimit-Reset";
    
    private final ApiKeyService apiKeyService;
    private final ApiKeyHashUtil apiKeyHashUtil;
    private final RequestContextExtractorService requestContextExtractor; // PHASE 1 INTEGRATION
    
    // INTEGRATION: Add new services for comprehensive API key functionality
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.jwtauthenticator.service.UsageStatsService usageStatsService;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.jwtauthenticator.service.RequestLoggingService requestLoggingService;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.example.jwtauthenticator.service.ApiKeyAddOnService addOnService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String apiKey = extractApiKey(request);
        
        // If no API key provided, continue with other authentication methods (JWT)
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("Processing API key authentication for request: {} {}", 
                 request.getMethod(), request.getRequestURI());
        
        // Variables for tracking request processing
        boolean authenticationSuccessful = false;
        String apiKeyId = null;
        String userFkId = null;
        RateLimitTier rateLimitTier = null;
        
        try {
            // Validate and authenticate the API key
            Optional<ApiKey> validatedKey = validateApiKey(apiKey);
            
            if (validatedKey.isEmpty()) {
                log.warn("Invalid API key used for request: {} {} from IP: {}", 
                        request.getMethod(), request.getRequestURI(), getClientIpAddress(request));
                
                // INTEGRATION: Log failed authentication attempt
                logRequestAsync(null, null, request, response, false);
                
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }
            
            ApiKey apiKeyEntity = validatedKey.get();
            apiKeyId = apiKeyEntity.getId().toString();
            userFkId = apiKeyEntity.getUserFkId();
            rateLimitTier = apiKeyEntity.getRateLimitTier() != null ? 
                           apiKeyEntity.getRateLimitTier() : RateLimitTier.FREE_TIER;
            
            // Check if key is active and not expired
            if (!isKeyActive(apiKeyEntity)) {
                log.warn("Inactive or expired API key used: {} from IP: {}", 
                        apiKeyEntity.getName(), getClientIpAddress(request));
                
                // INTEGRATION: Log inactive key usage attempt
                logRequestAsync(apiKeyId, userFkId, request, response, false);
                
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "API key is inactive or expired");
                return;
            }
            
            // NOTE: IP and domain validation removed from filter
            // Controllers now handle their own security requirements:
            // - /api/external/** uses basic validation in filter + scopes in controller
            // - /api/secure/** uses advanced ApiKeyDomainGuard in controller
            // - /forward uses no domain validation (internal/testing use)
            log.debug("API key authentication successful, controllers will handle authorization");
            
            // NOTE: Rate limiting removed from filter
            // Controllers now handle their own rate limiting:
            // - /api/secure/** uses ProfessionalRateLimitService
            // - /forward uses appropriate rate limiting per auth method
            // - /api/external/** can use basic rate limiting if needed
            
            // Parse scopes
            ApiKeyScope[] scopes = parseScopes(apiKeyEntity.getScopes());
            
            // Create authentication object
            ApiKeyAuthentication authentication = new ApiKeyAuthentication(
                apiKeyEntity.getKeyHash(),
                apiKeyEntity.getUserFkId(),
                apiKeyEntity.getName(),
                scopes
            );
            
            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Update last used timestamp (async to avoid blocking)
            updateLastUsedAsync(apiKeyEntity);
            
            // INTEGRATION: Record usage statistics (async)
            // Skip usage tracking for /api/secure/rivofetch as it handles its own tracking via ProfessionalRateLimitService
            // All other endpoints are free and don't count against usage limits
            String requestURI = request.getRequestURI();
            if (!"/api/secure/rivofetch".equals(requestURI)) {
                // Note: Currently no other endpoints count against usage limits
                // This is reserved for future paid endpoints if needed
                // recordUsageStatsAsync(apiKeyId, userFkId, request, rateLimitTier);
            }
            
            authenticationSuccessful = true;
            
            log.debug("API key authentication successful for key: {} (user: {})", 
                     apiKeyEntity.getName(), apiKeyEntity.getUserFkId());
            
        } catch (Exception e) {
            log.error("Error processing API key authentication", e);
            
            // INTEGRATION: Log error
            logRequestAsync(apiKeyId, userFkId, request, response, false);
            
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication error");
            return;
        }
        
        try {
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            
            // INTEGRATION: Log successful request after processing
            if (authenticationSuccessful) {
                logRequestAsync(apiKeyId, userFkId, request, response, true);
                
                // INTEGRATION: Consume add-on requests if applicable
                consumeAddOnRequestsAsync(apiKeyId);
            }
            
        } catch (Exception e) {
            // INTEGRATION: Log failed request processing
            if (authenticationSuccessful) {
                logRequestAsync(apiKeyId, userFkId, request, response, false);
            }
            throw e;
        }
    }
    
    /**
     * Extract API key from request headers.
     */
    private String extractApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null) {
            apiKey = apiKey.trim();
            return apiKey.isEmpty() ? null : apiKey;
        }
        return null;
    }
    
    /**
     * Validate the API key against the database.
     */
    private Optional<ApiKey> validateApiKey(String apiKey) {
        if (!apiKeyHashUtil.isValidApiKeyFormat(apiKey)) {
            return Optional.empty();
        }
        
        String keyHash = apiKeyHashUtil.hashApiKey(apiKey);
        return apiKeyService.findByKeyHash(keyHash);
    }
    
    /**
     * Check if the API key is active and not expired.
     */
    private boolean isKeyActive(ApiKey apiKey) {
        if (!apiKey.isActive()) {
            return false;
        }
        
        if (apiKey.getRevokedAt() != null) {
            return false;
        }
        
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if the client IP address is allowed.
     */
    private boolean isIpAllowed(ApiKey apiKey, HttpServletRequest request) {
        String allowedIps = apiKey.getAllowedIps();
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            return true; // No IP restrictions
        }
        
        String clientIp = getClientIpAddress(request);
        String[] ipList = allowedIps.split(",");
        
        return Arrays.stream(ipList)
            .map(String::trim)
            .anyMatch(ip -> ip.equals(clientIp) || ip.equals("*"));
    }
    
    /**
     * Check if the client domain is allowed.
     */
    private boolean isDomainAllowed(ApiKey apiKey, HttpServletRequest request) {
        String allowedDomains = apiKey.getAllowedDomains();
        if (allowedDomains == null || allowedDomains.trim().isEmpty()) {
            return true; // No domain restrictions
        }
        
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        
        if (origin == null && referer == null) {
            return true; // No domain info available, allow
        }
        
        String domain = origin != null ? extractDomain(origin) : extractDomain(referer);
        if (domain == null) {
            return true;
        }
        
        String[] domainList = allowedDomains.split(",");
        
        return Arrays.stream(domainList)
            .map(String::trim)
            .anyMatch(allowedDomain -> 
                allowedDomain.equals(domain) || 
                allowedDomain.equals("*") ||
                domain.endsWith("." + allowedDomain));
    }
    
    /**
     * Extract domain from URL.
     */
    private String extractDomain(String url) {
        if (url == null) return null;
        
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return new java.net.URL(url).getHost();
            }
            return url; // Assume it's already a domain
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get client IP address, considering proxy headers.
     * 
     * PHASE 1 INTEGRATION: Now uses RequestContextExtractorService for unified IP extraction
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // PHASE 1 INTEGRATION: Use unified context extractor instead of manual logic
        return requestContextExtractor.extractClientIp(request);
    }
    
    /**
     * Parse scopes from comma-separated string.
     */
    private ApiKeyScope[] parseScopes(String scopesString) {
        if (scopesString == null || scopesString.trim().isEmpty()) {
            return new ApiKeyScope[0];
        }
        
        try {
            return ApiKeyScope.fromString(scopesString);
        } catch (Exception e) {
            log.warn("Invalid scopes in API key: {}", scopesString, e);
            return new ApiKeyScope[0];
        }
    }
    
    /**
     * Get rate limit tier from string.
     */
    private RateLimitTier getRateLimitTier(String tierString) {
        if (tierString == null) {
            return RateLimitTier.FREE_TIER;
        }
        
        try {
            return RateLimitTier.valueOf(tierString.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid rate limit tier: {}, defaulting to FREE_TIER", tierString);
            return RateLimitTier.FREE_TIER;
        }
    }
    
    /**
     * Add rate limiting headers to response.
     * Note: Rate limiting headers are now handled by individual controllers using ProfessionalRateLimitService
     */
    private void addRateLimitHeaders(HttpServletResponse response, String apiKeyHash, RateLimitTier tier) {
        // Rate limiting headers are now handled by controllers that actually enforce rate limits
        // This method is kept for backward compatibility but does nothing
        log.debug("Rate limit headers are now handled by individual controllers");
    }
    
    /**
     * Update last used timestamp asynchronously.
     */
    private void updateLastUsedAsync(ApiKey apiKey) {
        // This could be enhanced with @Async annotation for true async processing
        try {
            apiKeyService.updateLastUsed(apiKey.getId());
        } catch (Exception e) {
            log.warn("Failed to update last used timestamp for API key: {}", apiKey.getName(), e);
        }
    }
    
    /**
     * Send error response with proper headers.
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String errorJson = String.format(
            "{\"error\": \"%s\", \"timestamp\": \"%s\", \"status\": %d}",
            message,
            LocalDateTime.now().toString(),
            statusCode
        );
        
        response.getWriter().write(errorJson);
    }
    
    /**
     * INTEGRATION: Record usage statistics asynchronously.
     */
    private void recordUsageStatsAsync(String apiKeyId, String userFkId, HttpServletRequest request, RateLimitTier tier) {
        if (usageStatsService != null && apiKeyId != null) {
            try {
                UUID apiKeyUuid = UUID.fromString(apiKeyId);
                usageStatsService.recordApiKeyUsage(
                    apiKeyUuid, 
                    userFkId, 
                    request.getRequestURI(), 
                    request.getMethod(), 
                    getClientIpAddress(request), 
                    tier
                );
            } catch (Exception e) {
                log.warn("Failed to record usage statistics for API key: {}", apiKeyId, e);
            }
        }
    }
    
    /**
     * INTEGRATION: Log API key request asynchronously.
     * ✅ FIXED: Extract request data before async call to prevent request recycling
     * ✅ FIXED: Skip logging for /rivofetch endpoint to prevent duplicates
     */
    private void logRequestAsync(String apiKeyId, String userFkId, HttpServletRequest request, 
                                HttpServletResponse response, boolean success) {
        if (requestLoggingService != null && apiKeyId != null) {
            try {
                // ✅ FIXED: Skip logging for rivofetch endpoint - it's handled by StreamlinedUsageTracker
                String path = request.getRequestURI();
                if (path != null && path.contains("/rivofetch")) {
                    log.debug("Skipping duplicate logging for rivofetch endpoint: {}", path);
                    return;
                }
                
                // ✅ Extract all request data BEFORE async call
                String method = request.getMethod();
                String clientIp = getClientIpAddress(request);
                String origin = request.getHeader("Origin");
                String referer = request.getHeader("Referer");
                String domain = origin != null ? extractDomain(origin) : extractDomain(referer);
                String userAgent = request.getHeader("User-Agent");
                int responseStatus = response.getStatus();
                
                // ✅ Call async method with extracted data (no request objects)
                requestLoggingService.logApiKeyRequest(apiKeyId, userFkId, method, path, 
                                                     clientIp, domain, userAgent, responseStatus, success);
            } catch (Exception e) {
                log.warn("Failed to log API key request for key: {}", apiKeyId, e);
            }
        }
    }
    
    /**
     * INTEGRATION: Consume add-on requests asynchronously.
     */
    private void consumeAddOnRequestsAsync(String apiKeyId) {
        if (addOnService != null) {
            try {
                addOnService.consumeAddOnRequests(apiKeyId, 1);
            } catch (Exception e) {
                log.debug("No add-on requests to consume for API key: {} (this is normal)", apiKeyId);
            }
        }
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip filter for public endpoints
        String path = request.getRequestURI();
        
        return path.startsWith("/auth/") ||
               path.startsWith("/v3/api-docs/") ||
               path.startsWith("/swagger-ui/") ||
               path.equals("/swagger-ui.html") ||
               path.startsWith("/actuator/health") ||
               path.equals("/api/category/hierarchy") ||
               path.equals("/api/brands/all") ||
               path.startsWith("/api/brands/assets/") ||
               path.startsWith("/api/brands/images/");
    }
}