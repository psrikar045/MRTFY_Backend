package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.service.ApiKeyService;
import com.example.jwtauthenticator.util.ApiKeyHashUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * Security filter specifically for /api/external/** endpoints.
 * Provides basic IP and domain validation for external API access.
 * 
 * This filter runs AFTER ApiKeyAuthenticationFilter and adds additional
 * security checks for external API endpoints only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalApiSecurityFilter extends OncePerRequestFilter {
    
    private final ApiKeyService apiKeyService;
    private final ApiKeyHashUtil apiKeyHashUtil;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Only process if we have API key authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof ApiKeyAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        ApiKeyAuthentication apiKeyAuth = (ApiKeyAuthentication) auth;
        
        try {
            // Get the full API key entity for validation
            Optional<ApiKey> apiKeyOpt = apiKeyService.findByKeyHash(apiKeyAuth.getName());
            if (apiKeyOpt.isEmpty()) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "API key not found");
                return;
            }
            
            ApiKey apiKey = apiKeyOpt.get();
            
            // Check IP restrictions (basic validation)
            if (!isIpAllowed(apiKey, request)) {
                log.warn("External API access denied - IP not allowed for API key: {} from IP: {}", 
                        apiKey.getName(), getClientIpAddress(request));
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, 
                    "IP address not allowed for this API key");
                return;
            }
            
            // Check domain restrictions (basic validation)
            if (!isDomainAllowed(apiKey, request)) {
                log.warn("External API access denied - Domain not allowed for API key: {} from domain: {}", 
                        apiKey.getName(), request.getHeader("Origin"));
                sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, 
                    "Domain not allowed for this API key");
                return;
            }
            
            log.debug("External API security validation passed for API key: {}", apiKey.getName());
            
        } catch (Exception e) {
            log.error("Error in external API security validation", e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Security validation error");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Check if the client IP address is allowed (basic validation).
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
     * Check if the client domain is allowed (basic validation).
     */
    private boolean isDomainAllowed(ApiKey apiKey, HttpServletRequest request) {
        String registeredDomain = apiKey.getRegisteredDomain();
        String allowedDomains = apiKey.getAllowedDomains();
        
        // If no domain restrictions, allow
        if ((registeredDomain == null || registeredDomain.trim().isEmpty()) && 
            (allowedDomains == null || allowedDomains.trim().isEmpty())) {
            return true;
        }
        
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        
        // For external APIs, we require domain headers
        if (origin == null && referer == null) {
            log.debug("External API request missing domain headers - this may be a server-to-server call");
            // Allow if it looks like server-to-server (no browser headers)
            String userAgent = request.getHeader("User-Agent");
            return userAgent == null || !userAgent.toLowerCase().contains("mozilla");
        }
        
        String requestDomain = origin != null ? extractDomain(origin) : extractDomain(referer);
        if (requestDomain == null) {
            return false;
        }
        
        // Check against registered domain
        if (registeredDomain != null && !registeredDomain.trim().isEmpty()) {
            if (requestDomain.equals(registeredDomain) || 
                requestDomain.endsWith("." + registeredDomain)) {
                return true;
            }
        }
        
        // Check against additional allowed domains
        if (allowedDomains != null && !allowedDomains.trim().isEmpty()) {
            String[] domainList = allowedDomains.split(",");
            return Arrays.stream(domainList)
                .map(String::trim)
                .anyMatch(allowedDomain -> 
                    allowedDomain.equals(requestDomain) || 
                    allowedDomain.equals("*") ||
                    requestDomain.endsWith("." + allowedDomain));
        }
        
        return false;
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
        // Only apply to /api/external/** endpoints
        String path = request.getRequestURI();
        return !path.startsWith("/api/external/");
    }
}