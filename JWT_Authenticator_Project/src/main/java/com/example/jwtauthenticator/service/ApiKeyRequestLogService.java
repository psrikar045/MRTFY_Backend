package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.ApiKeyRequestLog;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import com.example.jwtauthenticator.service.RequestContextExtractorService.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for logging API key requests and validating IP/Domain restrictions.
 * Provides comprehensive analytics and security monitoring capabilities.
 * 
 * PHASE 1 INTEGRATION: Now uses RequestContextExtractorService for unified context extraction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyRequestLogService {

    private final ApiKeyRequestLogRepository requestLogRepository;
    private final RequestContextExtractorService requestContextExtractor;

    @Value("${app.security.ip-validation.enabled:false}")
    private boolean ipValidationEnabled;

    @Value("${app.security.domain-validation.enabled:false}")
    private boolean domainValidationEnabled;

    @Value("${app.analytics.request-logging.enabled:true}")
    private boolean requestLoggingEnabled;

    @Value("${app.analytics.async-logging:true}")
    private boolean asyncLogging;

    /**
     * Log an API request asynchronously for analytics
     */
    @Async
    public CompletableFuture<Void> logRequestAsync(HttpServletRequest request, ApiKey apiKey,
                                                  Integer responseStatus, Long responseTimeMs) {
        log.info("logRequestAsync called - Logging enabled: {}, API Key ID: {}", requestLoggingEnabled, apiKey.getId());

        if (!requestLoggingEnabled) {
            log.warn("Request logging is disabled, skipping log entry");
            return CompletableFuture.completedFuture(null);
        }

        try {
            // PHASE 1 INTEGRATION: Use RequestContextExtractorService for unified context extraction
            // Extract all needed data from request before async processing to avoid recycled request issue
            
            // Debug: Log all headers for troubleshooting
            logRequestHeaders(request);
            
            // Use new unified context extractor instead of manual extraction
            RequestContext requestContext = requestContextExtractor.extractRequestContext();
            
            String clientIp = requestContext.clientIp();
            String domain = requestContext.domain();
            String userAgent = requestContext.userAgent();
            String requestMethod = request.getMethod();
            String requestPath = request.getRequestURI();
            
            log.info("Calling logRequestWithExtractedData for API Key: {}", apiKey.getId());
            logRequestWithExtractedData(apiKey, responseStatus, responseTimeMs, null, 
                                      clientIp, domain, userAgent, requestMethod, requestPath);
            log.info("Successfully logged request for API Key: {}", apiKey.getId());
        } catch (Exception e) {
            log.error("Failed to log API request asynchronously for API Key: {}", apiKey.getId(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Log an API request synchronously
     */
    @Transactional
    public void logRequest(HttpServletRequest request, ApiKey apiKey,
                          Integer responseStatus, Long responseTimeMs, String errorMessage) {
        log.info("logRequest called - Logging enabled: {}, API Key ID: {}", requestLoggingEnabled, apiKey.getId()); 

        if (!requestLoggingEnabled) {
            log.warn("Request logging is disabled, skipping log entry");
            return;
        }

        try {
            // PHASE 1 INTEGRATION: Use RequestContextExtractorService for unified context extraction
            // Debug: Log all headers for troubleshooting
            logRequestHeaders(request);
            
            // Use new unified context extractor instead of manual extraction
            RequestContext requestContext = requestContextExtractor.extractRequestContext();
            
            String clientIp = requestContext.clientIp();
            String domain = requestContext.domain();
            String userAgent = requestContext.userAgent();
            String requestMethod = request.getMethod();
            String requestPath = request.getRequestURI();

            logRequestWithExtractedData(apiKey, responseStatus, responseTimeMs, errorMessage,
                                      clientIp, domain, userAgent, requestMethod, requestPath);

        } catch (Exception e) {
            log.error("Failed to log API request for key: {}", apiKey.getId(), e);
            throw e; // Re-throw to see the full stack trace
        }
    }

    /**
     * Log an API request with pre-extracted data (safe for async processing)
     */
    @Transactional
    public void logRequestWithExtractedData(ApiKey apiKey, Integer responseStatus, Long responseTimeMs, 
                                          String errorMessage, String clientIp, String domain, 
                                          String userAgent, String requestMethod, String requestPath) {
        log.info("logRequestWithExtractedData called - API Key ID: {}", apiKey.getId());

        try {
            log.info("Extracted - IP: {}, Domain: {}", clientIp, domain);

            // Validate IP and domain restrictions
            boolean isAllowedIp = validateClientIp(apiKey, clientIp);
            boolean isAllowedDomain = validateDomain(apiKey, domain);

            // Determine if the request was successful
            boolean isSuccessful = (errorMessage == null || errorMessage.trim().isEmpty()) && 
                                 (responseStatus != null && responseStatus >= 200 && responseStatus < 300);

            ApiKeyRequestLog logEntry = ApiKeyRequestLog.builder()
                    .apiKeyId(apiKey.getId())
                    .userFkId(apiKey.getUserFkId())
                    .clientIp(clientIp)
                    .domain(domain)
                    .userAgent(userAgent)
                    .requestMethod(requestMethod)
                    .requestPath(requestPath)
                    .requestTimestamp(LocalDateTime.now())
                    .responseStatus(responseStatus)
                    .responseTimeMs(responseTimeMs)
                    .rateLimitTier(apiKey.getRateLimitTier() != null ? apiKey.getRateLimitTier().name() : "FREE_TIER")
                    .isAllowedIp(isAllowedIp)
                    .isAllowedDomain(isAllowedDomain)
                    .errorMessage(errorMessage)
                    .success(isSuccessful)
                    .build();

            log.info("Built log entry: {}", logEntry);

            // Add geographic information (placeholder for future GeoIP integration)
            enrichWithGeographicInfo(logEntry, clientIp);

            log.info("About to save log entry to database...");
            ApiKeyRequestLog savedEntry = requestLogRepository.save(logEntry);
            log.info("Successfully saved log entry with ID: {}", savedEntry.getId());

            // Log security violations
            if (!isAllowedIp || !isAllowedDomain) {
                log.warn("Security violation detected - API Key: {}, IP: {}, Domain: {}, Allowed IP: {}, Allowed Domain: {}",
                        apiKey.getId(), clientIp, domain, isAllowedIp, isAllowedDomain);
            }

        } catch (Exception e) {
            log.error("Failed to log API request for key: {}", apiKey.getId(), e);
            throw e; // Re-throw to see the full stack trace
        }
    }

    /**
     * Validate if client IP is allowed for the API key
     */
    public boolean validateClientIp(ApiKey apiKey, String clientIp) {
        if (!ipValidationEnabled || clientIp == null || clientIp.trim().isEmpty()) {
            return true; // Allow if validation is disabled or IP is not available
        }

        List<String> allowedIps = apiKey.getAllowedIpsAsList();
        if (allowedIps.isEmpty()) {
            return true; // Allow if no IP restrictions are set
        }

        // Check exact match and CIDR ranges
        for (String allowedIp : allowedIps) {
            if (isIpAllowed(clientIp, allowedIp.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate if domain is allowed for the API key
     */
    public boolean validateDomain(ApiKey apiKey, String domain) {
        if (!domainValidationEnabled || domain == null || domain.trim().isEmpty()) {
            return true; // Allow if validation is disabled or domain is not available
        }

        List<String> allowedDomains = apiKey.getAllowedDomainsAsList();
        if (allowedDomains.isEmpty()) {
            return true; // Allow if no domain restrictions are set
        }

        // Check exact match and wildcard domains
        for (String allowedDomain : allowedDomains) {
            if (isDomainAllowed(domain, allowedDomain.trim())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract client IP from request, considering proxy headers
     * 
     * @deprecated Use RequestContextExtractorService.extractClientIp() instead
     * This method now delegates to the unified RequestContextExtractorService for consistency
     */
    @Deprecated
    public String extractClientIp(HttpServletRequest request) {
        log.debug("🔄 PHASE 1 MIGRATION: Delegating extractClientIp to RequestContextExtractorService");
        
        // PHASE 1 INTEGRATION: Delegate to new unified service instead of duplicating logic
        return requestContextExtractor.extractClientIp(request);
    }

    /**
     * Extract domain from request
     * 
     * @deprecated Use RequestContextExtractorService.extractDomain() instead
     * This method now delegates to the unified RequestContextExtractorService for consistency
     */
    @Deprecated
    public String extractDomain(HttpServletRequest request) {
        log.debug("🔄 PHASE 1 MIGRATION: Delegating extractDomain to RequestContextExtractorService");
        
        // PHASE 1 INTEGRATION: Delegate to new unified service instead of duplicating logic
        return requestContextExtractor.extractDomain(request);
    }

    /**
     * Check if IP is allowed (supports CIDR notation)
     */
    private boolean isIpAllowed(String clientIp, String allowedIp) {
        if (allowedIp.equals("*") || allowedIp.equals("0.0.0.0/0")) {
            return true; // Allow all
        }

        if (allowedIp.equals(clientIp)) {
            return true; // Exact match
        }

        // TODO: Implement CIDR range checking for production use
        // For now, only exact matches are supported
        return false;
    }

    /**
     * Check if domain is allowed (supports wildcards)
     */
    private boolean isDomainAllowed(String domain, String allowedDomain) {
        if (allowedDomain.equals("*")) {
            return true; // Allow all
        }

        if (allowedDomain.equals(domain)) {
            return true; // Exact match
        }

        // Wildcard subdomain matching (e.g., *.example.com)
        if (allowedDomain.startsWith("*.")) {
            String baseDomain = allowedDomain.substring(2);
            return domain.endsWith("." + baseDomain) || domain.equals(baseDomain);
        }

        return false;
    }
    /**
     * Enrich log entry with geographic information (placeholder)
     */
    private void enrichWithGeographicInfo(ApiKeyRequestLog logEntry, String clientIp) {
        // TODO: Integrate with GeoIP service (MaxMind, IPStack, etc.)
        // For now, set placeholder values
        if (clientIp != null && !clientIp.startsWith("127.") && !clientIp.startsWith("192.168.")) {
            logEntry.setCountryCode("US"); // Placeholder
            logEntry.setRegion("Unknown");
            logEntry.setCity("Unknown");
        }
    }

    /**
     * Get request logs for an API key with pagination
     */
    @Transactional(readOnly = true)
    public Page<ApiKeyRequestLog> getRequestLogs(UUID apiKeyId, Pageable pageable) {
        return requestLogRepository.findByApiKeyIdOrderByRequestTimestampDesc(apiKeyId, pageable);
    }

    /**
     * Get security violations for an API key
     */
    @Transactional(readOnly = true)
    public List<ApiKeyRequestLog> getSecurityViolations(UUID apiKeyId) {
        return requestLogRepository.findSecurityViolationsByApiKey(apiKeyId);
    }

    /**
     * Get request statistics for an API key
     */
    @Transactional(readOnly = true)
    public long getRequestCount(UUID apiKeyId, LocalDateTime since) {
        return requestLogRepository.countRequestsByApiKeyAndTimeRange(
                apiKeyId, since, LocalDateTime.now());
    }

    /**
     * Get top client IPs for an API key
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTopClientIps(UUID apiKeyId, int limit) {
        return requestLogRepository.findTopClientIpsByApiKey(
                apiKeyId, PageRequest.of(0, limit));
    }

    /**
     * Get top domains for an API key
     */
    @Transactional(readOnly = true)
    public List<Object[]> getTopDomains(UUID apiKeyId, int limit) {
        return requestLogRepository.findTopDomainsByApiKey(
                apiKeyId, PageRequest.of(0, limit));
    }

    /**
     * Clean up old logs (should be called periodically)
     */
    @Transactional
    public void cleanupOldLogs(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        requestLogRepository.deleteByRequestTimestampBefore(cutoffDate);
        log.info("Cleaned up API request logs older than {} days", daysToKeep);
    }

    /**
     * Debug method to log all request headers
     */
    private void logRequestHeaders(HttpServletRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("=== Request Headers Debug ===");
            System.out.println( request.getRequestURL());
            System.out.println(request.getRequestURI());
            System.out.println( request.getRemoteAddr());
            System.out.println(request.getRemoteHost());
            System.out.println(request.getRemotePort());
            System.out.println( request.getLocalAddr());
            System.out.println(request.getLocalName());
            System.out.println(request.getLocalPort());
            log.debug("Request URL: {}", request.getRequestURL());
            log.debug("Request URI: {}", request.getRequestURI());
            log.debug("Remote Address: {}", request.getRemoteAddr());
            log.debug("Remote Host: {}", request.getRemoteHost());
            log.debug("Remote Port: {}", request.getRemotePort());
            log.debug("Local Address: {}", request.getLocalAddr());
            log.debug("Local Name: {}", request.getLocalName());
            log.debug("Local Port: {}", request.getLocalPort());
            
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                log.debug("Header {}: {}", headerName, headerValue);
            }
            log.debug("=== End Request Headers Debug ===");
        }
    }
}