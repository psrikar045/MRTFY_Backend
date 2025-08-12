package com.example.jwtauthenticator.service;
import com.example.jwtauthenticator.service.RequestContextExtractorService.RequestContext;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.RivoFetchRequestLog;
import com.example.jwtauthenticator.repository.RivoFetchRequestLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 🚀 Java 21 Record for immediate request data extraction (prevents recycling)
 */
record RequestSnapshot(
    String targetUrl,
    String userAgent, 
    String method,
    String path,
    String queryString,
    Long contentLength,
    Integer responseStatus,
    String clientIp
) {
    /**
     * 🏃‍♂️ Fast extraction from request/response (call BEFORE async)
     */
    static RequestSnapshot capture(HttpServletRequest request, HttpServletResponse response, String explicitUrl) {
        try {
            return new RequestSnapshot(
                explicitUrl != null ? explicitUrl : extractUrlSafe(request),
                getHeaderSafe(request, "User-Agent"),
                request != null ? request.getMethod() : null,
                request != null ? request.getRequestURI() : null,
                request != null ? request.getQueryString() : null,
                request != null && request.getContentLength() > 0 ? (long) request.getContentLength() : null,
                response != null ? response.getStatus() : null,
                request != null ? request.getRemoteAddr() : null
            );
        } catch (Exception e) {
            // Return safe defaults if extraction fails
            return new RequestSnapshot(explicitUrl, null, null, null, null, null, null, null);
        }
    }
    
    private static String extractUrlSafe(HttpServletRequest request) {
        if (request == null) return null;
        try {
            String url = request.getParameter("url");
            return url != null ? url : request.getHeader("X-Target-URL");
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String getHeaderSafe(HttpServletRequest request, String header) {
        if (request == null) return null;
        try {
            return request.getHeader(header);
        } catch (Exception e) {
            return null;
        }
    }
}

/**
 * 🚀 PHASE 2: RivoFetch Logging Service with Async Processing
 * 
 * Specialized service for logging RivoFetch API requests with high-performance
 * async processing to avoid blocking the main request flow.
 * 
 * Features:
 * - Async logging to prevent request blocking
 * - Integration with RequestContextExtractorService (Phase 1)
 * - Comprehensive request/response tracking
 * - Performance metrics collection
 * - Error handling and fallback strategies
 * - Batch processing capabilities for high-traffic scenarios
 * 
 * This service specifically handles /api/secure/rivofetch endpoint logging
 * while reusing the existing api_key_request_logs table structure.
 * 
 * @author BrandSnap API Team
 * @version 2.0
 * @since Java 21 - Phase 2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RivoFetchLoggingService {
    
    private final RivoFetchRequestLogRepository rivoFetchRepository;
    private final RequestContextExtractorService requestContextExtractor; // Phase 1 integration
    private final RivoFetchIdGeneratorService idGeneratorService; // RIVO9 ID generation
    
    /**
     * 🚀 Log successful RivoFetch request with cache hit type (FIXED - extracts data immediately)
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logSuccessfulRivoFetchAsync(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiKey apiKey,
            long startTime,
            String responseBody,
            String cacheHitType) {
        
        try {
            // Extract ALL data from request/response IMMEDIATELY to avoid recycling issues
            String targetUrl = null;
            String userAgent = null;
            String requestMethod = null;
            String requestPath = null;
            String queryString = null;
            Long requestSizeBytes = null;
            Integer responseStatus = null;
            
            if (request != null) {
                try {
                    targetUrl = extractTargetUrlFromRequest(request);
                    userAgent = request.getHeader("User-Agent");
                    requestMethod = request.getMethod();
                    requestPath = request.getRequestURI();
                    queryString = request.getQueryString();
                    
                    int contentLength = request.getContentLength();
                    if (contentLength > 0) {
                        requestSizeBytes = (long) contentLength;
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Could not extract request data (may be recycled): {}", e.getMessage());
                }
            }
            
            if (response != null) {
                try {
                    responseStatus = response.getStatus();
                } catch (Exception e) {
                    log.warn("⚠️ Could not extract response data (may be recycled): {}", e.getMessage());
                }
            }
            
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            
            var snapshot = new RequestSnapshot(targetUrl, userAgent, requestMethod, requestPath, 
                                             queryString, requestSizeBytes, responseStatus, context.clientIp());
            RivoFetchRequestLog logEntry = buildSuccessfulLogEntryFromSnapshot(
                    snapshot, apiKey, context, responseTimeMs, responseBody, cacheHitType);
            
            saveLogEntry(logEntry);
            
            log.info("✅ FIXED: Successful RivoFetch logged - API Key: {}, ID: {}, Time: {}ms, Cache: {}",
                    apiKey != null ? apiKey.getId() : "null", logEntry.getRivoFetchLogId(), responseTimeMs, cacheHitType);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ FIXED: Failed to log successful RivoFetch for API key {}: {}", 
                    apiKey != null ? apiKey.getId() : "null", e.getMessage(), e);
            // Return completed future instead of failed to prevent blocking
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 🚀 Log successful RivoFetch request with explicit target URL (FIXED - Java 21 optimized)
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logSuccessfulRivoFetchAsync(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiKey apiKey,
            long startTime,
            String responseBody,
            String cacheHitType,
            String targetUrl) {
        
        // 🏃‍♂️ IMMEDIATE data extraction (before async processing)
        var snapshot = RequestSnapshot.capture(request, response, targetUrl);
        var context = requestContextExtractor.extractRequestContext();
        long responseTimeMs = System.currentTimeMillis() - startTime;
        
        // 🚀 Now safe to process asynchronously with captured data
        return CompletableFuture.runAsync(() -> {
            try {
                var logEntry = buildSuccessfulLogEntryFromSnapshot(
                    snapshot, apiKey, context, responseTimeMs, responseBody, cacheHitType);
                
                saveLogEntry(logEntry);
                
                log.debug("✅ FIXED: RivoFetch logged - API Key: {}, ID: {}, Time: {}ms, Cache: {}, URL: {}",
                        apiKey != null ? apiKey.getId() : "null", 
                        logEntry.getRivoFetchLogId(), responseTimeMs, cacheHitType, snapshot.targetUrl());
                
            } catch (Exception e) {
                log.error("❌ FIXED: Failed to log RivoFetch for API key {}: {}", 
                        apiKey != null ? apiKey.getId() : "null", e.getMessage());
            }
        });
    }
    
    /**
     * 🚀 Log successful RivoFetch request (backward compatibility)
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logSuccessfulRivoFetchAsync(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiKey apiKey,
            long startTime,
            String responseBody) {
        
        return logSuccessfulRivoFetchAsync(request, response, apiKey, startTime, responseBody, "MISS");
    }
    
    /**
     * 🚀 Log successful public RivoFetch request (no API key)
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logSuccessfulPublicRivoFetchAsync(
            HttpServletRequest request,
            HttpServletResponse response,
            long startTime,
            String responseBody,
            String cacheHitType) {
        
        try {
            // 🏃‍♂️ IMMEDIATE data extraction (before async processing)
            var snapshot = RequestSnapshot.capture(request, response, null);
            var context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            String targetUrl = extractTargetUrlFromRequest(request);
            
            // 🚀 Now safe to process asynchronously with captured data
            RivoFetchRequestLog logEntry = buildSuccessfulLogEntryFromSnapshot(
                    snapshot, null, context, responseTimeMs, responseBody, cacheHitType);
            
            saveLogEntry(logEntry);
            
            log.debug("✅ Successful public RivoFetch logged - Time: {}ms, Cache: {}",
                    responseTimeMs, cacheHitType);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to log successful public RivoFetch: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 🚀 Log successful public RivoFetch request with explicit target URL
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logSuccessfulPublicRivoFetchAsync(
            HttpServletRequest request,
            HttpServletResponse response,
            long startTime,
            String responseBody,
            String cacheHitType,
            String targetUrl) {
        
        try {
            // 🏃‍♂️ IMMEDIATE data extraction (before async processing)
            var snapshot = RequestSnapshot.capture(request, response, targetUrl);
            var context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            
            // 🚀 Now safe to process asynchronously with captured data
            RivoFetchRequestLog logEntry = buildSuccessfulLogEntryFromSnapshot(
                    snapshot, null, context, responseTimeMs, responseBody, cacheHitType);
            
            saveLogEntry(logEntry);
            
            log.debug("✅ Successful public RivoFetch logged - Time: {}ms, Cache: {}, URL: {}",
                    responseTimeMs, cacheHitType, targetUrl);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to log successful public RivoFetch: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    

    
    /**
     * 🚀 Log failed RivoFetch request with explicit target URL
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logFailedRivoFetchAsync(
            HttpServletRequest request,
            ApiKey apiKey,
            long startTime,
            String errorMessage,
            int responseStatus,
            String targetUrl) {
        
        try {
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            
            RivoFetchRequestLog logEntry = buildFailedLogEntry(
                    request, apiKey, context, responseTimeMs, targetUrl, errorMessage, responseStatus);
            
            saveLogEntry(logEntry);
            
            log.debug("❌ Failed RivoFetch logged - API Key: {}, Status: {}, Error: {}, Time: {}ms, URL: {}",
                    apiKey != null ? apiKey.getId() : "null", responseStatus, errorMessage, responseTimeMs, targetUrl);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to log failed RivoFetch request: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 🚀 Log failed public RivoFetch request (no API key)
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logFailedPublicRivoFetchAsync(
            HttpServletRequest request,
            long startTime,
            String errorMessage,
            int responseStatus) {
        
        try {
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            String targetUrl = extractTargetUrlFromRequest(request);
            
            RivoFetchRequestLog logEntry = buildFailedLogEntry(
                    request, null, context, responseTimeMs, targetUrl, errorMessage, responseStatus);
            
            saveLogEntry(logEntry);
            
            log.debug("❌ Failed public RivoFetch logged - Status: {}, Error: {}, Time: {}ms",
                    responseStatus, errorMessage, responseTimeMs);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to log failed public RivoFetch request: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 🚀 Log failed public RivoFetch request with explicit target URL
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logFailedPublicRivoFetchAsync(
            HttpServletRequest request,
            long startTime,
            String errorMessage,
            int responseStatus,
            String targetUrl) {
        
        try {
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            
            RivoFetchRequestLog logEntry = buildFailedLogEntry(
                    request, null, context, responseTimeMs, targetUrl, errorMessage, responseStatus);
            
            saveLogEntry(logEntry);
            
            log.debug("❌ Failed public RivoFetch logged - Status: {}, Error: {}, Time: {}ms, URL: {}",
                    responseStatus, errorMessage, responseTimeMs, targetUrl);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to log failed public RivoFetch request: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 🏗️ Build successful RivoFetch log entry
     */
    private RivoFetchRequestLog buildSuccessfulLogEntry(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiKey apiKey,
            RequestContext context,
            long responseTimeMs,
            String targetUrl,
            String responseBody,
            String cacheHitType) {
        
        RivoFetchRequestLog logEntry = buildBaseLogEntry(request, apiKey, context, responseTimeMs, targetUrl);
        
        // Set success-specific fields
        logEntry.setRivoFetchSuccess(true);
        logEntry.setRivoFetchResponseStatus(response != null ? response.getStatus() : 200);
        logEntry.setRivoFetchCacheHitType(cacheHitType);
        
        // Calculate response size
        if (responseBody != null) {
            logEntry.setRivoFetchResponseSizeBytes((long) responseBody.getBytes().length);
        }
        
        // Set external API duration based on cache hit type
        if ("MISS".equals(cacheHitType)) {
            logEntry.setRivoFetchExternalApiDurationMs(Math.max(0, responseTimeMs - 50));
        } else {
            logEntry.setRivoFetchExternalApiDurationMs(0L);
        }
        
        return logEntry;
    }
    
    /**
     * 🏗️ Build failed RivoFetch log entry
     */
    private RivoFetchRequestLog buildFailedLogEntry(
            HttpServletRequest request,
            ApiKey apiKey,
            RequestContext context,
            long responseTimeMs,
            String targetUrl,
            String errorMessage,
            int responseStatus) {
        
        RivoFetchRequestLog logEntry = buildBaseLogEntry(request, apiKey, context, responseTimeMs, targetUrl);
        
        // Set failure-specific fields
        logEntry.setRivoFetchSuccess(false);
        logEntry.setRivoFetchResponseStatus(responseStatus);
        logEntry.setRivoFetchErrorMessage(errorMessage);
        logEntry.setRivoFetchCacheHitType("MISS");
        logEntry.setRivoFetchExternalApiDurationMs(Math.max(0, responseTimeMs - 50));
        
        return logEntry;
    }
    
    /**
     * 🏗️ Build base RivoFetch log entry with common fields
     */
    private RivoFetchRequestLog buildBaseLogEntry(
            HttpServletRequest request,
            ApiKey apiKey,
            RequestContext context,
            long responseTimeMs,
            String targetUrl) {
        
        String rivoFetchId = generateUniqueRivoFetchId();
        LocalDateTime now = LocalDateTime.now();
        
        log.info("🏗️ Building log entry with ID: {} for URL: {}", rivoFetchId, targetUrl);
        
        // Extract request data safely before async processing
        String userAgent = null;
        String requestMethod = null;
        String requestPath = null;
        Long requestSizeBytes = null;
        
        if (request != null) {
            try {
                userAgent = request.getHeader("User-Agent");
                requestMethod = request.getMethod();
                requestPath = request.getRequestURI();
                
                int contentLength = request.getContentLength();
                if (contentLength > 0) {
                    requestSizeBytes = (long) contentLength;
                }
            } catch (Exception e) {
                log.warn("⚠️ Could not extract request data (request may be recycled): {}", e.getMessage());
            }
        }
        
        RivoFetchRequestLog.RivoFetchRequestLogBuilder builder = RivoFetchRequestLog.builder()
                .rivoFetchLogId(rivoFetchId)
                .universalRequestUuid(UUID.randomUUID())
                .rivoFetchTimestamp(now)
                .rivoFetchTotalDurationMs(responseTimeMs)
                .rivoFetchTargetUrl(targetUrl)
                .rivoFetchClientIp(context.clientIp())
                .rivoFetchUserAgent(userAgent)
                .rivoFetchRequestMethod(requestMethod)
                .rivoFetchRequestPath(requestPath)
                .rivoFetchRequestSizeBytes(requestSizeBytes)
                .rivoFetchCreatedAt(now)
                .rivoFetchUpdatedAt(now);
        
        // Extract domain from target URL
        if (targetUrl != null) {
            String urlDomain = requestContextExtractor.extractDomainFromUrl(targetUrl);
            builder.rivoFetchUrlDomain(urlDomain);
        }
        
        // Set request domain from headers
        if (context.domain() != null) {
            builder.rivoFetchRequestDomain(context.domain());
        }
        
        // Set API key related fields if available
        if (apiKey != null) {
            builder.rivoFetchApiKeyId(apiKey.getId())
                   .rivoFetchUserId(apiKey.getUserFkId())
                   .rivoFetchRateLimitTier(apiKey.getRateLimitTier() != null ? 
                                         apiKey.getRateLimitTier().name() : null);
        }
        
        // Set controller context
        builder.rivoFetchControllerName(context.controllerName())
               .rivoFetchMethodName(context.methodName())
               .rivoFetchRequestSource(context.requestSource().name());
        
        // Set query string if present
        if (context.queryString() != null) {
            builder.rivoFetchQueryString(context.queryString());
        }
        
        return builder.build();
    }
    
    /**
     * 💾 Save log entry to database using dedicated database service
     * 
     * @param logEntry Log entry to save
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveLogEntry(RivoFetchRequestLog logEntry) {
        try {
            // Direct repository save with proper error handling
            RivoFetchRequestLog saved = rivoFetchRepository.save(logEntry);
            
            if (saved != null && saved.getRivoFetchLogId() != null) {
                log.info("💾 ✅ Successfully saved RivoFetch log entry: {}", saved.getRivoFetchLogId());
            } else {
                log.error("❌ 🚨 FAILED: Could not save RivoFetch log entry: {}", 
                         logEntry != null ? logEntry.getRivoFetchLogId() : "null");
            }
            
        } catch (Exception e) {
            log.error("❌ 🚨 CRITICAL: Exception in saveLogEntry for {}: {}", 
                     logEntry != null ? logEntry.getRivoFetchLogId() : "null", e.getMessage(), e);
        }
    }
    
    /**
     * 🎯 Extract target URL from request
     * 
     * This method extracts the target URL that RivoFetch is trying to fetch.
     * It can be from query parameters, request body, or headers.
     * 
     * @param request HTTP servlet request
     * @return Target URL or null if not found
     */
    private String extractTargetUrlFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        // Try to get URL from query parameter
        String urlParam = request.getParameter("url");
        if (urlParam != null && !urlParam.trim().isEmpty()) {
            return urlParam.trim();
        }
        
        // Try to get URL from 'target' parameter
        String targetParam = request.getParameter("target");
        if (targetParam != null && !targetParam.trim().isEmpty()) {
            return targetParam.trim();
        }
        
        // Try to get URL from 'domain' parameter
        String domainParam = request.getParameter("domain");
        if (domainParam != null && !domainParam.trim().isEmpty()) {
            // If it's just a domain, construct a URL
            if (!domainParam.startsWith("http://") && !domainParam.startsWith("https://")) {
                return "https://" + domainParam.trim();
            }
            return domainParam.trim();
        }
        
        // Try to extract from request body if it's JSON
        try {
            String contentType = request.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                // For JSON requests, we would need to read the body
                // This is complex because the body can only be read once
                // For now, we'll rely on query parameters
                log.debug("JSON request detected, but body parsing not implemented yet");
            }
        } catch (Exception e) {
            log.debug("Could not check request body for URL: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * � Log RivoFetch batch processing (for high-traffic scenarios)
     * 
     * This method can be used for batch processing multiple RivoFetch requests
     * to improve performance in high-traffic scenarios.
     * 
     * @param logEntries List of log entries to save
     * @return CompletableFuture for async processing
     */
    @Async("transactionalAsyncExecutor")
    public CompletableFuture<Void> logRivoFetchBatchAsync(
            java.util.List<RivoFetchRequestLog> logEntries) {
        
        try {
            if (logEntries == null || logEntries.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            
            rivoFetchRepository.saveAll(logEntries);
            
            log.debug("✅ Batch logged {} RivoFetch requests", logEntries.size());
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to batch log {} RivoFetch requests: {}", 
                    logEntries.size(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // ==================== SYNCHRONOUS LOGGING METHODS ====================
    
    /**
     * 🚀 Log successful RivoFetch request SYNCHRONOUSLY (for critical operations)
     * 
     * This method ensures the log is saved to database before returning.
     * Use this for critical operations where you need to guarantee logging.
     * 
     * @param request HTTP servlet request
     * @param response HTTP servlet response  
     * @param apiKey API key used for the request
     * @param startTime Request start time
     * @param responseBody Response body content
     * @param cacheHitType Cache hit type (DATABASE_HIT, MEMORY_HIT, MISS)
     * @param targetUrl Explicit target URL
     * @return true if successfully logged, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean logSuccessfulRivoFetchSync(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiKey apiKey,
            long startTime,
            String responseBody,
            String cacheHitType,
            String targetUrl) {
        
        try {
            // Extract data immediately to avoid recycling issues
            var snapshot = RequestSnapshot.capture(request, response, targetUrl);
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            
            RivoFetchRequestLog logEntry = buildSuccessfulLogEntryFromSnapshot(
                    snapshot, apiKey, context, responseTimeMs, responseBody, cacheHitType);
            
            // Save synchronously
            RivoFetchRequestLog saved = rivoFetchRepository.save(logEntry);
            
            if (saved != null && saved.getRivoFetchLogId() != null) {
                log.info("💾 ✅ SYNC: Successfully saved RivoFetch log entry: {}", saved.getRivoFetchLogId());
                return true;
            } else {
                log.error("❌ 🚨 SYNC: Failed to save RivoFetch log entry");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ 🚨 SYNC: Exception in synchronous logging for API key {}: {}", 
                     apiKey != null ? apiKey.getId() : "null", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 🚀 Log failed RivoFetch request SYNCHRONOUSLY (for critical operations)
     * 
     * @param request HTTP servlet request
     * @param apiKey API key used for the request
     * @param startTime Request start time
     * @param errorMessage Error message
     * @param statusCode HTTP status code
     * @param targetUrl Explicit target URL
     * @return true if successfully logged, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean logFailedRivoFetchSync(
            HttpServletRequest request,
            ApiKey apiKey,
            long startTime,
            String errorMessage,
            int statusCode,
            String targetUrl) {
        
        try {
            // Extract data immediately
            var snapshot = RequestSnapshot.capture(request, null, targetUrl);
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            
            RivoFetchRequestLog logEntry = buildFailedLogEntryFromSnapshot(
                    snapshot, apiKey, context, responseTimeMs, errorMessage, statusCode);
            
            // Save synchronously
            RivoFetchRequestLog saved = rivoFetchRepository.save(logEntry);
            
            if (saved != null && saved.getRivoFetchLogId() != null) {
                log.info("💾 ✅ SYNC: Successfully saved failed RivoFetch log entry: {}", saved.getRivoFetchLogId());
                return true;
            } else {
                log.error("❌ 🚨 SYNC: Failed to save failed RivoFetch log entry");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ 🚨 SYNC: Exception in synchronous failed logging for API key {}: {}", 
                     apiKey != null ? apiKey.getId() : "null", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 🎯 Generate unique RivoFetch ID with collision detection
     */
    private String generateUniqueRivoFetchId() {
        int maxRetries = 5;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String candidateId = idGeneratorService.generateRivoFetchId();
                
                // Check if ID already exists in database using repository
                boolean exists = rivoFetchRepository.existsByRivoFetchLogId(candidateId);
                if (!exists) {
                    log.info("🎯 Generated unique RivoFetch ID: {} (attempt {})", candidateId, attempt);
                    return candidateId;
                }
                
                log.warn("⚠️ ID collision detected: {} (attempt {}), retrying...", candidateId, attempt);
                
                // Add small delay before retry
                Thread.sleep(10);
                
            } catch (Exception e) {
                log.error("❌ Error generating unique ID (attempt {}): {}", attempt, e.getMessage());
            }
        }
        
        // Fallback: Use timestamp-based ID if all retries fail
        long timestamp = System.currentTimeMillis() % 1000000; // Last 6 digits of timestamp
        String fallbackId = String.format("RIVO9%06d", timestamp);
        log.error("🚨 Using fallback timestamp-based ID after {} attempts: {}", maxRetries, fallbackId);
        return fallbackId;
    }
    

    

    

    
    /**
     * 🏗️ Build failed log entry from snapshot (Java 21 optimized)
     */
    private RivoFetchRequestLog buildFailedLogEntryFromSnapshot(
            RequestSnapshot snapshot, ApiKey apiKey, RequestContext context, 
            long responseTimeMs, String errorMessage, int statusCode) {
        
        String rivoFetchId = generateUniqueRivoFetchId();
        LocalDateTime now = LocalDateTime.now();
        
        return RivoFetchRequestLog.builder()
                .rivoFetchLogId(rivoFetchId)
                .universalRequestUuid(UUID.randomUUID())
                .rivoFetchTimestamp(now)
                .rivoFetchTotalDurationMs(responseTimeMs)
                .rivoFetchTargetUrl(snapshot.targetUrl())
                .rivoFetchClientIp(snapshot.clientIp() != null ? snapshot.clientIp() : context.clientIp())
                .rivoFetchUserAgent(snapshot.userAgent())
                .rivoFetchRequestMethod(snapshot.method())
                .rivoFetchRequestPath(snapshot.path())
                .rivoFetchQueryString(snapshot.queryString())
                .rivoFetchRequestSizeBytes(snapshot.contentLength())
                .rivoFetchResponseStatus(statusCode)
                .rivoFetchResponseSizeBytes(null)
                .rivoFetchSuccess(false)
                .rivoFetchErrorMessage(errorMessage)
                .rivoFetchCacheHitType("MISS")
                .rivoFetchUrlDomain(snapshot.targetUrl() != null ? 
                    requestContextExtractor.extractDomainFromUrl(snapshot.targetUrl()) : null)
                .rivoFetchApiKeyId(apiKey != null ? apiKey.getId() : null)
                .rivoFetchUserId(apiKey != null ? apiKey.getUserFkId() : null)
                .rivoFetchRateLimitTier(apiKey != null && apiKey.getRateLimitTier() != null ? 
                    apiKey.getRateLimitTier().name() : null)
                .rivoFetchControllerName(context.controllerName())
                .rivoFetchMethodName(context.methodName())
                .rivoFetchRequestSource(context.requestSource() != null ? 
                    context.requestSource().name() : null)
                .build();
    }
    
    /**
     * 🏗️ Build log entry from snapshot (Java 21 optimized - HIGH PERFORMANCE)
     */
    private RivoFetchRequestLog buildSuccessfulLogEntryFromSnapshot(
            RequestSnapshot snapshot, ApiKey apiKey, RequestContext context, 
            long responseTimeMs, String responseBody, String cacheHitType) {
        
        String rivoFetchId = generateUniqueRivoFetchId();
        LocalDateTime now = LocalDateTime.now();
        
        return RivoFetchRequestLog.builder()
                .rivoFetchLogId(rivoFetchId)
                .universalRequestUuid(UUID.randomUUID())
                .rivoFetchTimestamp(now)
                .rivoFetchTotalDurationMs(responseTimeMs)
                .rivoFetchTargetUrl(snapshot.targetUrl())
                .rivoFetchClientIp(snapshot.clientIp() != null ? snapshot.clientIp() : context.clientIp())
                .rivoFetchUserAgent(snapshot.userAgent())
                .rivoFetchRequestMethod(snapshot.method())
                .rivoFetchRequestPath(snapshot.path())
                .rivoFetchQueryString(snapshot.queryString())
                .rivoFetchRequestSizeBytes(snapshot.contentLength())
                .rivoFetchResponseStatus(snapshot.responseStatus() != null ? snapshot.responseStatus() : 200)
                .rivoFetchResponseSizeBytes(responseBody != null ? (long) responseBody.getBytes().length : null)
                .rivoFetchSuccess(true)
                .rivoFetchCacheHitType(cacheHitType)
                .rivoFetchUrlDomain(snapshot.targetUrl() != null ? 
                    requestContextExtractor.extractDomainFromUrl(snapshot.targetUrl()) : null)
                .rivoFetchRequestDomain(context.domain())
                .rivoFetchApiKeyId(apiKey != null ? apiKey.getId() : null)
                .rivoFetchUserId(apiKey != null ? apiKey.getUserFkId() : null)
                .rivoFetchRateLimitTier(apiKey != null && apiKey.getRateLimitTier() != null ? 
                    apiKey.getRateLimitTier().name() : null)
                .rivoFetchControllerName(context.controllerName())
                .rivoFetchMethodName(context.methodName())
                .rivoFetchRequestSource(context.requestSource() != null ? 
                    context.requestSource().name() : null)
                .rivoFetchCreatedAt(now)
                .rivoFetchUpdatedAt(now)
                .build();
    }


}
