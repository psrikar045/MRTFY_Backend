package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.ApiKeyRequestLog;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyRequestLogService {

    private final ApiKeyRequestLogRepository requestLogRepository;

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
            log.info("Calling logRequest synchronously for API Key: {}", apiKey.getId());
            logRequest(request, apiKey, responseStatus, responseTimeMs, null);
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
            String clientIp = extractClientIp(request);
            String domain = extractDomain(request);
            
            log.info("Extracted - IP: {}, Domain: {}", clientIp, domain);
            
            // Validate IP and domain restrictions
            boolean isAllowedIp = validateClientIp(apiKey, clientIp);
            boolean isAllowedDomain = validateDomain(apiKey, domain);

            ApiKeyRequestLog logEntry = ApiKeyRequestLog.builder()
                    .apiKeyId(apiKey.getId())
                    .userFkId(apiKey.getUserFkId())
                    .clientIp(clientIp)
                    .domain(domain)
                    .userAgent(request.getHeader("User-Agent"))
                    .requestMethod(request.getMethod())
                    .requestPath(request.getRequestURI())
                    .requestTimestamp(LocalDateTime.now())
                    .responseStatus(responseStatus)
                    .responseTimeMs(responseTimeMs)
                    .rateLimitTier(apiKey.getRateLimitTier())
                    .isAllowedIp(isAllowedIp)
                    .isAllowedDomain(isAllowedDomain)
                    .errorMessage(errorMessage)
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
     */
    public String extractClientIp(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Client-IP",
            "CF-Connecting-IP", // Cloudflare
            "True-Client-IP",   // Akamai
            "X-Cluster-Client-IP"
        };

        for (String headerName : headerNames) {
            String ip = request.getHeader(headerName);
            if (ip != null && !ip.trim().isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Handle comma-separated IPs (X-Forwarded-For can contain multiple IPs)
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIpAddress(ip)) {
                    return ip;
                }
            }
        }

        // Fallback to remote address
        String remoteAddr = request.getRemoteAddr();
        return isValidIpAddress(remoteAddr) ? remoteAddr : null;
    }

    /**
     * Extract domain from request
     */
    public String extractDomain(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.trim().isEmpty()) {
            return null;
        }

        // Remove port if present
        if (host.contains(":")) {
            host = host.split(":")[0];
        }

        return host.toLowerCase().trim();
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
     * Basic IP address validation
     */
    private boolean isValidIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }

        // Basic IPv4 validation (simplified)
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
}