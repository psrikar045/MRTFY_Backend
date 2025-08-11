package com.example.jwtauthenticator.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


/**
 * 🔧 Unified Request Context Extractor Service
 * 
 * Java 21 optimized service that consolidates the best practices from:
 * - ApiKeyRequestLogService (comprehensive IP extraction)
 * - DomainValidationService (robust domain extraction)
 * 
 * Features:
 * - Modern Java 21 StackWalker for controller detection
 * - Comprehensive IP extraction with proxy header support
 * - Multi-source domain extraction with fallback strategies
 * - Thread-safe operations with virtual thread support
 * - Performance optimized with minimal allocations
 * 
 * @since Java 21
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestContextExtractorService {
    
    private final DomainValidationService domainValidationService;
    
    // Thread-local cache for request context (Java 21 optimized)
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT_CACHE = 
        ThreadLocal.withInitial(() -> null);
    
    /**
     * 🎯 Main method to extract complete request context
     */
    public RequestContext extractRequestContext() {
        // Check thread-local cache first (performance optimization)
        RequestContext cachedContext = REQUEST_CONTEXT_CACHE.get();
        if (cachedContext != null) {
            return cachedContext;
        }
        
        try {
            HttpServletRequest request = getCurrentRequest();
            ControllerContext controllerContext = extractControllerContext();
            AuthenticationContext authenticationContext = extractAuthenticationContext();
            
            RequestContext context = RequestContext.builder()
                .clientIp(extractClientIp(request))
                .domain(extractDomain(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .controllerName(controllerContext.controllerName())
                .methodName(controllerContext.methodName())
                .userId(authenticationContext.userId())
                .apiKeyId(authenticationContext.apiKeyId())
                .requestSource(determineRequestSource(authenticationContext))
                .build();
                
            // Cache for current thread (performance optimization)
            REQUEST_CONTEXT_CACHE.set(context);
            
            log.debug("🎯 Extracted request context: controller={}, method={}, ip={}, domain={}", 
                context.controllerName(), context.methodName(), context.clientIp(), context.domain());
                
            return context;
            
        } catch (Exception e) {
            log.warn("Failed to extract complete request context, returning minimal context", e);
            return createFallbackContext();
        } finally {
            // Clear cache after processing (prevent memory leaks)
            REQUEST_CONTEXT_CACHE.remove();
        }
    }
    
    /**
     * 🌐 Extract client IP using comprehensive proxy header support
     * Reuses the best implementation from ApiKeyRequestLogService
     */
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            log.debug("Request is null, returning fallback IP");
            return "127.0.0.1";
        }
        
        log.debug("🔍 Extracting client IP from request");
        
        // Comprehensive proxy header list (ordered by priority)
        String[] headerNames = {
            "X-Forwarded-For",      // Most common proxy header
            "X-Real-IP",            // Nginx proxy
            "X-Client-IP",          // General purpose
            "CF-Connecting-IP",     // Cloudflare
            "True-Client-IP",       // Akamai CDN
            "X-Cluster-Client-IP",  // Load balancer
            "X-Original-IP",        // Custom proxy setups
            "Proxy-Client-IP",      // Proxy servers
            "WL-Proxy-Client-IP",   // WebLogic proxy
            "HTTP_X_FORWARDED_FOR", // Alternative header format
            "HTTP_CLIENT_IP"        // HTTP client IP
        };

        // Check proxy headers first (performance optimized with Java 21 features)
        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            log.debug("🔍 Checking header {}: {}", headerName, ip);
            
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs (X-Forwarded-For can contain multiple IPs)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].strip(); // Java 21 strip() method
                }
                
                if (isValidIpAddress(ip)) {
                    log.info("✅ Found valid IP from header {}: {}", headerName, ip);
                    return ip;
                }
            }
        }

        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        log.debug("🔍 Checking remote address: {}", remoteAddr);
        
        // Handle IPv6 localhost (Java 21 pattern matching could be used here in future)
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            log.info("🏠 IPv6 localhost detected, converting to IPv4");
            return "127.0.0.1";
        }
        
        // Accept local/private IPs for development
        if (isLocalOrPrivateIp(remoteAddr)) {
            log.info("🏠 Local/private IP detected: {}", remoteAddr);
            return remoteAddr;
        }
        
        if (isValidIpAddress(remoteAddr)) {
            log.info("✅ Using remote address as client IP: {}", remoteAddr);
            return remoteAddr;
        }
        
        log.warn("⚠️ Could not extract valid client IP, using fallback");
        return "127.0.0.1"; // Fallback for local testing
    }
    
    /**
     * 🌍 Extract domain using comprehensive header checking
     * Enhanced version of DomainValidationService implementation
     */
    public String extractDomain(HttpServletRequest request) {
        if (request == null) {
            log.debug("Request is null, returning localhost");
            return "localhost";
        }
        
        log.debug("🌍 Extracting domain from request headers");
        
        // Use the robust domain extraction from DomainValidationService
        String extractedDomain = domainValidationService.extractDomainFromRequest(request);
        
        if (extractedDomain != null) {
            log.info("✅ Domain successfully extracted: {}", extractedDomain);
            return extractedDomain;
        }
        
        // Additional fallback for edge cases
        String host = request.getHeader("Host");
        if (host != null && !host.isBlank()) {
            // Remove port if present
            if (host.contains(":")) {
                host = host.split(":")[0];
            }
            String domain = host.toLowerCase().strip();
            log.info("🔄 Fallback domain extraction successful: {}", domain);
            return domain;
        }
        
        log.warn("⚠️ Could not extract domain, using localhost fallback");
        return "localhost";
    }
    
    /**
     * 🎯 Extract domain from URL string
     * Delegates to DomainValidationService for consistency
     */
    public String extractDomainFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        
        String domain = domainValidationService.extractDomainFromUrl(url);
        log.debug("🌍 Extracted domain from URL '{}': '{}'", url, domain);
        return domain;
    }
    
    /**
     * 🕵️ Extract controller context using Java 21 StackWalker
     * Modern approach replacing traditional reflection
     */
    public ControllerContext extractControllerContext() {
        try {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                    .filter(frame -> frame.getClassName().endsWith("Controller"))
                    .findFirst()
                    .map(frame -> {
                        String className = frame.getClassName();
                        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
                        log.debug("🎯 Detected controller: {} -> {}", simpleClassName, frame.getMethodName());
                        return new ControllerContext(simpleClassName, frame.getMethodName());
                    })
                    .orElseGet(() -> {
                        log.debug("⚠️ No controller detected in stack, using fallback");
                        return new ControllerContext("UnknownController", "unknownMethod");
                    })
                );
        } catch (Exception e) {
            log.warn("Failed to extract controller context using StackWalker", e);
            return new ControllerContext("ErrorController", "errorMethod");
        }
    }
    
    /**
     * 🔐 Extract authentication context (user ID, API key ID)
     */
    public AuthenticationContext extractAuthenticationContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                String principal = authentication.getName();
                Object details = authentication.getDetails();
                
                // Determine if it's JWT or API Key authentication
                if (details instanceof ApiKeyAuthenticationDetails apiKeyDetails) {
                    return new AuthenticationContext(null, apiKeyDetails.getApiKeyId());
                } else if (principal != null && principal.startsWith("DOMBR")) {
                    // JWT authentication with user ID
                    return new AuthenticationContext(principal, null);
                }
            }
            
            return new AuthenticationContext(null, null);
        } catch (Exception e) {
            log.debug("Could not extract authentication context", e);
            return new AuthenticationContext(null, null);
        }
    }
    
    /**
     * 📡 Get current HTTP request from Spring context
     */
    public HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (IllegalStateException e) {
            log.debug("No current request available (likely async context)");
            return null;
        }
    }
    
    /**
     * 🏷️ Determine request source based on authentication context
     */
    public RequestSource determineRequestSource(AuthenticationContext authenticationContext) {
        if (authenticationContext.apiKeyId() != null) {
            return RequestSource.API_KEY;
        } else if (authenticationContext.userId() != null) {
            return RequestSource.JWT_AUTH;
        } else {
            return RequestSource.PUBLIC;
        }
    }
    
    // ================== PRIVATE HELPER METHODS ==================
    
    /**
     * Basic IP address validation (optimized for Java 21)
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // Accept localhost and private IPs for testing
        if (isLocalOrPrivateIp(ip)) {
            return true;
        }

        // Basic IPv4 validation (simplified but effective)
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Check if IP is local or private range
     */
    private boolean isLocalOrPrivateIp(String ip) {
        if (ip == null) return false;
        return "127.0.0.1".equals(ip) || 
               ip.startsWith("192.168.") || 
               ip.startsWith("10.") || 
               ip.startsWith("172.16.") || 
               ip.startsWith("172.17.") || 
               ip.startsWith("172.18.") || 
               ip.startsWith("172.19.") ||
               ip.startsWith("172.2") || // 172.20-29
               ip.startsWith("172.30") ||
               ip.startsWith("172.31");
    }
    
    /**
     * Create fallback context when extraction fails
     */
    private RequestContext createFallbackContext() {
        return RequestContext.builder()
            .clientIp("127.0.0.1")
            .domain("localhost")
            .userAgent("Unknown")
            .controllerName("UnknownController")
            .methodName("unknownMethod")
            .userId(null)
            .apiKeyId(null)
            .requestSource(RequestSource.PUBLIC)
            .build();
    }
    
    // ================== RECORD CLASSES (Java 21 Features) ==================
    
    /**
     * 🎯 Controller context information
     */
    public record ControllerContext(String controllerName, String methodName) {}
    
    /**
     * 🔐 Authentication context information
     */
    public record AuthenticationContext(String userId, String apiKeyId) {}
    
    /**
     * 📋 Complete request context
     */
    public record RequestContext(
        String clientIp,
        String domain,
        String userAgent,
        String controllerName,
        String methodName,
        String userId,
        String apiKeyId,
        RequestSource requestSource
    ) {
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String clientIp;
            private String domain;
            private String userAgent;
            private String controllerName;
            private String methodName;
            private String userId;
            private String apiKeyId;
            private RequestSource requestSource;
            
            public Builder clientIp(String clientIp) { this.clientIp = clientIp; return this; }
            public Builder domain(String domain) { this.domain = domain; return this; }
            public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
            public Builder controllerName(String controllerName) { this.controllerName = controllerName; return this; }
            public Builder methodName(String methodName) { this.methodName = methodName; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder apiKeyId(String apiKeyId) { this.apiKeyId = apiKeyId; return this; }
            public Builder requestSource(RequestSource requestSource) { this.requestSource = requestSource; return this; }
            
            public RequestContext build() {
                return new RequestContext(clientIp, domain, userAgent, controllerName, 
                                        methodName, userId, apiKeyId, requestSource);
            }
        }
    }
    
    /**
     * 📡 Request source enumeration
     */
    public enum RequestSource {
        PUBLIC("Public Access"),
        JWT_AUTH("JWT Authentication"), 
        API_KEY("API Key Authentication");
        
        private final String description;
        
        RequestSource(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 🔑 API Key authentication details placeholder
     * This will be implemented based on your existing authentication system
     */
    public static class ApiKeyAuthenticationDetails {
        private final String apiKeyId;
        
        public ApiKeyAuthenticationDetails(String apiKeyId) {
            this.apiKeyId = apiKeyId;
        }
        
        public String getApiKeyId() {
            return apiKeyId;
        }
    }
}