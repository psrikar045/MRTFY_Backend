package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ForwardRequest;
import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.dto.BrandExtractionResponse;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.service.ApiKeyAuthenticationService;
import com.example.jwtauthenticator.service.ForwardService;
import com.example.jwtauthenticator.service.ForwardUsageValidationService;
import com.example.jwtauthenticator.service.ForwardJwtUsageTrackingService;
import com.example.jwtauthenticator.service.RateLimiterService;
import com.example.jwtauthenticator.service.ProfessionalRateLimitService;
import com.example.jwtauthenticator.service.StreamlinedUsageTracker;
import com.example.jwtauthenticator.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/forward")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Request Forwarding", description = "Endpoints for forwarding authenticated requests to external APIs")
public class ForwardController {

    private final ForwardService forwardService;
    private final RateLimiterService rateLimiterService;
    private final ProfessionalRateLimitService professionalRateLimitService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final ApiKeyAuthenticationService apiKeyAuthenticationService;
    private final ForwardUsageValidationService forwardUsageValidationService;
    private final ForwardJwtUsageTrackingService forwardJwtUsageTrackingService;
    private final StreamlinedUsageTracker streamlinedUsageTracker;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(
        summary = "Forward authenticated request", 
        description = "Forwards an authenticated request to an external API with rate limiting",
        security = { @SecurityRequirement(name = "Bearer Authentication") },
        parameters = {
            @Parameter(
                name = "Authorization", 
                description = "JWT Bearer token OR API Key", 
                required = true, 
                in = ParameterIn.HEADER,
                example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... OR sk-1234567890abcdef..."
            ),
            @Parameter(
                name = "X-API-Key", 
                description = "Alternative API Key authentication (if not using Authorization header)", 
                required = false, 
                in = ParameterIn.HEADER,
                example = "sk-1234567890abcdef..."
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Request forwarded successfully",
            content = @Content(schema = @Schema(implementation = BrandExtractionResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid URL or missing required headers"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token or API key"),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error"),
        @ApiResponse(responseCode = "504", description = "Gateway Timeout - External API timed out")
    })
    public ResponseEntity<?> forward(
            @Parameter(description = "Forward request details", required = true)
            @Valid @RequestBody ForwardRequest request, 
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        long start = System.currentTimeMillis();
        
        // Get authentication details (authentication already handled by security filter)
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        String userId;
        String apiKeyId = null;
        String authMethod = "JWT"; // Default to JWT
        ApiKey apiKey = null; // Store the full API key object
        UserPlan userPlan = UserPlan.FREE; // Default plan
        
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // JWT authentication
                String token = authHeader.substring(7);
                userId = jwtUtil.extractUserId(token);
                authMethod = "JWT";
                
                // Get user plan for JWT users
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    userPlan = user.getPlan() != null ? user.getPlan() : UserPlan.FREE;
                }
                
                // PHASE 2: Plan-based validation for JWT users
                ForwardUsageValidationService.ValidationResult validation = 
                    forwardUsageValidationService.validateApiCallLimit(userId, userPlan);
                if (!validation.isAllowed()) {
                    log.warn("JWT usage limit exceeded for user {} (plan: {}): {}", userId, userPlan.getDisplayName(), validation.getReason());
                    return buildError(validation.getReason(), HttpStatus.TOO_MANY_REQUESTS);
                }
                
                // For JWT, use existing rate limiter
                ConsumptionProbe probe = rateLimiterService.consume(userId);
                if (!probe.isConsumed()) {
                    long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("Retry-After", String.valueOf(waitSeconds));
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .headers(headers)
                            .body(buildErrorMap("Rate limit exceeded. Try again later.", HttpStatus.TOO_MANY_REQUESTS));
                }
            } else {
                // API Key authentication - use professional rate limiting
                String apiKeyValue = authHeader != null ? authHeader : httpRequest.getHeader("X-API-Key");
                ApiKeyAuthenticationService.ApiKeyAuthResult authResult = 
                    apiKeyAuthenticationService.authenticateApiKey(apiKeyValue);
                
                if (!authResult.isSuccess()) {
                    return buildError("Authentication failed", HttpStatus.UNAUTHORIZED);
                }
                
                userId = authResult.getUserId();
                apiKey = authResult.getApiKey(); // Store the full API key object
                apiKeyId = apiKey.getId().toString();
                authMethod = "API_KEY";
                
                // Get user plan for API key users
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    userPlan = user.getPlan() != null ? user.getPlan() : UserPlan.FREE;
                }
                
                // PHASE 2: Plan-based validation for API key users
                ForwardUsageValidationService.ValidationResult validation = 
                    forwardUsageValidationService.validateApiCallLimit(userId, userPlan);
                if (!validation.isAllowed()) {
                    log.warn("API key usage limit exceeded for user {} (plan: {}): {}", userId, userPlan.getDisplayName(), validation.getReason());
                    return buildError(validation.getReason(), HttpStatus.TOO_MANY_REQUESTS);
                }
                
                // Apply professional rate limiting for API keys
                ProfessionalRateLimitService.RateLimitResult rateLimitResult = 
                    professionalRateLimitService.checkRateLimit(apiKeyId);
                
                if (!rateLimitResult.isAllowed()) {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("X-RateLimit-Limit", String.valueOf(rateLimitResult.getTier() != null ? rateLimitResult.getTier().getRequestLimit() : 0));
                    headers.add("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingRequests() != null ? rateLimitResult.getRemainingRequests() : 0));
                    headers.add("X-RateLimit-Reset", String.valueOf(rateLimitResult.getResetInSeconds() != null ? rateLimitResult.getResetInSeconds() : 0));
                    headers.add("X-RateLimit-Tier", rateLimitResult.getTier() != null ? rateLimitResult.getTier().name() : "UNKNOWN");
                    headers.add("X-RateLimit-Additional-Available", String.valueOf(rateLimitResult.getAdditionalRequestsRemaining() != null ? rateLimitResult.getAdditionalRequestsRemaining() : 0));
                    headers.add("X-RateLimit-Total-Remaining", String.valueOf(rateLimitResult.getTotalRequestsRemaining()));
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .headers(headers)
                            .body(buildErrorMap(rateLimitResult.getReason(), HttpStatus.TOO_MANY_REQUESTS));
                }
                
                // Add success headers for API key requests
                HttpHeaders successHeaders = new HttpHeaders();
                successHeaders.add("X-RateLimit-Limit", String.valueOf(rateLimitResult.getTier().getRequestLimit()));
                successHeaders.add("X-RateLimit-Remaining", String.valueOf(rateLimitResult.getRemainingRequests()));
                successHeaders.add("X-RateLimit-Tier", rateLimitResult.getTier().name());
                successHeaders.add("X-RateLimit-Additional-Available", String.valueOf(rateLimitResult.getAdditionalRequestsRemaining()));
                successHeaders.add("X-RateLimit-Total-Remaining", String.valueOf(rateLimitResult.getTotalRequestsRemaining()));
                if (rateLimitResult.isUsedAddOn()) {
                    successHeaders.add("X-RateLimit-Used-AddOn", "true");
                }
                
                // Store headers for later use in response
                httpRequest.setAttribute("rateLimitHeaders", successHeaders);
            }
        } catch (Exception e) {
            return buildError("Invalid authentication", HttpStatus.UNAUTHORIZED);
        }

        try {
            // PHASE 2: Use consistent forwarding with usage tracking
            CompletableFuture<ResponseEntity<String>> future;
            if (apiKey != null) {
                // API key authentication - use forwardWithLogging (same as /rivofetech)
                future = forwardService.forwardWithLogging(request.url(), httpRequest, httpResponse, apiKey);
            } else {
                // JWT authentication - use forwardWithPublicLogging but ADD usage tracking
                future = forwardService.forwardWithPublicLogging(request.url(), httpRequest, httpResponse);
            }
            
            ResponseEntity<String> extResponse = future.get();
            long duration = System.currentTimeMillis() - start;
            
            log.info("userId={} | authMethod={} | plan={} | url={} | status={} | duration={}ms", 
                    userId, authMethod, userPlan.getDisplayName(), request.url(), 
                    extResponse.getStatusCode().value(), duration);
            
            // PHASE 2: Track usage in same tables as /rivofetech
            if (extResponse.getStatusCode().is2xxSuccessful()) {
                trackApiUsage(userId, userPlan, apiKey, request.url(), httpRequest, 
                            extResponse.getStatusCode().value(), duration, null);
                
                // Parse the response into BrandExtractionResponse object
                try {
                    BrandExtractionResponse brandResponse = objectMapper.readValue(extResponse.getBody(), BrandExtractionResponse.class);
                    return ResponseEntity.ok(brandResponse);
                } catch (Exception parseException) {
                    log.error("Failed to parse external API response as BrandExtractionResponse for URL: {}", request.url(), parseException);
                    
                    // Track the parsing error
                    trackApiUsage(userId, userPlan, apiKey, request.url(), httpRequest, 
                                500, duration, "Failed to parse external API response");
                    
                    return buildError("Failed to parse external API response", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                // Track failed response
                trackApiUsage(userId, userPlan, apiKey, request.url(), httpRequest, 
                            extResponse.getStatusCode().value(), duration, 
                            "External API error: " + extResponse.getBody());
            }
            
            return ResponseEntity.status(extResponse.getStatusCode())
                    .body(buildErrorMap("External API error: " + extResponse.getBody(), extResponse.getStatusCode()));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("userId={} | authMethod={} | plan={} | url={} | error={}", 
                     userId, authMethod, userPlan.getDisplayName(), request.url(), e.getMessage());
            
            // Track the error
            String errorMessage = e.getMessage();
            int errorStatus = 500;
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                errorMessage = "External API timed out after " + forwardService.getForwardConfig().getTimeoutSeconds() + " seconds";
                errorStatus = 504;
            }
            
            trackApiUsage(userId, userPlan, apiKey, request.url(), httpRequest, 
                        errorStatus, duration, errorMessage);
            
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                return buildError(errorMessage, HttpStatus.GATEWAY_TIMEOUT);
            }
            return buildError("External API error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    private ResponseEntity<Map<String, Object>> buildError(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(buildErrorMap(message, status));
    }

    private Map<String, Object> buildErrorMap(String message, HttpStatusCode status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        body.put("timestamp", Instant.now().toString());
        return body;
    }

    /**
     * Track API usage in same tables as /rivofetech for consistency
     */
    private void trackApiUsage(String userId, UserPlan userPlan, ApiKey apiKey, String url, 
                             HttpServletRequest request, int responseStatus, long responseTimeMs, 
                             String errorMessage) {
        try {
            if (apiKey != null) {
                // For API key requests - use StreamlinedUsageTracker (same as /rivofetech)
                streamlinedUsageTracker.trackRivofetchCallSync(
                    apiKey.getId(),
                    userId,
                    getClientIpAddress(request),
                    extractDomainFromRequest(request),
                    request.getHeader("User-Agent"),
                    responseStatus,
                    responseTimeMs,
                    errorMessage
                );
                log.debug("✅ API key usage tracked for /forward: apiKey={}, status={}", apiKey.getId(), responseStatus);
            } else {
                // For JWT requests - use JWT usage tracking service
                forwardJwtUsageTrackingService.trackJwtUsage(
                    userId, userPlan, url, request, responseStatus, responseTimeMs, errorMessage
                );
                log.debug("✅ JWT usage tracked for /forward: user={}, plan={}, status={}", 
                         userId, userPlan.getDisplayName(), responseStatus);
            }
        } catch (Exception e) {
            log.error("❌ Failed to track /forward usage: userId={}, apiKey={}, error={}", 
                     userId, apiKey != null ? apiKey.getId() : null, e.getMessage());
            // Don't throw - we don't want to break the API call due to tracking issues
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

    /**
     * Extract domain from request headers
     */
    private String extractDomainFromRequest(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isEmpty()) {
            return origin.replaceAll("https?://", "");
        }
        
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return referer.replaceAll("https?://", "").split("/")[0];
        }
        
        return request.getServerName();
    }

}
