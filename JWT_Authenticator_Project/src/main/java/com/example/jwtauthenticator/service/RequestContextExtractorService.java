package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;


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
    private final UserRepository userRepository;
    
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
                .queryString(request != null ? request.getQueryString() : null)
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
                // Special handling for CDN-specific headers
                String processedIp = processCdnSpecificHeader(headerName, ip);
                String bestIp = extractBestIpFromChain(processedIp);
                
                if (bestIp != null && isValidIpAddress(bestIp)) {
                    log.info("✅ Found valid IP from header {}: {}", headerName, bestIp);
                    return bestIp;
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
                    .filter(frame -> {
                        String className = frame.getClassName();
                        // Look for classes ending with Controller or having Spring annotations
                        return className.endsWith("Controller") || 
                               className.contains("controller") ||
                               isSpringControllerClass(frame.getDeclaringClass());
                    })
                    .findFirst()
                    .map(frame -> {
                        String className = frame.getClassName();
                        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
                        String methodName = frame.getMethodName();
                        log.debug("🎯 Detected controller: {} -> {}", simpleClassName, methodName);
                        return new ControllerContext(simpleClassName, methodName);
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
     * 🏷️ Check if a class is a Spring controller
     */
    private boolean isSpringControllerClass(Class<?> clazz) {
        try {
            return clazz.isAnnotationPresent(Controller.class) ||
                   clazz.isAnnotationPresent(RestController.class);
        } catch (Exception e) {
            return false;
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
                } else if (principal != null && principal.startsWith("MRTFY")) {
                    // JWT authentication with user ID already in principal
                    return new AuthenticationContext(principal, null);
                } else if (principal != null && !principal.equals("anonymousUser")) {
                    // JWT authentication with username - need to get user ID
                    String userId = getUserIdFromUsername(principal);
                    return new AuthenticationContext(userId, null);
                }
            }
            
            return new AuthenticationContext(null, null);
        } catch (Exception e) {
            log.debug("Could not extract authentication context", e);
            return new AuthenticationContext(null, null);
        }
    }
    
    /**
     * 🔍 Get user ID from username (for JWT authentication)
     */
    private String getUserIdFromUsername(String username) {
        try {
            Optional<User> userOptional = userRepository.findByUsername(username);
            if (userOptional.isPresent()) {
                String userId = userOptional.get().getId();
                log.debug("Found user ID {} for username {}", userId, username);
                return userId;
            } else {
                log.debug("No user found for username: {}", username);
                return null;
            }
        } catch (Exception e) {
            log.debug("Could not get user ID for username: {}", username, e);
            return null;
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
            return RequestSource.AUTHENTICATED;
        } else {
            return RequestSource.PUBLIC;
        }
    }
    
    // ================== PRIVATE HELPER METHODS ==================
    
    /**
     * Enhanced IP address validation (IPv4 and IPv6 support)
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        try {
            // Use Java's built-in IP validation for both IPv4 and IPv6
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            log.debug("Invalid IP address format: {}", ip);
            return false;
        }
    }
    
    /**
     * Enhanced check for local or private IP ranges using InetAddress
     */
    private boolean isLocalOrPrivateIp(String ip) {
        if (ip == null) return false;
        
        try {
            InetAddress addr = InetAddress.getByName(ip);
            // Use Java's built-in methods for accurate detection
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            // Fallback to string-based check for edge cases
            return "127.0.0.1".equals(ip) || 
                   ip.startsWith("192.168.") || 
                   ip.startsWith("10.") || 
                   (ip.startsWith("172.") && isInRange172(ip));
        }
    }
    
    /**
     * Helper method for 172.16.0.0/12 range validation (fallback only)
     */
    private boolean isInRange172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            int secondOctet = Integer.parseInt(parts[1]);
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Extract best IP from comma-separated chain (Priority: Public > Private > Localhost)
     */
    private String extractBestIpFromChain(String ipChain) {
        if (!ipChain.contains(",")) {
            return ipChain.strip();
        }
        
        String[] ips = ipChain.split(",");
        log.debug("🔗 Analyzing IP chain with {} addresses", ips.length);
        
        String publicIp = null;
        String privateIp = null;
        String localhostIp = null;
        
        for (String ip : ips) {
            ip = ip.strip();
            if (!isValidIpAddress(ip)) continue;
            
            if (isPublicIp(ip)) {
                publicIp = ip;
                log.debug("🌍 Found public IP in chain: {}", ip);
                break; // Public IP has highest priority
            } else if (isLocalOrPrivateIp(ip)) {
                if (isLocalhostIp(ip) && localhostIp == null) {
                    localhostIp = ip;
                } else if (privateIp == null) {
                    privateIp = ip;
                }
            }
        }
        
        String selectedIp = publicIp != null ? publicIp : 
                           privateIp != null ? privateIp : 
                           localhostIp != null ? localhostIp : 
                           ips[0].strip(); // Fallback to first IP
        
        log.debug("🎯 Selected IP from chain: {}", selectedIp);
        return selectedIp;
    }
    
    /**
     * Check if IP is public (not private/localhost)
     */
    private boolean isPublicIp(String ip) {
        return isValidIpAddress(ip) && !isLocalOrPrivateIp(ip);
    }
    
    /**
     * Check if IP is localhost
     */
    private boolean isLocalhostIp(String ip) {
        if (ip == null) return false;
        
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1".equals(ip) || "::1".equals(ip);
        }
    }
    
    /**
     * Process CDN-specific header formats
     */
    private String processCdnSpecificHeader(String headerName, String headerValue) {
        // AWS CloudFront uses "IP:PORT" format
        if ("CloudFront-Viewer-Address".equals(headerName) && headerValue.contains(":")) {
            String[] parts = headerValue.split(":");
            if (parts.length >= 2) {
                log.debug("🌩️ CloudFront header detected, extracting IP from: {}", headerValue);
                return parts[0]; // Return just the IP part
            }
        }
        
        // For other headers, return as-is
        return headerValue;
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
            .queryString(null)
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
        RequestSource requestSource,
        String queryString
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
            private String queryString;
            
            public Builder clientIp(String clientIp) { this.clientIp = clientIp; return this; }
            public Builder domain(String domain) { this.domain = domain; return this; }
            public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
            public Builder controllerName(String controllerName) { this.controllerName = controllerName; return this; }
            public Builder methodName(String methodName) { this.methodName = methodName; return this; }
            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder apiKeyId(String apiKeyId) { this.apiKeyId = apiKeyId; return this; }
            public Builder requestSource(RequestSource requestSource) { this.requestSource = requestSource; return this; }
            public Builder queryString(String queryString) { this.queryString = queryString; return this; }
            
            public RequestContext build() {
                return new RequestContext(clientIp, domain, userAgent, controllerName, 
                                        methodName, userId, apiKeyId, requestSource, queryString);
            }
        }
    }
    
    /**
     * 📡 Request source enumeration
     */
    public enum RequestSource {
        PUBLIC("Public Access"),
        AUTHENTICATED("Authenticated Access"), 
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