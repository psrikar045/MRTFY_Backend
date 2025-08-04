package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Centralized domain validation service for API key-based access control.
 * Handles various domain formats including .com, .org, .io, .in, .co, etc.
 * 
 * Features:
 * - Domain normalization and validation
 * - Support for all TLD types (.com, .org, .io, .in, .co, etc.)
 * - Exact domain matching (Phase 1)
 * - Future: Configurable subdomain matching per API key
 * - Comprehensive logging and error reporting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainValidationService {

    // Enhanced domain pattern to support all common TLDs and development domains
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?" +
        "(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*" +
        "\\.[a-zA-Z]{2,}$"
    );

    // Common TLDs for validation
    private static final List<String> SUPPORTED_TLDS = Arrays.asList(
        "com", "org", "net", "edu", "gov", "mil", "int",
        "co", "io", "ai", "me", "ly", "be", "it", "de", "fr", "uk",
        "in", "cn", "jp", "kr", "au", "ca", "br", "mx", "ru",
        "info", "biz", "name", "pro", "museum", "travel", "jobs",
        "app", "dev", "tech", "online", "site", "website", "store",
        "cloud", "digital", "agency", "studio", "design", "blog"
    );

    /**
     * Validate domain against API key's allowed domains with fallback strategies
     */
    public DomainValidationResult validateDomain(ApiKey apiKey, String requestDomain) {
        log.debug("Validating domain '{}' for API key '{}'", requestDomain, apiKey.getId());

        if (requestDomain == null || requestDomain.trim().isEmpty()) {
            log.debug("Request domain is null/empty, checking fallback validation options for API key: {}", apiKey.getId());
            return handleMissingDomainFallback(apiKey);
        }

        // Normalize the request domain
        String normalizedRequestDomain = normalizeDomain(requestDomain);
        
        if (!isValidDomainFormat(normalizedRequestDomain)) {
            return DomainValidationResult.failure(
                "Invalid domain format: " + requestDomain,
                "INVALID_FORMAT"
            );
        }

        // Check primary registered domain first
        if (apiKey.getRegisteredDomain() != null) {
            String normalizedRegisteredDomain = normalizeDomain(apiKey.getRegisteredDomain());
            if (isExactDomainMatch(normalizedRequestDomain, normalizedRegisteredDomain)) {
                log.info("Domain validation successful - Primary domain match: {} for API key {}", 
                        normalizedRequestDomain, apiKey.getId());
                return DomainValidationResult.success(
                    "Primary registered domain match", 
                    "PRIMARY_MATCH",
                    normalizedRegisteredDomain
                );
            }
        }

        // Check additional allowed domains
        List<String> allowedDomains = apiKey.getAllowedDomainsAsList();
        for (String allowedDomain : allowedDomains) {
            String normalizedAllowedDomain = normalizeDomain(allowedDomain);
            if (isExactDomainMatch(normalizedRequestDomain, normalizedAllowedDomain)) {
                log.info("Domain validation successful - Additional domain match: {} for API key {}", 
                        normalizedRequestDomain, apiKey.getId());
                return DomainValidationResult.success(
                    "Additional allowed domain match", 
                    "ADDITIONAL_MATCH",
                    normalizedAllowedDomain
                );
            }
        }

        // Domain not found in allowed list
        String errorMessage = String.format(
            "Domain '%s' not allowed. Registered: '%s', Additional: [%s]",
            normalizedRequestDomain,
            apiKey.getRegisteredDomain() != null ? apiKey.getRegisteredDomain() : "None",
            String.join(", ", allowedDomains)
        );

        log.warn("Domain validation failed for API key {}: {}", apiKey.getId(), errorMessage);
        return DomainValidationResult.failure(errorMessage, "DOMAIN_NOT_ALLOWED");
    }

    /**
     * Extract domain from HTTP request headers
     */
    public String extractDomainFromRequest(HttpServletRequest request) {
        log.debug("Extracting domain from request headers");

        // Priority order for domain extraction
        String[] headerNames = {
            "Origin",           // CORS requests
            "Referer",          // Standard referer header
            "Host",             // Host header
            "X-Forwarded-Host", // Proxy forwarded host
            "X-Original-Host"   // Original host before proxy
        };

        for (String headerName : headerNames) {
            String headerValue = request.getHeader(headerName);
            log.debug("Checking header '{}': '{}'", headerName, headerValue);
            
            if (headerValue != null && !headerValue.trim().isEmpty()) {
                String extractedDomain = extractDomainFromUrl(headerValue);
                if (extractedDomain != null) {
                    log.info("Domain extracted from '{}' header: '{}'", headerName, extractedDomain);
                    return extractedDomain;
                }
            }
        }

        log.warn("Could not extract domain from request headers");
        return null;
    }

    /**
     * Handle missing domain headers with fallback validation strategies
     */
    private DomainValidationResult handleMissingDomainFallback(ApiKey apiKey) {
        log.info("Handling missing domain headers for API key: {} - Checking fallback options", apiKey.getId());

        // Strategy 1: Check if API key has IP restrictions configured
        List<String> allowedIps = apiKey.getAllowedIpsAsList();
        if (!allowedIps.isEmpty()) {
            log.info("API key {} has IP restrictions configured - allowing IP-based validation", apiKey.getId());
            return DomainValidationResult.success(
                "Domain headers missing but IP validation available",
                "IP_FALLBACK_AVAILABLE",
                "IP_VALIDATION"
            );
        }

        // Strategy 2: Check if API key is configured for server-to-server usage
        if (isServerToServerApiKey(apiKey)) {
            log.info("API key {} configured for server-to-server usage - allowing without domain", apiKey.getId());
            return DomainValidationResult.success(
                "Server-to-server API key - domain validation bypassed",
                "SERVER_TO_SERVER",
                "S2S_BYPASS"
            );
        }

        // Strategy 3: Check if API key has special scopes that allow domain-less access
        if (hasSpecialDomainlessScopes(apiKey)) {
            log.info("API key {} has special scopes allowing domain-less access", apiKey.getId());
            return DomainValidationResult.success(
                "Special scopes allow domain-less access",
                "SPECIAL_SCOPES",
                "SCOPE_BYPASS"
            );
        }

        // Strategy 4: Development/testing environment bypass
        if (isDevelopmentEnvironment() && isTestingApiKey(apiKey)) {
            log.warn("Development environment - allowing domain-less access for testing API key: {}", apiKey.getId());
            return DomainValidationResult.success(
                "Development environment - domain validation bypassed for testing",
                "DEV_TESTING_BYPASS",
                "DEV_BYPASS"
            );
        }

        // No fallback options available
        log.warn("No fallback validation options available for API key: {}", apiKey.getId());
        return DomainValidationResult.failure(
            "Could not determine request domain. Configure IP restrictions or use appropriate headers (Origin, Referer, Host).",
            "MISSING_DOMAIN_NO_FALLBACK"
        );
    }

    /**
     * Check if API key is configured for server-to-server usage
     */
    private boolean isServerToServerApiKey(ApiKey apiKey) {
        // Check if API key has server-to-server indicators
        List<String> scopes = apiKey.getScopesAsList();
        
        // Look for scopes that indicate server-to-server usage (based on actual enum values)
        boolean hasS2SScopes = scopes.stream().anyMatch(scope -> 
            scope.equals("FULL_ACCESS") ||           // Full access implies server usage
            scope.equals("ADMIN_ACCESS") ||          // Admin access implies server usage
            scope.equals("SYSTEM_MONITOR") ||        // System monitoring is server-to-server
            scope.equals("MANAGE_API_KEYS") ||       // API key management is server-to-server
            scope.equals("BUSINESS_READ") ||         // Business API scopes are often server-to-server
            scope.equals("BUSINESS_WRITE") ||        // Business API scopes are often server-to-server
            scope.equals("SERVER_ACCESS") ||         // Explicit server-to-server access
            scope.equals("BACKEND_API") ||           // Backend service API access
            scope.equals("SERVICE_ACCESS") ||        // Microservice communication access
            scope.equals("INTERNAL_API")             // Internal API access
        );

        // Check if API key name indicates server usage
        String keyName = apiKey.getName() != null ? apiKey.getName().toLowerCase() : "";
        boolean hasS2SName = keyName.contains("server") || 
                            keyName.contains("backend") || 
                            keyName.contains("service") ||
                            keyName.contains("cron") ||
                            keyName.contains("job") ||
                            keyName.contains("worker") ||
                            keyName.contains("system") ||
                            keyName.contains("internal");

        // Check if prefix indicates server usage
        String prefix = apiKey.getPrefix() != null ? apiKey.getPrefix().toLowerCase() : "";
        boolean hasS2SPrefix = prefix.contains("srv") || 
                              prefix.contains("svc") || 
                              prefix.contains("s2s") ||
                              prefix.contains("sys") ||
                              prefix.contains("int");

        return hasS2SScopes || hasS2SName || hasS2SPrefix;
    }

    /**
     * Check if API key has special scopes that allow domain-less access
     */
    private boolean hasSpecialDomainlessScopes(ApiKey apiKey) {
        List<String> scopes = apiKey.getScopesAsList();
        
        return scopes.stream().anyMatch(scope -> 
            scope.equals("FULL_ACCESS") ||      // Complete system access - bypass all validation
            scope.equals("ADMIN_ACCESS") ||     // Admin access - bypass domain validation
            scope.equals("SYSTEM_MONITOR") ||   // System monitoring - often needs domain-less access
            scope.equals("DOMAINLESS_ACCESS")   // Explicit permission to bypass domain validation
        );
    }

    /**
     * Check if current environment is development/testing
     */
    private boolean isDevelopmentEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "");
        String env = System.getenv("ENVIRONMENT");
        
        return profile.contains("dev") || 
               profile.contains("test") || 
               profile.contains("local") ||
               (env != null && (env.contains("dev") || env.contains("test")));
    }

    /**
     * Check if API key is for testing purposes
     */
    private boolean isTestingApiKey(ApiKey apiKey) {
        String keyName = apiKey.getName() != null ? apiKey.getName().toLowerCase() : "";
        String prefix = apiKey.getPrefix() != null ? apiKey.getPrefix().toLowerCase() : "";
        
        return keyName.contains("test") || 
               keyName.contains("dev") || 
               keyName.contains("demo") ||
               prefix.contains("test") ||
               prefix.contains("dev");
    }

    /**
     * Extract domain from URL or domain string
     */
    public String extractDomainFromUrl(String urlOrDomain) {
        if (urlOrDomain == null || urlOrDomain.trim().isEmpty()) {
            return null;
        }

        String domain = urlOrDomain.trim();

        // Remove protocol if present
        if (domain.startsWith("http://") || domain.startsWith("https://")) {
            domain = domain.replaceFirst("^https?://", "");
        }

        // Remove path, query, and fragment
        int pathIndex = domain.indexOf('/');
        if (pathIndex != -1) {
            domain = domain.substring(0, pathIndex);
        }

        int queryIndex = domain.indexOf('?');
        if (queryIndex != -1) {
            domain = domain.substring(0, queryIndex);
        }

        int fragmentIndex = domain.indexOf('#');
        if (fragmentIndex != -1) {
            domain = domain.substring(0, fragmentIndex);
        }

        // Remove port if present
        int portIndex = domain.lastIndexOf(':');
        if (portIndex != -1 && portIndex > domain.lastIndexOf(']')) { // Handle IPv6
            domain = domain.substring(0, portIndex);
        }

        return normalizeDomain(domain);
    }

    /**
     * Normalize domain for consistent comparison
     * Handles various domain formats: xamply.com, xamplyfy.co, xamplyfy.in, etc.
     */
    public String normalizeDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return null;
        }

        String normalized = domain.toLowerCase().trim();

        // Remove www prefix
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        // Remove trailing dot if present
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    /**
     * Validate domain format supports all TLD types including development domains
     */
    public boolean isValidDomainFormat(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }

        // Special handling for development/testing domains
        if (isDevelopmentDomain(domain)) {
            return true;
        }

        // Basic format validation
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            return false;
        }

        // Extract TLD and validate
        String[] parts = domain.split("\\.");
        if (parts.length < 2) {
            return false;
        }

        String tld = parts[parts.length - 1].toLowerCase();
        
        // Allow all TLDs with 2+ characters (covers .in, .io, .com, .org, etc.)
        return tld.length() >= 2 && tld.matches("^[a-z]+$");
    }

    /**
     * Check if domain is a development/testing domain
     */
    private boolean isDevelopmentDomain(String domain) {
        if (domain == null) return false;
        
        String lowerDomain = domain.toLowerCase();
        
        // Common development domains
        return lowerDomain.equals("localhost") ||
               lowerDomain.startsWith("localhost.") ||
               lowerDomain.equals("127.0.0.1") ||
               lowerDomain.equals("::1") ||
               lowerDomain.startsWith("192.168.") ||
               lowerDomain.startsWith("10.") ||
               lowerDomain.startsWith("172.") ||
               lowerDomain.endsWith(".local") ||
               lowerDomain.endsWith(".test") ||
               lowerDomain.endsWith(".dev") ||
               lowerDomain.contains("localhost") ||
               lowerDomain.contains("postman-echo");
    }

    /**
     * Check for exact domain match (Phase 1 implementation)
     */
    private boolean isExactDomainMatch(String requestDomain, String allowedDomain) {
        if (allowedDomain == null || allowedDomain.trim().isEmpty()) {
            return false;
        }

        String normalizedAllowed = normalizeDomain(allowedDomain);
        return normalizedAllowed != null && normalizedAllowed.equalsIgnoreCase(requestDomain);
    }

    /**
     * Check if domain is available for registration
     */
    public boolean isDomainAvailable(String domain, String excludeApiKeyId) {
        // This will be implemented when we integrate with repository
        // For now, return true
        return true;
    }

    /**
     * Get domain validation suggestions for error messages
     */
    public List<String> getDomainSuggestions(String requestedDomain, ApiKey apiKey) {
        // Future enhancement: provide suggestions for similar domains
        return Arrays.asList(
            "Ensure your request originates from: " + apiKey.getRegisteredDomain(),
            "Check that your domain is correctly configured",
            "Verify the Origin or Referer header is set correctly"
        );
    }

    /**
     * Domain validation result class
     */
    public static class DomainValidationResult {
        private final boolean valid;
        private final String message;
        private final String errorCode;
        private final String matchedDomain;

        private DomainValidationResult(boolean valid, String message, String errorCode, String matchedDomain) {
            this.valid = valid;
            this.message = message;
            this.errorCode = errorCode;
            this.matchedDomain = matchedDomain;
        }

        public static DomainValidationResult success(String message, String errorCode, String matchedDomain) {
            return new DomainValidationResult(true, message, errorCode, matchedDomain);
        }

        public static DomainValidationResult failure(String message, String errorCode) {
            return new DomainValidationResult(false, message, errorCode, null);
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getMatchedDomain() { return matchedDomain; }
    }
}