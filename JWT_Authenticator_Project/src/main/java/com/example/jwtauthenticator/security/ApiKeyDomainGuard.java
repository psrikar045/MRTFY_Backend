package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.service.ApiKeyAuthenticationService;
import com.example.jwtauthenticator.service.DomainValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Security guard for API key-based access with domain validation.
 * Provides comprehensive validation including API key authentication and domain restrictions.
 * 
 * Features:
 * - API key authentication
 * - Domain validation against registered and allowed domains
 * - Support for all TLD types (.com, .org, .io, .in, .co, etc.)
 * - Detailed error reporting
 * - Security violation logging
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyDomainGuard {

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    private final DomainValidationService domainValidationService;

    /**
     * Validate API key access with domain restrictions
     */
    public SecurityValidationResult validateApiKeyAccess(String apiKeyValue, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        log.debug("Starting API key validation for {} {}", method, requestPath);

        try {
            // Step 1: Authenticate API key
            ApiKeyAuthenticationService.ApiKeyAuthResult authResult = 
                apiKeyAuthenticationService.authenticateApiKey(apiKeyValue);

            if (!authResult.isSuccess()) {
                log.warn("API key authentication failed: {}", authResult.getErrorMessage());
                return SecurityValidationResult.authFailure(
                    authResult.getErrorMessage(),
                    "INVALID_API_KEY"
                );
            }

            ApiKey apiKey = authResult.getApiKey();
            User user = authResult.getUser();

            log.info("API key authenticated successfully: {} for user: {}", 
                    apiKey.getId(), user.getId());

            // Step 2: Extract domain from request (automatic)
            String requestDomain = domainValidationService.extractDomainFromRequest(request);
            
            if (requestDomain == null) {
                log.info("No domain headers found for API key: {} - Attempting IP fallback validation", apiKey.getId());
                
                // Step 2.1: Try IP-based validation as fallback
                String clientIp = extractClientIp(request);
                log.info("Automatically extracted client IP: {} for API key: {}", clientIp, apiKey.getId());
                
                if (validateIpAccess(apiKey, clientIp)) {
                    log.info("✅ IP fallback validation successful for API key: {} - IP: {}", apiKey.getId(), clientIp);
                    
                    long validationTime = System.currentTimeMillis() - startTime;
                    log.info("API key validation successful via IP fallback for {} {} - IP: '{}', Time: {}ms", 
                            method, requestPath, clientIp, validationTime);
                    
                    return SecurityValidationResult.success(
                        apiKey, 
                        user, 
                        "IP:" + clientIp,  // Use IP as domain identifier
                        clientIp, 
                        "IP_FALLBACK"
                    );
                } else {
                    log.warn("❌ IP fallback validation failed for API key: {} - IP: {} not in allowed list", 
                            apiKey.getId(), clientIp);
                    
                    return SecurityValidationResult.domainFailure(
                        String.format("Access denied. No domain headers found and IP address %s is not in allowed list. " +
                                     "Either include Origin/Referer header or add IP to allowed list.", clientIp),
                        "IP_NOT_ALLOWED_NO_DOMAIN",
                        "IP:" + clientIp,
                        apiKey.getRegisteredDomain()
                    );
                }
            }

            log.debug("Extracted request domain: '{}' for API key: {}", requestDomain, apiKey.getId());

            // Step 3: Validate domain (normal flow)
            DomainValidationService.DomainValidationResult domainResult = 
                domainValidationService.validateDomain(apiKey, requestDomain);

            if (!domainResult.isValid()) {
                log.warn("Domain validation failed for API key {}: {} (Code: {})", 
                        apiKey.getId(), domainResult.getMessage(), domainResult.getErrorCode());
                
                return SecurityValidationResult.domainFailure(
                    domainResult.getMessage(),
                    domainResult.getErrorCode(),
                    requestDomain,
                    apiKey.getRegisteredDomain()
                );
            }

            long validationTime = System.currentTimeMillis() - startTime;
            log.info("API key validation successful for {} {} - Domain: '{}', Match: '{}', Time: {}ms", 
                    method, requestPath, requestDomain, domainResult.getMatchedDomain(), validationTime);

            return SecurityValidationResult.success(
                apiKey, 
                user, 
                requestDomain, 
                domainResult.getMatchedDomain(),
                domainResult.getErrorCode() // SUCCESS_PRIMARY_MATCH or SUCCESS_ADDITIONAL_MATCH
            );

        } catch (Exception e) {
            long validationTime = System.currentTimeMillis() - startTime;
            log.error("API key validation error for {} {} (Time: {}ms): {}", 
                     method, requestPath, validationTime, e.getMessage(), e);
            
            return SecurityValidationResult.error(
                "Internal validation error: " + e.getMessage(),
                "VALIDATION_ERROR"
            );
        }
    }

    /**
     * Extract API key from request headers
     */
    public String extractApiKeyFromRequest(HttpServletRequest request) {
        // Priority order for API key extraction
        String[] headerNames = {
            "x-api-key",        // Primary header
            "X-API-Key",        // Alternative casing
            "Authorization"     // Bearer token format
        };

        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.trim().isEmpty()) {
                // Handle Authorization header with Bearer prefix
                if ("Authorization".equalsIgnoreCase(headerName) && headerValue.startsWith("Bearer ")) {
                    // Skip if it looks like a JWT (contains dots)
                    String token = headerValue.substring(7);
                    if (!token.contains(".")) {
                        return token;
                    }
                } else {
                    return headerValue.trim();
                }
            }
        }

        return null;
    }

    /**
     * Extract client IP address automatically from request headers
     * NO USER INPUT REQUIRED - This is completely automatic
     */
    private String extractClientIp(HttpServletRequest request) {
        log.debug("Extracting client IP automatically from request headers");
        
        // Priority order for IP extraction (handles proxies, load balancers, CDNs)
        String[] ipHeaders = {
            "X-Forwarded-For",      // Most common proxy header
            "X-Real-IP",            // Nginx proxy
            "X-Client-IP",          // Apache proxy
            "CF-Connecting-IP",     // Cloudflare
            "True-Client-IP",       // Akamai
            "X-Cluster-Client-IP",  // Cluster environments
            "X-Original-Forwarded-For", // Original forwarded
            "Forwarded"             // RFC 7239 standard
        };

        for (String headerName : ipHeaders) {
            String headerValue = request.getHeader(headerName);
            log.debug("Checking IP header '{}': '{}'", headerName, headerValue);
            
            if (headerValue != null && !headerValue.trim().isEmpty() && !"unknown".equalsIgnoreCase(headerValue)) {
                // X-Forwarded-For can contain multiple IPs: "client, proxy1, proxy2"
                // Take the first one (original client)
                if (headerValue.contains(",")) {
                    String clientIp = headerValue.split(",")[0].trim();
                    log.info("Client IP extracted from '{}' header (first in chain): {}", headerName, clientIp);
                    return clientIp;
                } else {
                    log.info("Client IP extracted from '{}' header: {}", headerName, headerValue.trim());
                    return headerValue.trim();
                }
            }
        }

        // Fallback to direct connection IP
        String remoteAddr = request.getRemoteAddr();
        log.info("Client IP from direct connection (RemoteAddr): {}", remoteAddr);
        return remoteAddr;
    }

    /**
     * Validate if automatically extracted client IP is allowed for the API key
     * NO USER INPUT REQUIRED - Uses IP extracted from headers
     */
    private boolean validateIpAccess(ApiKey apiKey, String clientIp) {
        List<String> allowedIps = apiKey.getAllowedIpsAsList();
        
        if (allowedIps.isEmpty()) {
            log.debug("No IP restrictions configured for API key: {} - allowing all IPs", apiKey.getId());
            return true; // No IP restrictions = allow all
        }

        log.info("Validating automatically extracted IP '{}' against allowed IPs: {}", clientIp, allowedIps);

        for (String allowedIp : allowedIps) {
            if (isIpMatch(clientIp, allowedIp.trim())) {
                log.info("✅ IP match found: {} matches allowed IP {}", clientIp, allowedIp);
                return true;
            }
        }

        log.warn("❌ IP validation failed: {} not in allowed IPs {}", clientIp, allowedIps);
        return false;
    }

    /**
     * Check if client IP matches allowed IP pattern (supports CIDR notation)
     */
    private boolean isIpMatch(String clientIp, String allowedIp) {
        if (clientIp == null || allowedIp == null) {
            return false;
        }

        // Exact IP match
        if (clientIp.equals(allowedIp)) {
            log.debug("Exact IP match: {}", clientIp);
            return true;
        }

        // CIDR notation support (e.g., 192.168.1.0/24)
        if (allowedIp.contains("/")) {
            try {
                String[] parts = allowedIp.split("/");
                String network = parts[0];
                int prefixLength = Integer.parseInt(parts[1]);
                
                boolean matches = isIpInCidr(clientIp, network, prefixLength);
                log.debug("CIDR match check: {} in {}/{} = {}", clientIp, network, prefixLength, matches);
                return matches;
            } catch (Exception e) {
                log.warn("Invalid CIDR notation: {} - {}", allowedIp, e.getMessage());
                return false;
            }
        }

        return false;
    }

    /**
     * Check if IP is in CIDR range (IPv4 implementation)
     */
    private boolean isIpInCidr(String ip, String network, int prefixLength) {
        try {
            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(network);
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            
            return (ipLong & mask) == (networkLong & mask);
        } catch (Exception e) {
            log.warn("Error checking CIDR: {} in {}/{} - {}", ip, network, prefixLength, e.getMessage());
            return false;
        }
    }

    /**
     * Convert IPv4 address to long for CIDR calculations
     */
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }
        
        long result = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i]);
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + octet);
            }
            result = (result << 8) + octet;
        }
        return result;
    }

    /**
     * Security validation result class
     */
    public static class SecurityValidationResult {
        private final boolean success;
        private final String errorMessage;
        private final String errorCode;
        private final ApiKey apiKey;
        private final User user;
        private final String requestDomain;
        private final String matchedDomain;
        private final String validationType;
        private final LocalDateTime timestamp;

        private SecurityValidationResult(boolean success, String errorMessage, String errorCode,
                                       ApiKey apiKey, User user, String requestDomain, 
                                       String matchedDomain, String validationType) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.apiKey = apiKey;
            this.user = user;
            this.requestDomain = requestDomain;
            this.matchedDomain = matchedDomain;
            this.validationType = validationType;
            this.timestamp = LocalDateTime.now();
        }

        public static SecurityValidationResult success(ApiKey apiKey, User user, 
                                                     String requestDomain, String matchedDomain, String validationType) {
            return new SecurityValidationResult(true, null, null, apiKey, user, 
                                              requestDomain, matchedDomain, validationType);
        }

        public static SecurityValidationResult authFailure(String message, String errorCode) {
            return new SecurityValidationResult(false, message, errorCode, null, null, null, null, "AUTH_FAILURE");
        }

        public static SecurityValidationResult domainFailure(String message, String errorCode, 
                                                           String requestDomain, String registeredDomain) {
            return new SecurityValidationResult(false, message, errorCode, null, null, 
                                              requestDomain, registeredDomain, "DOMAIN_FAILURE");
        }

        public static SecurityValidationResult error(String message, String errorCode) {
            return new SecurityValidationResult(false, message, errorCode, null, null, null, null, "ERROR");
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public String getErrorCode() { return errorCode; }
        public ApiKey getApiKey() { return apiKey; }
        public User getUser() { return user; }
        public String getUserId() { return user != null ? user.getId() : null; }
        public String getRequestDomain() { return requestDomain; }
        public String getMatchedDomain() { return matchedDomain; }
        public String getValidationType() { return validationType; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}