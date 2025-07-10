package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.ForwardRequest;
import com.example.jwtauthenticator.service.ForwardService;
import com.example.jwtauthenticator.service.RateLimiterService;
import com.example.jwtauthenticator.util.JwtUtil;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
public class ForwardController {

    private final ForwardService forwardService;
    private final RateLimiterService rateLimiterService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<?> forward(@Valid @RequestBody ForwardRequest request, HttpServletRequest httpRequest) {
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

    private Map<String, Object> buildErrorMap(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
