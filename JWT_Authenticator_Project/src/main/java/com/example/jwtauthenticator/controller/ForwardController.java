package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ForwardRequest;
import com.example.jwtauthenticator.service.ForwardService;
import com.example.jwtauthenticator.service.RateLimiterService;
import com.example.jwtauthenticator.util.JwtUtil;
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
    private final JwtUtil jwtUtil;

    @PostMapping
    @Operation(
        summary = "Forward authenticated request", 
        description = "Forwards an authenticated request to an external API with rate limiting",
        security = { @SecurityRequirement(name = "Bearer Authentication") },
        parameters = {
            @Parameter(
                name = "Authorization", 
                description = "JWT Bearer token", 
                required = true, 
                in = ParameterIn.HEADER,
                example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
            ),
            @Parameter(
                name = "X-Brand-Id", 
                description = "Brand identifier for multi-tenant support", 
                required = true, 
                in = ParameterIn.HEADER,
                example = "brand1"
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Request forwarded successfully"),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid URL or missing required headers"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error"),
        @ApiResponse(responseCode = "504", description = "Gateway Timeout - External API timed out")
    })
    public ResponseEntity<?> forward(
            @Parameter(description = "Forward request details", required = true)
            @Valid @RequestBody ForwardRequest request, 
            HttpServletRequest httpRequest) {
        long start = System.currentTimeMillis();
        String authHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return buildError("Invalid or missing JWT token", HttpStatus.UNAUTHORIZED);
        }
        String token = authHeader.substring(7);
        String userId;
        try {
            userId = jwtUtil.extractUserId(token);
        } catch (Exception e) {
            return buildError("Invalid or missing JWT token", HttpStatus.UNAUTHORIZED);
        }

        ConsumptionProbe probe = rateLimiterService.consume(userId);
        if (!probe.isConsumed()) {
            long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            HttpHeaders headers = new HttpHeaders();
            headers.add("Retry-After", String.valueOf(waitSeconds));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(buildErrorMap("Rate limit exceeded. Try again later.", HttpStatus.TOO_MANY_REQUESTS));
        }

        try {
            CompletableFuture<ResponseEntity<String>> future = forwardService.forward(request.url());
            ResponseEntity<String> extResponse = future.get();
            log.info("userId={} | url={} | status={} | duration={}ms", userId, request.url(), extResponse.getStatusCode().value(), System.currentTimeMillis() - start);
            if (extResponse.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok(extResponse.getBody());
            }
            return ResponseEntity.status(extResponse.getStatusCode())
                    .body(buildErrorMap("External API error: " + extResponse.getBody(), extResponse.getStatusCode()));
        } catch (Exception e) {
            log.error("userId={} | url={} | error={}", userId, request.url(), e.getMessage());
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                return buildError("External API timed out after " + forwardService.getForwardConfig().getTimeoutSeconds() + " seconds", HttpStatus.GATEWAY_TIMEOUT);
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
}
