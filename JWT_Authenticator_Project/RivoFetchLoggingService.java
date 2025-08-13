package com.example.jwtauthenticator.service;
import com.example.jwtauthenticator.service.RequestContextExtractorService.RequestContext;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.RivoFetchRequestLog;
import com.example.jwtauthenticator.repository.RivoFetchRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
 * - Cache hit type tracking (DATABASE_HIT, MEMORY_HIT, MISS)
 * 
 * This service specifically handles RivoFetch endpoint logging
 * using the dedicated rivo_fetch_request_logs table.
 * 
 * @author BrandSnap API Team
 * @version 3.0
 * @since Java 21 - Phase 3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RivoFetchLoggingService {
    
    private final RivoFetchRequestLogRepository rivoFetchRepository;
    private final RequestContextExtractorService requestContextExtractor;
    private final RivoFetchIdGeneratorService idGeneratorService;
    
    /**
     * 🚀 Log successful RivoFetch request with cache hit type
     */
    @Async("rivoFetchTaskExecutor")
    public CompletableFuture<Void> logSuccessfulRivoFetchAsync(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiKey apiKey,
            long startTime,
            String responseBody,
            String cacheHitType) {
        
        try {
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            String targetUrl = extractTargetUrlFromRequest(request);
            
            RivoFetchRequestLog logEntry = buildSuccessfulLogEntry(
                    request, response, apiKey, context, responseTimeMs, targetUrl, responseBody, cacheHitType);
            
            saveLogEntry(logEntry);
            
            log.debug("✅ Successful RivoFetch logged - API Key: {}, ID: {}, Time: {}ms, Cache: {}",
                    apiKey.getId(), logEntry.getRivoFetchLogId(), responseTimeMs, cacheHitType);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to log successful RivoFetch for API key {}: {}", 
                    apiKey.getId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 🚀 Log successful RivoFetch request (backward compatibility)
     */
    @Async("rivoFetchTaskExecutor")
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
    @Async("rivoFetchTaskExecutor")
    public CompletableFuture<Void> logSuccessfulPublicRivoFetchAsync(
            HttpServletRequest request,
            HttpServletResponse response,
            long startTime,
            String responseBody,
            String cacheHitType) {
        
        try {
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            String targetUrl = extractTargetUrlFromRequest(request);
            
            RivoFetchRequestLog logEntry = buildSuccessfulLogEntry(
                    request, response, null, context, responseTimeMs, targetUrl, responseBody, cacheHitType);
            
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
     * 🚀 Log failed RivoFetch request
     */
    @Async("rivoFetchTaskExecutor")
    public CompletableFuture<Void> logFailedRivoFetchAsync(
            HttpServletRequest request,
            ApiKey apiKey,
            long startTime,
            String errorMessage,
            int responseStatus) {
        
        try {
            RequestContext context = requestContextExtractor.extractRequestContext();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            String targetUrl = extractTargetUrlFromRequest(request);
            
            RivoFetchRequestLog logEntry = buildFailedLogEntry(
                    request, apiKey, context, responseTimeMs, targetUrl, errorMessage, responseStatus);
            
            saveLogEntry(logEntry);
            
            log.debug("❌ Failed RivoFetch logged - API Key: {}, Status: {}, Error: {}, Time: {}ms",
                    apiKey != null ? apiKey.getId() : "null", responseStatus, errorMessage, responseTimeMs);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("❌ Failed to log failed RivoFetch request: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 🚀 Log failed public RivoFetch request (no API key)
     */
    @Async("rivoFetchTaskExecutor")
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
        
        String rivoFetchId = idGeneratorService.generateRivoFetchId();
        LocalDateTime now = LocalDateTime.now();
        
        RivoFetchRequestLog.RivoFetchRequestLogBuilder builder = RivoFetchRequestLog.builder()
                .rivoFetchLogId(rivoFetchId)
                .universalRequestUuid(UUID.randomUUID())
                .rivoFetchTimestamp(now)
                .rivoFetchTotalDurationMs(responseTimeMs)
                .rivoFetchTargetUrl(targetUrl)
                .rivoFetchClientIp(context.clientIp())
                .rivoFetchUserAgent(request != null ? request.getHeader("User-Agent") : null)
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
        
        // Set request method and path
        if (request != null) {
            builder.rivoFetchRequestMethod(request.getMethod())
                   .rivoFetchRequestPath(request.getRequestURI());
        }
        
        // Set query string if present
        if (request != null && request.getQueryString() != null) {
            builder.rivoFetchQueryString(request.getQueryString());
        }
        
        // Calculate request size
        if (request != null) {
            try {
                int contentLength = request.getContentLength();
                if (contentLength > 0) {
                    builder.rivoFetchRequestSizeBytes((long) contentLength);
                }
            } catch (Exception e) {
                log.debug("Could not determine request size: {}", e.getMessage());
            }
        }
        
        return builder.build();
    }
    
    /**
     * 💾 Save log entry to database with error handling
     */
    @Transactional
    private void saveLogEntry(RivoFetchRequestLog logEntry) {
        try {
            rivoFetchRepository.save(logEntry);
            log.trace("💾 RivoFetch log entry saved: {}", logEntry.getRivoFetchLogId());
        } catch (Exception e) {
            log.error("❌ Failed to save RivoFetch log entry {}: {}", 
                    logEntry.getRivoFetchLogId(), e.getMessage(), e);
            // Don't rethrow - logging failures shouldn't break the main flow
        }
    }
    
    /**
     * 🔍 Extract target URL from request parameters or body
     */
    private String extractTargetUrlFromRequest(HttpServletRequest request) {
        if (request == null) return null;
        
        // Try to get URL from query parameter first
        String url = request.getParameter("url");
        if (url != null && !url.trim().isEmpty()) {
            return url.trim();
        }
        
        // For POST requests, the URL might be in the request body
        // This is a simplified extraction - in real scenarios you might need
        // to parse JSON body to get the URL
        return null;
    }
}