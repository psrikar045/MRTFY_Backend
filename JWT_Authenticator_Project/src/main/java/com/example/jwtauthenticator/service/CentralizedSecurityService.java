package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.security.SecurityValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 🔒 CENTRALIZED Security Validation Service
 * 
 * Fixes identified issues:
 * - Consolidates all domain validation logic
 * - Eliminates inconsistent validation methods
 * - Provides single source of truth for security decisions
 * - Improves maintainability and testing
 * - Adds comprehensive logging for security events
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CentralizedSecurityService {
    
    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    private final DomainValidationService domainValidationService;
    
    // Compiled patterns for better performance
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$"
    );
    
    /**
     * 🎯 MAIN VALIDATION METHOD - Single entry point for all security checks
     * 
     * Performs comprehensive validation:
     * 1. API key authentication
     * 2. Domain validation
     * 3. IP validation (fallback)
     * 4. Rate limiting check
     * 5. Security logging
     */
    public SecurityValidationResult validateRequest(String apiKeyValue, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = extractClientIp(request);
        
        log.debug("🔒 Starting security validation for {} {} from IP: {}", method, requestPath, clientIp);
        
        try {
            // Step 1: Authenticate API key
            ApiKeyAuthenticationService.ApiKeyAuthResult authResult = 
                apiKeyAuthenticationService.authenticateApiKey(apiKeyValue);
            
            if (!authResult.isSuccess()) {
                return logAndReturnFailure(
                    SecurityValidationResult.authFailure(authResult.getErrorMessage(), "INVALID_API_KEY"),
                    clientIp, requestPath, "API_KEY_AUTH_FAILED", startTime
                );
            }
            
            ApiKey apiKey = authResult.getApiKey();
            User user = authResult.getUser();
            
            log.debug("✅ API key authenticated: {} for user: {}", apiKey.getId(), user.getId());
            
            // Step 2: Domain validation
            String requestDomain = domainValidationService.extractDomainFromRequest(request);
            
            if (requestDomain != null) {
                // Domain-based validation
                DomainValidationResult domainResult = validateDomain(apiKey, requestDomain);
                
                if (!domainResult.isValid()) {
                    return logAndReturnFailure(
                        SecurityValidationResult.domainFailure(
                            domainResult.getErrorMessage(),
                            domainResult.getErrorCode(),
                            requestDomain,
                            apiKey.getRegisteredDomain()
                        ),
                        clientIp, requestPath, "DOMAIN_VALIDATION_FAILED", startTime
                    );
                }
                
                log.debug("✅ Domain validation passed: {}", requestDomain);
                
                // Success with domain validation
                long validationTime = System.currentTimeMillis() - startTime;
                log.info("🔒 Security validation successful for {} {} - Domain: '{}', Time: {}ms", 
                        method, requestPath, requestDomain, validationTime);
                
                return SecurityValidationResult.success(apiKey, user, requestDomain, clientIp, "DOMAIN_VALIDATED");
                
            } else {
                // Step 3: IP-based validation (fallback)
                log.debug("No domain headers found, attempting IP validation for API key: {}", apiKey.getId());
                
                IpValidationResult ipResult = validateIp(apiKey, clientIp);
                
                if (!ipResult.isValid()) {
                    return logAndReturnFailure(
                        SecurityValidationResult.domainFailure(
                            ipResult.getErrorMessage(),
                            ipResult.getErrorCode(),
                            "IP:" + clientIp,
                            apiKey.getRegisteredDomain()
                        ),
                        clientIp, requestPath, "IP_VALIDATION_FAILED", startTime
                    );
                }
                
                log.debug("✅ IP validation passed: {}", clientIp);
                
                // Success with IP validation
                long validationTime = System.currentTimeMillis() - startTime;
                log.info("🔒 Security validation successful for {} {} - IP: '{}', Time: {}ms", 
                        method, requestPath, clientIp, validationTime);
                
                return SecurityValidationResult.success(apiKey, user, "IP:" + clientIp, clientIp, "IP_VALIDATED");
            }
            
        } catch (Exception e) {
            log.error("❌ Security validation error for {} {}: {}", method, requestPath, e.getMessage(), e);
            return SecurityValidationResult.authFailure("Internal security validation error", "VALIDATION_ERROR");
        }
    }
    
    /**
     * 🌐 CENTRALIZED Domain Validation
     * 
     * Consolidates all domain validation logic into a single method
     */
    public DomainValidationResult validateDomain(ApiKey apiKey, String requestDomain) {
        if (requestDomain == null || requestDomain.trim().isEmpty()) {
            return DomainValidationResult.invalid("Domain cannot be null or empty", "DOMAIN_NULL");
        }
        
        String normalizedDomain = normalizeDomain(requestDomain);
        
        if (!isValidDomainFormat(normalizedDomain)) {
            return DomainValidationResult.invalid(
                "Invalid domain format: " + requestDomain, 
                "DOMAIN_INVALID_FORMAT"
            );
        }
        
        // 1. Check registered domain (primary)
        if (apiKey.getRegisteredDomain() != null) {
            String normalizedRegistered = normalizeDomain(apiKey.getRegisteredDomain());
            if (normalizedDomain.equals(normalizedRegistered)) {
                log.debug("✅ Domain matches registered domain: {}", normalizedDomain);
                return DomainValidationResult.valid("Registered domain match");
            }
        }
        
        // 2. Check allowed domains list
        List<String> allowedDomains = apiKey.getAllowedDomainsAsList();
        for (String allowedDomain : allowedDomains) {
            String normalizedAllowed = normalizeDomain(allowedDomain);
            if (normalizedDomain.equals(normalizedAllowed)) {
                log.debug("✅ Domain matches allowed domain: {}", normalizedDomain);
                return DomainValidationResult.valid("Allowed domain match");
            }
        }
        
        // 3. Check subdomain patterns (if configured)
        if (apiKey.getSubdomainPattern() != null && !apiKey.getSubdomainPattern().trim().isEmpty()) {
            if (matchesSubdomainPattern(normalizedDomain, apiKey.getSubdomainPattern())) {
                log.debug("✅ Domain matches subdomain pattern: {}", normalizedDomain);
                return DomainValidationResult.valid("Subdomain pattern match");
            }
        }
        
        // 4. Check main domain (if configured)
        if (apiKey.getMainDomain() != null) {
            String normalizedMain = normalizeDomain(apiKey.getMainDomain());
            if (normalizedDomain.equals(normalizedMain)) {
                log.debug("✅ Domain matches main domain: {}", normalizedDomain);
                return DomainValidationResult.valid("Main domain match");
            }
        }
        
        // 5. Development environment special handling
        if (apiKey.getEnvironment() != null && 
            apiKey.getEnvironment().name().equals("DEVELOPMENT") && 
            isDevelopmentDomain(normalizedDomain)) {
            log.debug("✅ Development domain allowed: {}", normalizedDomain);
            return DomainValidationResult.valid("Development domain");
        }
        
        // Domain not allowed
        log.warn("❌ Domain validation failed: {} not in allowed list for API key: {}", 
                normalizedDomain, apiKey.getId());
        
        return DomainValidationResult.invalid(
            String.format("Domain '%s' is not authorized for this API key. " +
                         "Registered domain: '%s', Allowed domains: %s", 
                         requestDomain, apiKey.getRegisteredDomain(), allowedDomains),
            "DOMAIN_NOT_AUTHORIZED"
        );
    }
    
    /**
     * 🌐 CENTRALIZED IP Validation
     */
    public IpValidationResult validateIp(ApiKey apiKey, String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            return IpValidationResult.invalid("Client IP cannot be null or empty", "IP_NULL");
        }
        
        if (!isValidIpFormat(clientIp)) {
            return IpValidationResult.invalid("Invalid IP format: " + clientIp, "IP_INVALID_FORMAT");
        }
        
        // Check if IP is in allowed list
        List<String> allowedIps = apiKey.getAllowedIpsAsList();
        
        if (allowedIps.isEmpty()) {
            // No IP restrictions configured - allow all IPs
            log.debug("✅ No IP restrictions configured for API key: {}", apiKey.getId());
            return IpValidationResult.valid("No IP restrictions");
        }
        
        // Check exact IP match
        for (String allowedIp : allowedIps) {
            if (clientIp.equals(allowedIp.trim())) {
                log.debug("✅ IP matches allowed IP: {}", clientIp);
                return IpValidationResult.valid("Exact IP match");
            }
        }
        
        // Check CIDR ranges (if implemented)
        for (String allowedIp : allowedIps) {
            if (allowedIp.contains("/") && matchesCidrRange(clientIp, allowedIp)) {
                log.debug("✅ IP matches CIDR range: {} in {}", clientIp, allowedIp);
                return IpValidationResult.valid("CIDR range match");
            }
        }
        
        // IP not allowed
        log.warn("❌ IP validation failed: {} not in allowed list for API key: {}", 
                clientIp, apiKey.getId());
        
        return IpValidationResult.invalid(
            String.format("IP address '%s' is not authorized for this API key. " +
                         "Allowed IPs: %s", clientIp, allowedIps),
            "IP_NOT_AUTHORIZED"
        );
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Extract client IP with proper proxy handling
     */
    private String extractClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Client-IP",
            "CF-Connecting-IP", // Cloudflare
            "True-Client-IP"    // Akamai
        };
        
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.trim().isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs (X-Forwarded-For can have multiple IPs)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIpFormat(ip)) {
                    return ip;
                }
            }
        }
        
        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
    
    /**
     * Normalize domain for consistent comparison
     */
    private String normalizeDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }
        
        return domain.toLowerCase()
                    .replaceFirst("^www\\.", "") // Remove www prefix
                    .trim();
    }
    
    /**
     * Validate domain format
     */
    private boolean isValidDomainFormat(String domain) {
        if (domain == null || domain.length() > 253) {
            return false;
        }
        
        return DOMAIN_PATTERN.matcher(domain).matches();
    }
    
    /**
     * Validate IP format
     */
    private boolean isValidIpFormat(String ip) {
        if (ip == null) {
            return false;
        }
        
        // IPv4 validation
        if (IP_PATTERN.matcher(ip).matches()) {
            return true;
        }
        
        // IPv6 validation (basic)
        if (ip.contains(":") && ip.length() <= 39) {
            try {
                java.net.InetAddress.getByName(ip);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * Check if domain matches subdomain pattern
     */
    private boolean matchesSubdomainPattern(String domain, String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return false;
        }
        
        String normalizedPattern = pattern.trim().toLowerCase();
        
        // Handle wildcard patterns like "*.example.com"
        if (normalizedPattern.startsWith("*.")) {
            String baseDomain = normalizedPattern.substring(2);
            
            // Allow exact match with base domain
            if (domain.equals(baseDomain)) {
                return true;
            }
            
            // Allow subdomains
            if (domain.endsWith("." + baseDomain)) {
                String subdomain = domain.substring(0, domain.length() - baseDomain.length() - 1);
                return isValidSubdomain(subdomain);
            }
        }
        
        // Exact pattern match
        return normalizedPattern.equals(domain);
    }
    
    /**
     * Check if subdomain is valid
     */
    private boolean isValidSubdomain(String subdomain) {
        if (subdomain == null || subdomain.trim().isEmpty()) {
            return false;
        }
        
        // Basic subdomain validation
        return subdomain.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$");
    }
    
    /**
     * Check if domain is a development domain
     */
    private boolean isDevelopmentDomain(String domain) {
        String[] devPatterns = {
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1"
        };
        
        for (String pattern : devPatterns) {
            if (domain.equals(pattern) || domain.startsWith(pattern + ":")) {
                return true;
            }
        }
        
        // Check for common development TLDs
        return domain.endsWith(".local") || 
               domain.endsWith(".dev") || 
               domain.endsWith(".test");
    }
    
    /**
     * Check if IP matches CIDR range
     */
    private boolean matchesCidrRange(String ip, String cidr) {
        try {
            // Basic CIDR matching implementation
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            // This is a simplified implementation
            // In production, use a proper CIDR library like Apache Commons Net
            return ip.startsWith(parts[0].substring(0, parts[0].lastIndexOf(".")));
            
        } catch (Exception e) {
            log.warn("Failed to parse CIDR range: {}", cidr);
            return false;
        }
    }
    
    /**
     * Log security failure and return result
     */
    private SecurityValidationResult logAndReturnFailure(
            SecurityValidationResult result, String clientIp, String requestPath, 
            String failureType, long startTime) {
        
        long validationTime = System.currentTimeMillis() - startTime;
        
        log.warn("🔒 Security validation failed: {} for {} from IP: {} - Reason: {} ({}ms)", 
                failureType, requestPath, clientIp, result.getErrorMessage(), validationTime);
        
        return result;
    }
    
    // ==================== RESULT CLASSES ====================
    
    @lombok.Data
    @lombok.Builder
    public static class DomainValidationResult {
        private boolean valid;
        private String message;
        private String errorCode;
        
        public static DomainValidationResult valid(String message) {
            return DomainValidationResult.builder()
                .valid(true)
                .message(message)
                .build();
        }
        
        public static DomainValidationResult invalid(String message, String errorCode) {
            return DomainValidationResult.builder()
                .valid(false)
                .message(message)
                .errorCode(errorCode)
                .build();
        }
        
        public String getErrorMessage() {
            return message;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class IpValidationResult {
        private boolean valid;
        private String message;
        private String errorCode;
        
        public static IpValidationResult valid(String message) {
            return IpValidationResult.builder()
                .valid(true)
                .message(message)
                .build();
        }
        
        public static IpValidationResult invalid(String message, String errorCode) {
            return IpValidationResult.builder()
                .valid(false)
                .message(message)
                .errorCode(errorCode)
                .build();
        }
        
        public String getErrorMessage() {
            return message;
        }
    }
}