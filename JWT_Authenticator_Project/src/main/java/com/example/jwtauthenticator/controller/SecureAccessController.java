package com.example.jwtauthenticator.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.jwtauthenticator.dto.BrandExtractionResponse;
import com.example.jwtauthenticator.dto.ForwardRequest;
import com.example.jwtauthenticator.security.ApiKeyDomainGuard;
import com.example.jwtauthenticator.service.ApiKeyAuthenticationService;
import com.example.jwtauthenticator.service.ApiKeyRequestLogService;
import com.example.jwtauthenticator.service.EnhancedApiKeyValidationService;
import com.example.jwtauthenticator.service.ForwardService;
import com.example.jwtauthenticator.service.MonthlyUsageTrackingService;
import com.example.jwtauthenticator.service.ProfessionalRateLimitService;
import com.example.jwtauthenticator.service.UsageStatsService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Secure Access Controller - Public-facing API endpoints with API key + domain validation.
 * 
 * This controller provides secure access to internal services using API key authentication
 * with domain-based access control. Supports all domain types (.com, .org, .io, .in, .co, etc.)
 * 
 * Features:
 * - API key authentication with domain validation
 * - Professional rate limiting
 * - Request forwarding to internal services
 * - Comprehensive error handling
 * - Security violation logging
 * 
 * Usage:
 * - Use x-api-key header for authentication
 * - Ensure Origin/Referer header matches registered domain
 * - Supports domains like: xamply.com, xamplyfy.co, xamplyfy.in, etc.
 */
@RestController
@RequestMapping("/api/secure")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Secure API Access", 
     description = "Public API endpoints with API key authentication and domain-based access control")
public class SecureAccessController {

    private final ApiKeyDomainGuard apiKeyDomainGuard;
    private final ForwardService forwardService;
    private final ProfessionalRateLimitService professionalRateLimitService;
    private final ObjectMapper objectMapper;
    private final EnhancedApiKeyValidationService enhancedValidationService;
    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    
    // MISSING SERVICES: Add usage tracking and logging services
    private final ApiKeyRequestLogService apiKeyRequestLogService;
    private final UsageStatsService usageStatsService;
    private final MonthlyUsageTrackingService monthlyUsageService;

    @PostMapping("/rivofetch")
    @Operation(
        summary = "Secure RivoFetch Request", 
        description = "Forward authenticated request to external API with API key + domain validation. " +
                     "Supports all domain types including .com, .org, .io, .in, .co, etc.",
        parameters = {
            @Parameter(
                name = "x-api-key", 
                description = "API Key for authentication", 
                required = true, 
                in = ParameterIn.HEADER,
                example = "sk-1234567890abcdef...",
                schema = @Schema(type = "string")
            ),
            @Parameter(
                name = "Origin", 
                description = "Origin header must match registered domain (xamply.com, xamplyfy.co, xamplyfy.in, etc.)", 
                required = false, 
                in = ParameterIn.HEADER,
                example = "https://xamply.com",
                schema = @Schema(type = "string")
            ),
            @Parameter(
                name = "Referer", 
                description = "Referer header (alternative to Origin)", 
                required = false, 
                in = ParameterIn.HEADER,
                example = "https://xamplyfy.co/dashboard",
                schema = @Schema(type = "string")
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Request forwarded successfully",
            content = @Content(schema = @Schema(implementation = BrandExtractionResponse.class))
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Bad Request - Invalid URL or missing required parameters",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(
            responseCode = "401", 
            description = "Unauthorized - Invalid or missing API key",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(
            responseCode = "403", 
            description = "Forbidden - Domain validation failed or API key restrictions",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(
            responseCode = "429", 
            description = "Too Many Requests - Rate limit exceeded",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(
            responseCode = "500", 
            description = "Internal Server Error",
            content = @Content(schema = @Schema(implementation = Map.class))
        ),
        @ApiResponse(
            responseCode = "504", 
            description = "Gateway Timeout - External API timed out",
            content = @Content(schema = @Schema(implementation = Map.class))
        )
    })
    public ResponseEntity<?> secureRivoFetch(
            @Parameter(description = "Forward request details", required = true)
            @Valid @RequestBody ForwardRequest request,
            HttpServletRequest httpRequest) {
        
        long startTime = System.currentTimeMillis();
        String requestPath = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        
        log.info("Secure RivoFetch request: {} {} - URL: {}", method, requestPath, request.url());

        try {
            // Step 1: Extract API key from request
            String apiKeyValue = apiKeyDomainGuard.extractApiKeyFromRequest(httpRequest);
            
            if (apiKeyValue == null || apiKeyValue.trim().isEmpty()) {
                return buildErrorResponse(
                    "API key is required. Use 'x-api-key' header.",
                    HttpStatus.UNAUTHORIZED,
                    "MISSING_API_KEY",
                    null,
                    null
                );
            }

            // Step 2: Validate API key and domain
            ApiKeyDomainGuard.SecurityValidationResult validationResult = 
                apiKeyDomainGuard.validateApiKeyAccess(apiKeyValue, httpRequest);

            if (!validationResult.isSuccess()) {
                HttpStatus status = determineErrorStatus(validationResult.getErrorCode());
                return buildErrorResponse(
                    validationResult.getErrorMessage(),
                    status,
                    validationResult.getErrorCode(),
                    validationResult.getRequestDomain(),
                    validationResult.getMatchedDomain()
                );
            }

            // Step 3: Apply professional rate limiting
            String apiKeyId = validationResult.getApiKey().getId().toString();
            ProfessionalRateLimitService.RateLimitResult rateLimitResult = 
                professionalRateLimitService.checkRateLimit(apiKeyId);

            if (!rateLimitResult.isAllowed()) {
                // Log rate limit exceeded request
                long duration = System.currentTimeMillis() - startTime;
                logFailedRequest(httpRequest, validationResult.getApiKey(), 
                               HttpStatus.TOO_MANY_REQUESTS.value(), duration, 
                               rateLimitResult.getReason(), request.url());
                
                HttpHeaders headers = buildRateLimitHeaders(rateLimitResult);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .headers(headers)
                        .body(buildErrorMap(
                            rateLimitResult.getReason(), 
                            HttpStatus.TOO_MANY_REQUESTS,
                            "RATE_LIMIT_EXCEEDED",
                            validationResult.getRequestDomain(),
                            validationResult.getMatchedDomain()
                        ));
            }

            // Step 4: Forward request to internal service
            CompletableFuture<ResponseEntity<String>> future = forwardService.forward(request.url());
            ResponseEntity<String> extResponse = future.get();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Secure RivoFetch completed: {} {} - Status: {}, Domain: '{}', Duration: {}ms", 
                    method, requestPath, extResponse.getStatusCode().value(), 
                    validationResult.getRequestDomain(), duration);

            // Step 5: Process successful response
            if (extResponse.getStatusCode().is2xxSuccessful()) {
                try {
                    BrandExtractionResponse brandResponse = 
                        objectMapper.readValue(extResponse.getBody(), BrandExtractionResponse.class);
                    
                    // Step 6: Log successful request and update usage statistics
                    logSuccessfulRequest(httpRequest, validationResult.getApiKey(), 
                                       extResponse.getStatusCode().value(), duration, request.url());
                    
                    // Add rate limit headers to successful response
                    HttpHeaders successHeaders = buildRateLimitHeaders(rateLimitResult);
                    return ResponseEntity.ok()
                            .headers(successHeaders)
                            .body(brandResponse);
                            
                } catch (Exception parseException) {
                    log.error("Failed to parse external API response for URL: {}", request.url(), parseException);
                    
                    // Log failed request due to parse error
                    logFailedRequest(httpRequest, validationResult.getApiKey(), 
                                   HttpStatus.INTERNAL_SERVER_ERROR.value(), duration, 
                                   "Failed to parse external API response", request.url());
                    
                    return buildErrorResponse(
                        "Failed to parse external API response",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "PARSE_ERROR",
                        validationResult.getRequestDomain(),
                        validationResult.getMatchedDomain()
                    );
                }
            }

            // Handle external API errors
            // Log failed request due to external API error
            logFailedRequest(httpRequest, validationResult.getApiKey(), 
                           extResponse.getStatusCode().value(), duration, 
                           "External API error: " + extResponse.getBody(), request.url());
            
            return ResponseEntity.status(extResponse.getStatusCode())
                    .body(buildErrorMap(
                        "External API error: " + extResponse.getBody(),
                        extResponse.getStatusCode(),
                        "EXTERNAL_API_ERROR",
                        validationResult.getRequestDomain(),
                        validationResult.getMatchedDomain()
                    ));

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Secure RivoFetch error: {} {} - Duration: {}ms", method, requestPath, duration, e);
            
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                return buildErrorResponse(
                    "External API timed out after " + forwardService.getForwardConfig().getTimeoutSeconds() + " seconds",
                    HttpStatus.GATEWAY_TIMEOUT,
                    "TIMEOUT_ERROR",
                    null,
                    null
                );
            }
            
            return buildErrorResponse(
                "Internal server error: " + e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                null,
                null
            );
        }
    }

    @GetMapping("/health")
    @Operation(
        summary = "Health Check", 
        description = "Health check endpoint for API key users with domain validation",
        parameters = {
            @Parameter(
                name = "x-api-key", 
                description = "API Key for authentication", 
                required = true, 
                in = ParameterIn.HEADER,
                example = "sk-1234567890abcdef..."
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health check successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid API key"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Domain validation failed")
    })
    public ResponseEntity<?> healthCheck(HttpServletRequest httpRequest) {
        try {
            // Extract and validate API key
            String apiKeyValue = apiKeyDomainGuard.extractApiKeyFromRequest(httpRequest);
            
            if (apiKeyValue == null) {
                return buildErrorResponse(
                    "API key is required",
                    HttpStatus.UNAUTHORIZED,
                    "MISSING_API_KEY",
                    null,
                    null
                );
            }

            // Validate API key and domain
            ApiKeyDomainGuard.SecurityValidationResult validationResult = 
                apiKeyDomainGuard.validateApiKeyAccess(apiKeyValue, httpRequest);

            if (!validationResult.isSuccess()) {
                HttpStatus status = determineErrorStatus(validationResult.getErrorCode());
                return buildErrorResponse(
                    validationResult.getErrorMessage(),
                    status,
                    validationResult.getErrorCode(),
                    validationResult.getRequestDomain(),
                    validationResult.getMatchedDomain()
                );
            }

            // Return health status
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", "healthy");
            healthInfo.put("timestamp", Instant.now().toString());
            healthInfo.put("apiKeyId", validationResult.getApiKey().getId().toString());
            healthInfo.put("userId", validationResult.getUserId());
            healthInfo.put("requestDomain", validationResult.getRequestDomain());
            healthInfo.put("matchedDomain", validationResult.getMatchedDomain());
            healthInfo.put("validationType", validationResult.getValidationType());

            return ResponseEntity.ok(healthInfo);

        } catch (Exception e) {
            log.error("Health check error", e);
            return buildErrorResponse(
                "Health check failed: " + e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "HEALTH_CHECK_ERROR",
                null,
                null
            );
        }
    }

    /**
     * Determine HTTP status based on error code
     */
    private HttpStatus determineErrorStatus(String errorCode) {
        if (errorCode == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        
        return switch (errorCode) {
            case "INVALID_API_KEY", "MISSING_API_KEY" -> HttpStatus.UNAUTHORIZED;
            case "DOMAIN_NOT_ALLOWED", "MISSING_DOMAIN_HEADER", "INVALID_FORMAT" -> HttpStatus.FORBIDDEN;
            case "RATE_LIMIT_EXCEEDED" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Build rate limit headers
     */
    private HttpHeaders buildRateLimitHeaders(ProfessionalRateLimitService.RateLimitResult rateLimitResult) {
        HttpHeaders headers = new HttpHeaders();
        
        if (rateLimitResult.getTier() != null) {
            headers.add("X-RateLimit-Limit", String.valueOf(rateLimitResult.getTier().getRequestLimit()));
            headers.add("X-RateLimit-Tier", rateLimitResult.getTier().name());
        }
        
        if (rateLimitResult.getRemainingRequests() != null) {
            headers.add("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingRequests()));
        }
        
        if (rateLimitResult.getResetInSeconds() != null) {
            headers.add("X-RateLimit-Reset", String.valueOf(rateLimitResult.getResetInSeconds()));
        }
        
        if (rateLimitResult.getAdditionalRequestsRemaining() != null) {
            headers.add("X-RateLimit-Additional-Available", String.valueOf(rateLimitResult.getAdditionalRequestsRemaining()));
        }
        
        headers.add("X-RateLimit-Total-Remaining", String.valueOf(rateLimitResult.getTotalRequestsRemaining()));
        
        if (rateLimitResult.isUsedAddOn()) {
            headers.add("X-RateLimit-Used-AddOn", "true");
        }
        
        return headers;
    }

    /**
     * Build error response
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status, 
                                                                  String errorCode, String requestDomain, String matchedDomain) {
        return ResponseEntity.status(status)
                .body(buildErrorMap(message, status, errorCode, requestDomain, matchedDomain));
    }

    /**
     * Build error map
     */
    private Map<String, Object> buildErrorMap(String message, HttpStatusCode status, String errorCode,
                                            String requestDomain, String matchedDomain) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("error", message);
        errorMap.put("status", status.value());
        errorMap.put("errorCode", errorCode);
        errorMap.put("timestamp", Instant.now().toString());
        
        if (requestDomain != null) {
            errorMap.put("requestDomain", requestDomain);
        }
        
        if (matchedDomain != null) {
            errorMap.put("expectedDomain", matchedDomain);
        }
        
        // Add helpful suggestions for domain errors
        if ("DOMAIN_NOT_ALLOWED".equals(errorCode) || "MISSING_DOMAIN_HEADER".equals(errorCode)) {
            errorMap.put("suggestions", List.of(
                "Ensure your request includes Origin or Referer header",
                "Verify your domain is registered for this API key",
                "Check that your domain format is correct (e.g., xamply.com, xamplyfy.co, xamplyfy.in)"
            ));
        }
        
        return errorMap;
    }
    
    /**
     * Log successful API request and update usage statistics
     */
    private void logSuccessfulRequest(HttpServletRequest request, com.example.jwtauthenticator.entity.ApiKey apiKey, 
                                    int responseStatus, long responseTimeMs, String targetUrl) {
        try {
            // Log request to ApiKeyRequestLog table
            apiKeyRequestLogService.logRequestAsync(request, apiKey, responseStatus, responseTimeMs);
            
            // Update usage statistics
            usageStatsService.recordApiKeyUsage(
                apiKey.getId(), 
                apiKey.getUserFkId(), 
                request.getRequestURI(), 
                request.getMethod(), 
                getClientIpAddress(request), 
                apiKey.getRateLimitTier()
            );
            
            // Update monthly usage tracking
            monthlyUsageService.recordApiCall(apiKey.getId(), apiKey.getUserFkId(), true);
            
            log.info("Successfully logged API request for key: {}", apiKey.getId());
            
        } catch (Exception e) {
            log.error("Failed to log successful API request for key: {} - Error: {}", apiKey.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Log failed API request
     */
    private void logFailedRequest(HttpServletRequest request, com.example.jwtauthenticator.entity.ApiKey apiKey, 
                                int responseStatus, long responseTimeMs, String errorMessage, String targetUrl) {
        try {
            // Log request to ApiKeyRequestLog table
            apiKeyRequestLogService.logRequest(request, apiKey, responseStatus, responseTimeMs, errorMessage);
            
            log.info("Successfully logged failed API request for key: {}", apiKey.getId());
            
        } catch (Exception e) {
            log.error("Failed to log failed API request for key: {} - Error: {}", apiKey.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Extract client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}