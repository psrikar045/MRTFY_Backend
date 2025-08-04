package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.ApiKeyRequestLog;
import com.example.jwtauthenticator.repository.ApiKeyRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for logging API key requests.
 * Provides comprehensive request logging and audit trail functionality.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestLoggingService {

    private final ApiKeyRequestLogRepository logRepository;

    /**
     * Log an API key request asynchronously to avoid blocking the request.
     */
    @Async
    @Transactional
    public void logApiKeyRequest(String apiKeyId, String userFkId, HttpServletRequest request, 
                                HttpServletResponse response, boolean success) {
        try {
            ApiKeyRequestLog requestLog = ApiKeyRequestLog.builder()
                .apiKeyId(UUID.fromString(apiKeyId))
                .userFkId(userFkId)
                .clientIp(getClientIp(request))
                .domain(getDomain(request))
                .userAgent(request.getHeader("User-Agent"))
                .requestMethod(request.getMethod())
                .requestPath(request.getRequestURI())
                .queryString(request.getQueryString())
                .requestTimestamp(LocalDateTime.now())
                .responseStatus(response.getStatus())
                .success(success)
                .build();

            logRepository.save(requestLog);
            
            log.debug("Logged API key request: {} {} from {} for API key: {}", 
                     request.getMethod(), request.getRequestURI(), 
                     getClientIp(request), apiKeyId);
                     
        } catch (Exception e) {
            log.error("Error logging API key request for key: {}, error: {}", apiKeyId, e.getMessage(), e);
        }
    }

    /**
     * Log an API key request with additional details.
     */
    @Async
    @Transactional
    public void logApiKeyRequest(String apiKeyId, String userFkId, String method, String path,
                                String clientIp, String domain, String userAgent, 
                                int responseStatus, boolean success) {
        try {
            ApiKeyRequestLog requestLog = ApiKeyRequestLog.builder()
                .apiKeyId(UUID.fromString(apiKeyId))
                .userFkId(userFkId)
                .clientIp(clientIp)
                .domain(domain)
                .userAgent(userAgent)
                .requestMethod(method)
                .requestPath(path)
                .requestTimestamp(LocalDateTime.now())
                .responseStatus(responseStatus)
                .success(success)
                .build();

            logRepository.save(requestLog);
            
        } catch (Exception e) {
            log.error("Error logging API key request for key: {}, error: {}", apiKeyId, e.getMessage(), e);
        }
    }

    /**
     * Get request logs for an API key within a date range.
     */
    public Page<ApiKeyRequestLog> getRequestLogsForApiKey(UUID apiKeyId, LocalDateTime from, 
                                                         LocalDateTime to, Pageable pageable) {
        return logRepository.findByApiKeyIdAndRequestTimestampBetweenOrderByRequestTimestampDesc(
            apiKeyId, from, to, pageable);
    }

    /**
     * Get recent request logs for an API key (last N requests).
     */
    public List<ApiKeyRequestLog> getRecentRequestLogs(UUID apiKeyId, int limit) {
        return logRepository.findTopNByApiKeyIdOrderByRequestTimestampDesc(apiKeyId, limit);
    }

    /**
     * Get request logs for a user across all their API keys.
     */
    public Page<ApiKeyRequestLog> getRequestLogsForUser(String userFkId, LocalDateTime from, 
                                                       LocalDateTime to, Pageable pageable) {
        return logRepository.findByUserFkIdAndRequestTimestampBetweenOrderByRequestTimestampDesc(
            userFkId, from, to, pageable);
    }

    /**
     * Get failed request logs for an API key.
     */
    public List<ApiKeyRequestLog> getFailedRequestLogs(UUID apiKeyId, LocalDateTime from, LocalDateTime to) {
        return logRepository.findByApiKeyIdAndSuccessAndRequestTimestampBetween(
            apiKeyId, false, from, to);
    }

    /**
     * Get request logs by client IP.
     */
    public List<ApiKeyRequestLog> getRequestLogsByClientIp(String clientIp, LocalDateTime from, LocalDateTime to) {
        return logRepository.findByClientIpAndRequestTimestampBetween(clientIp, from, to);
    }

    /**
     * Get request logs by domain.
     */
    public List<ApiKeyRequestLog> getRequestLogsByDomain(String domain, LocalDateTime from, LocalDateTime to) {
        return logRepository.findByDomainAndRequestTimestampBetween(domain, from, to);
    }

    /**
     * Get request count for an API key in the last 24 hours.
     */
    public long getRequestCountLast24Hours(UUID apiKeyId) {
        LocalDateTime yesterday = LocalDateTime.now().minusHours(24);
        return logRepository.countByApiKeyIdAndRequestTimestampAfter(apiKeyId, yesterday);
    }

    /**
     * Get successful request count for an API key today.
     */
    public long getSuccessfulRequestCountToday(UUID apiKeyId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        return logRepository.countByApiKeyIdAndSuccessAndRequestTimestampAfter(
            apiKeyId, true, startOfDay);
    }

    /**
     * Get failed request count for an API key today.
     */
    public long getFailedRequestCountToday(UUID apiKeyId) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        return logRepository.countByApiKeyIdAndSuccessAndRequestTimestampAfter(
            apiKeyId, false, startOfDay);
    }

    /**
     * Get most active client IPs for an API key.
     */
    public List<Object[]> getMostActiveClientIps(UUID apiKeyId, LocalDateTime from, LocalDateTime to, int limit) {
        return logRepository.findMostActiveClientIps(apiKeyId, from, to, 
            org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * Get most accessed endpoints for an API key.
     */
    public List<Object[]> getMostAccessedEndpoints(UUID apiKeyId, LocalDateTime from, LocalDateTime to, int limit) {
        return logRepository.findMostAccessedEndpoints(apiKeyId, from, to, 
            org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * Clean up old request logs (older than specified days).
     */
    @Transactional
    public int cleanupOldLogs(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        int deletedCount = logRepository.deleteLogsOlderThan(cutoff);
        
        if (deletedCount > 0) {
            log.info("Cleaned up {} old request log records", deletedCount);
        }
        
        return deletedCount;
    }

    /**
     * Get request log summary for an API key.
     */
    public RequestLogSummary getRequestLogSummary(UUID apiKeyId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime yesterday = now.minusHours(24);
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);

        return RequestLogSummary.builder()
            .apiKeyId(apiKeyId)
            .requestsToday(logRepository.countByApiKeyIdAndRequestTimestampAfter(apiKeyId, startOfDay))
            .requestsLast24Hours(logRepository.countByApiKeyIdAndRequestTimestampAfter(apiKeyId, yesterday))
            .requestsLast7Days(logRepository.countByApiKeyIdAndRequestTimestampAfter(apiKeyId, weekAgo))
            .requestsLast30Days(logRepository.countByApiKeyIdAndRequestTimestampAfter(apiKeyId, monthAgo))
            .successfulRequestsToday(logRepository.countByApiKeyIdAndSuccessAndRequestTimestampAfter(
                apiKeyId, true, startOfDay))
            .failedRequestsToday(logRepository.countByApiKeyIdAndSuccessAndRequestTimestampAfter(
                apiKeyId, false, startOfDay))
            .lastRequestAt(logRepository.findLastRequestTimestamp(apiKeyId))
            .mostActiveClientIp(logRepository.findMostActiveClientIp(apiKeyId, startOfDay))
            .build();
    }

    /**
     * Extract client IP address from request, handling proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        // Check for X-Forwarded-For header (proxy/load balancer)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // Take the first IP in the chain
            return xForwardedFor.split(",")[0].trim();
        }

        // Check for X-Real-IP header (Nginx)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        // Check for CF-Connecting-IP header (Cloudflare)
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty() && !"unknown".equalsIgnoreCase(cfConnectingIp)) {
            return cfConnectingIp;
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }

    /**
     * Extract domain from request headers.
     */
    private String getDomain(HttpServletRequest request) {
        // Check Origin header first
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isEmpty()) {
            try {
                return new URL(origin).getHost();
            } catch (Exception e) {
                return origin; // Return as-is if parsing fails
            }
        }

        // Check Referer header
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            try {
                return new URL(referer).getHost();
            } catch (Exception e) {
                return referer; // Return as-is if parsing fails
            }
        }

        // Check Host header
        String host = request.getHeader("Host");
        if (host != null && !host.isEmpty()) {
            // Remove port if present
            return host.split(":")[0];
        }

        return null;
    }

    /**
     * Request log summary DTO.
     */
    @lombok.Data
    @lombok.Builder
    public static class RequestLogSummary {
        private UUID apiKeyId;
        private long requestsToday;
        private long requestsLast24Hours;
        private long requestsLast7Days;
        private long requestsLast30Days;
        private long successfulRequestsToday;
        private long failedRequestsToday;
        private LocalDateTime lastRequestAt;
        private String mostActiveClientIp;
        
        public double getSuccessRateToday() {
            if (requestsToday == 0) return 0.0;
            return (double) successfulRequestsToday / requestsToday * 100.0;
        }
        
        public double getFailureRateToday() {
            if (requestsToday == 0) return 0.0;
            return (double) failedRequestsToday / requestsToday * 100.0;
        }
    }
}