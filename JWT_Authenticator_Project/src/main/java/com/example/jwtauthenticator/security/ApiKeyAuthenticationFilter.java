package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.enums.ApiKeyScope;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.service.ApiKeyService;
import com.example.jwtauthenticator.service.RateLimitService;
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
    private final RateLimitService rateLimitService;
    private final ApiKeyHashUtil apiKeyHashUtil;
    
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
        
        try {
            // Validate and authenticate the API key
            Optional<ApiKey> validatedKey = validateApiKey(apiKey);
            
            if (validatedKey.isEmpty()) {
                log.warn("Invalid API key used for request: {} {} from IP: {}", 
                        request.getMethod(), request.getRequestURI(), getClientIpAddress(request));
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }
            
            ApiKey apiKeyEntity = validatedKey.get();
            
            // Check if key is active and not expired
            if (!isKeyActive(apiKeyEntity)) {
                log.warn("Inactive or expired API key used: {} from IP: {}", 
                        apiKeyEntity.getName(), getClientIpAddress(request));
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "API key is inactive or expired");
                return;
            }
            
            // Check IP restrictions
            if (!isIpAllowed(apiKeyEntity, request)) {
                log.warn("API key {} used from unauthorized IP: {}", 
                        apiKeyEntity.getName(), getClientIpAddress(request));
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "IP address not allowed");
                return;
            }
            
            // Check domain restrictions
            if (!isDomainAllowed(apiKeyEntity, request)) {
                log.warn("API key {} used from unauthorized domain: {}", 
                        apiKeyEntity.getName(), request.getHeader("Origin"));
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Domain not allowed");
                return;
            }
            
            // Check rate limits
            RateLimitTier rateLimitTier = getRateLimitTier(apiKeyEntity.getRateLimitTier());
            if (!rateLimitService.isAllowed(apiKeyEntity.getKeyHash(), rateLimitTier)) {
                log.warn("Rate limit exceeded for API key: {} from IP: {}", 
                        apiKeyEntity.getName(), getClientIpAddress(request));
                addRateLimitHeaders(response, apiKeyEntity.getKeyHash(), rateLimitTier);
                sendErrorResponse(response, 429, "Rate limit exceeded");
                return;
            }
            
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
            
            // Add rate limit headers to response
            addRateLimitHeaders(response, apiKeyEntity.getKeyHash(), rateLimitTier);
            
            // Update last used timestamp (async to avoid blocking)
            updateLastUsedAsync(apiKeyEntity);
            
            log.debug("API key authentication successful for key: {} (user: {})", 
                     apiKeyEntity.getName(), apiKeyEntity.getUserFkId());
            
        } catch (Exception e) {
            log.error("Error processing API key authentication", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication error");
            return;
        }
        
        filterChain.doFilter(request, response);
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
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
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
            return RateLimitTier.BASIC;
        }
        
        try {
            return RateLimitTier.valueOf(tierString.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid rate limit tier: {}, defaulting to BASIC", tierString);
            return RateLimitTier.BASIC;
        }
    }
    
    /**
     * Add rate limiting headers to response.
     */
    private void addRateLimitHeaders(HttpServletResponse response, String apiKeyHash, RateLimitTier tier) {
        try {
            RateLimitService.RateLimitStatus status = rateLimitService.getRateLimitStatus(apiKeyHash, tier);
            
            response.setHeader(RATE_LIMIT_HEADER, String.valueOf(status.getMaxRequests()));
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(status.getRemainingRequests()));
            response.setHeader(RATE_LIMIT_RESET_HEADER, String.valueOf(status.getWindowEnd().toEpochSecond(java.time.ZoneOffset.UTC)));
        } catch (Exception e) {
            log.warn("Failed to add rate limit headers", e);
        }
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