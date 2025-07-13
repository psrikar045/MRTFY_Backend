package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.model.AuthRequest;
import com.example.jwtauthenticator.model.AuthResponse;
import com.example.jwtauthenticator.model.EmailLoginRequest;
import com.example.jwtauthenticator.model.RegisterRequest;
import com.example.jwtauthenticator.model.UsernameLoginRequest;
import com.example.jwtauthenticator.service.AuthService;
import com.example.jwtauthenticator.service.ForwardService;
import com.example.jwtauthenticator.service.PasswordResetService;
import com.example.jwtauthenticator.service.RateLimiterService;
import com.example.jwtauthenticator.service.TfaService;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.dto.*;
import com.example.jwtauthenticator.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication and user management endpoints")
@lombok.extern.slf4j.Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private TfaService tfaService;

    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private ForwardService forwardService;
    
    @Autowired
    private RateLimiterService rateLimiterService;
    
    @Autowired
    private UserRepository userRepository;

    @Operation(summary = "Register a new user", 
               description = "Register a new user account with email verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Username or email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.registerUser(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @Operation(
        summary = "Generate authentication token", 
        description = "Generate JWT access and refresh tokens for authenticated user. Include brandId in the request for multi-tenant support."
    )
    @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200", 
                description = "Authentication successful",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AuthResponse.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Success Response",
                            value = com.example.jwtauthenticator.model.ApiRequestExamples.LOGIN_RESPONSE
                        )
                    }
                )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid credentials or email not verified")
    })
    @PostMapping("/token")
    public ResponseEntity<?> createAuthenticationToken(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Authentication request with username, password and optional brandId",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AuthRequest.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Authentication with Brand ID",
                            value = com.example.jwtauthenticator.model.ApiRequestExamples.LOGIN_REQUEST_WITH_BRAND
                        )
                    }
                )
            )
            @Valid @RequestBody AuthRequest authenticationRequest) throws Exception {
        AuthResponse authResponse = authService.createAuthenticationToken(authenticationRequest);
        return ResponseEntity.ok(authResponse);
    }

    @Operation(
        summary = "User login (Legacy)", 
        description = "Legacy login endpoint. Use /login/username or /login/email instead."
    )
    @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200", 
                description = "Login successful",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AuthResponse.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Success Response",
                            value = com.example.jwtauthenticator.model.ApiRequestExamples.LOGIN_RESPONSE
                        )
                    }
                )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Authentication request with username, password and optional brandId",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AuthRequest.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Login with Brand ID",
                            value = com.example.jwtauthenticator.model.ApiRequestExamples.LOGIN_REQUEST_WITH_BRAND
                        )
                    }
                )
            )
            @Valid @RequestBody AuthRequest authenticationRequest) throws Exception {
        AuthResponse authResponse = authService.loginUser(authenticationRequest);
        return ResponseEntity.ok(authResponse);
    }
    
    @Operation(
        summary = "Username-based login", 
        description = "Authenticate user with username and password and return JWT tokens with brandId and expiration time."
    )
    @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200", 
                description = "Login successful",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AuthResponse.class)
                )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid credentials")
    })
    @PostMapping("/login/username")
    public ResponseEntity<?> loginWithUsername(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Username-based login request",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = UsernameLoginRequest.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Username Login",
                            value = com.example.jwtauthenticator.model.ApiRequestExamples.USERNAME_LOGIN_REQUEST
                        )
                    }
                )
            )
            @Valid @RequestBody UsernameLoginRequest loginRequest) throws Exception {
        AuthResponse authResponse = authService.loginWithUsername(loginRequest.username(), loginRequest.password());
        return ResponseEntity.ok(authResponse);
    }
    
    @Operation(
        summary = "Email-based login", 
        description = "Authenticate user with email and password and return JWT tokens with brandId and expiration time."
    )
    @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200", 
                description = "Login successful",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AuthResponse.class)
                )
            ),
            @ApiResponse(responseCode = "400", description = "Invalid credentials")
    })
    @PostMapping("/login/email")
    public ResponseEntity<?> loginWithEmail(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Email-based login request",
                required = true,
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = EmailLoginRequest.class),
                    examples = {
                        @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Email Login",
                            value = com.example.jwtauthenticator.model.ApiRequestExamples.EMAIL_LOGIN_REQUEST
                        )
                    }
                )
            )
            @Valid @RequestBody EmailLoginRequest loginRequest) throws Exception {
        AuthResponse authResponse = authService.loginWithEmail(loginRequest.email(), loginRequest.password());
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Refresh JWT token", 
               description = "Generate new access and refresh tokens using a valid refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody String refreshToken) throws Exception {
        AuthResponse authResponse = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Google Sign-In", 
               description = "Authenticate user using Google ID token and return JWT tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Google Sign-In successful", 
                        content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid Google ID token"),
            @ApiResponse(responseCode = "500", description = "Google Sign-In service error")
    })
    @PostMapping("/google")
    public ResponseEntity<?> googleSignIn(@Valid @RequestBody GoogleSignInRequest request) {
        try {
            AuthResponse authResponse = authService.googleSignIn(request);
            return ResponseEntity.ok(authResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Google Sign-In failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password reset with 6-digit verification code", 
               description = "Initiates the password reset process by generating and sending a 6-digit verification code")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification code sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input or user ID and email do not match")
    })
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            String result = authService.sendPasswordResetCode(request);
            return ResponseEntity.ok(Map.of("message", result));
        } catch (Exception e) {
            // Return a generic response to avoid revealing whether the user exists
            return ResponseEntity.ok(Map.of(
                "message", "If the user ID and email are registered, a verification code will be sent."
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok("Password has been reset successfully.");
    }

    @PostMapping("/tfa/setup")
    @Operation(
        summary = "Setup Two-Factor Authentication", 
        description = "Generate a new 2FA secret for a user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA secret generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid username")
    })
    public ResponseEntity<?> setupTfa(
            @Parameter(description = "Username to set up 2FA for", required = true)
            @RequestParam String username) {
        String secret = tfaService.generateNewSecret(username);
        return ResponseEntity.ok("New 2FA secret generated: " + secret);
    }

    @PostMapping("/tfa/verify")
    @Operation(
        summary = "Verify Two-Factor Authentication code", 
        description = "Verify a 2FA code provided by the user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA code verified successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid 2FA code")
    })
    public ResponseEntity<?> verifyTfa(
            @Parameter(description = "2FA verification request", required = true)
            @Valid @RequestBody TfaRequest request) {
        if (tfaService.verifyCode(request.username(), Integer.parseInt(request.code()))) {
            return ResponseEntity.ok("2FA code verified successfully.");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid 2FA code.");
        }
    }
    
    @Operation(
        summary = "Public forward request", 
        description = "Forwards a request to an external API without authentication requirement"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Request forwarded successfully"),
        @ApiResponse(responseCode = "400", description = "Bad Request - Invalid URL"),
        @ApiResponse(responseCode = "429", description = "Too Many Requests - Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error"),
        @ApiResponse(responseCode = "504", description = "Gateway Timeout - External API timed out")
    })
    @PostMapping("/public-forward")
    public ResponseEntity<?> publicForward(
            @Parameter(description = "Forward request details", required = true)
            @Valid @RequestBody PublicForwardRequest request,
            HttpServletRequest httpRequest) {
        
        long start = System.currentTimeMillis();
        String clientIp = getClientIp(httpRequest);
        
        // Apply rate limiting based on client IP with the public rate limiter
        io.github.bucket4j.ConsumptionProbe probe = rateLimiterService.consumePublic(clientIp);
        if (!probe.isConsumed()) {
            long waitSeconds = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            HttpHeaders headers = new HttpHeaders();
            headers.add("Retry-After", String.valueOf(waitSeconds));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(buildErrorMap("Rate limit exceeded. Try again later.", HttpStatus.TOO_MANY_REQUESTS));
        }
        
        try {
            java.util.concurrent.CompletableFuture<ResponseEntity<String>> future = forwardService.forward(request.url());
            ResponseEntity<String> extResponse = future.get();
            
            // Log the request (without user ID since this is unauthenticated)
            log.info("ip={} | url={} | status={} | duration={}ms", 
                    clientIp, request.url(), extResponse.getStatusCode().value(), System.currentTimeMillis() - start);
            
            if (extResponse.getStatusCode().is2xxSuccessful()) {
                return ResponseEntity.ok(extResponse.getBody());
            }
            
            return ResponseEntity.status(extResponse.getStatusCode())
                    .body(buildErrorMap("External API error: " + extResponse.getBody(), extResponse.getStatusCode()));
        } catch (Exception e) {
            log.error("ip={} | url={} | error={}", clientIp, request.url(), e.getMessage());
            
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                return buildError("External API timed out after " + forwardService.getForwardConfig().getTimeoutSeconds() + " seconds", 
                        HttpStatus.GATEWAY_TIMEOUT);
            }
            
            return buildError("External API error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    private ResponseEntity<Map<String, Object>> buildError(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(buildErrorMap(message, status));
    }
    
    private Map<String, Object> buildErrorMap(String message, HttpStatusCode status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        body.put("timestamp", java.time.Instant.now().toString());
        return body;
    }
    
    private Map<String, Object> buildErrorMap(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("status", status.value());
        body.put("timestamp", java.time.Instant.now().toString());
        return body;
    }

    @PostMapping("/tfa/enable")
    @Operation(
        summary = "Enable Two-Factor Authentication", 
        description = "Enable 2FA for a user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA enabled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid username or 2FA setup not completed")
    })
    public ResponseEntity<?> enableTfa(
            @Parameter(description = "Username to enable 2FA for", required = true)
            @RequestParam String username) {
        tfaService.enableTfa(username);
        return ResponseEntity.ok("2FA enabled for user: " + username);
    }

    @PostMapping("/tfa/disable")
    @Operation(
        summary = "Disable Two-Factor Authentication", 
        description = "Disable 2FA for a user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "2FA disabled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid username")
    })
    public ResponseEntity<?> disableTfa(
            @Parameter(description = "Username to disable 2FA for", required = true)
            @RequestParam String username) {
        tfaService.disableTfa(username);
        return ResponseEntity.ok("2FA disabled for user: " + username);
    }

    @GetMapping("/tfa/qr-code")
    @Operation(
        summary = "Get 2FA QR Code", 
        description = "Generate a QR code for 2FA setup"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "QR code generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid username or 2FA not set up")
    })
    public ResponseEntity<byte[]> getTfaQrCode(
            @Parameter(description = "Username to get QR code for", required = true)
            @RequestParam String username) {
        try {
            byte[] qrCode = tfaService.generateQRCode(username);
            return ResponseEntity.ok()
                    .header("Content-Type", "image/png")
                    .body(qrCode);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/tfa/current-code")
    @Operation(
        summary = "Get current TOTP code", 
        description = "Get the current time-based one-time password (TOTP) for a user (for testing purposes)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Current TOTP code retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid username or 2FA not set up")
    })
    public ResponseEntity<?> getCurrentTotpCode(
            @Parameter(description = "Username to get TOTP code for", required = true)
            @RequestParam String username) {
        try {
            int currentCode = tfaService.getCurrentTotpCode(username);
            Map<String, String> response = new HashMap<>();
            response.put("username", username);
            response.put("currentCode", String.format("%06d", currentCode));
            response.put("note", "This code changes every 30 seconds");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Verify email address", 
               description = "Verify user's email address using the verification token sent via email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid verification token")
    })
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Parameter(description = "Email verification token") @RequestParam String token) {
        String response = authService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forward")
    public ResponseEntity<?> forwardRequest(@Valid @RequestBody AuthRequest authenticationRequest, @RequestHeader(value = "X-Forward-URL", required = true) String forwardUrl) throws Exception {
        // Authenticate user and get JWT token
        AuthResponse authResponse = authService.loginUser(authenticationRequest);
        String token = authResponse.token();

        // Option 1: Using RestTemplate (default)
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("userId", authenticationRequest.username()); // Pass userId in header
        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>("parameters", headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(forwardUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error forwarding request: " + e.getMessage());
        }

        /*
        // Option 2: Using WebClient (commented out for reference)
        WebClient webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader("userId", authenticationRequest.username())
                .build();

        Mono<String> responseMono = webClient.get()
                .uri(forwardUrl)
                .retrieve()
                .bodyToMono(String.class);

        return ResponseEntity.ok(responseMono.block());
        */
    }

    // Profile Update Endpoint
    @PutMapping("/profile")
    @Operation(
        summary = "Update user profile", 
        description = "Update user profile information",
        security = { @SecurityRequirement(name = "Bearer Authentication") },
        parameters = {
            @Parameter(
                name = "X-Brand-Id", 
                description = "Brand identifier for multi-tenant support", 
                required = true, 
                in = ParameterIn.HEADER,
                example = "brand1"
            ),
            @Parameter(
                name = "Authorization", 
                description = "JWT Bearer token", 
                required = true, 
                in = ParameterIn.HEADER,
                example = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
            )
        }
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input or missing X-Brand-Id header"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<?> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            HttpServletRequest httpRequest) {
        try {
            // Extract user info from JWT token
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization header missing");
            }

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            String brandId = httpRequest.getHeader("X-Brand-Id");

            if (brandId == null) {
                return ResponseEntity.badRequest().body("Brand ID header missing");
            }

            String result = authService.updateProfile(username, brandId, request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Check Email Existence Endpoint
    @PostMapping("/check-email")
    @Operation(summary = "Check if email exists", description = "Check if an email address is already registered")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email check completed"),
        @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<?> checkEmail(@Valid @RequestBody CheckEmailRequest request) {
        try {
            boolean exists = authService.checkEmailExists(request);
            return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "Email address is already registered" : "Email address is available"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }
    
    // Check Username Existence Endpoint
    @PostMapping("/check-username")
    @Operation(
        summary = "Check if username exists", 
        description = "Check if a username is already registered with a specific brand. This endpoint requires a brand ID."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Username check completed successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid input",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        )
    })
    public ResponseEntity<?> checkUsername(@Valid @RequestBody CheckUsernameRequest request) {
        try {
            boolean exists = authService.checkUsernameExists(request);
            return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "Username is already taken" : "Username is available"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }
    
    // Simple Check Username Existence Endpoint (without brand ID)
    @PostMapping("/check-username/simple")
    @Operation(
        summary = "Check if username exists (simple version)", 
        description = "Check if a username is already registered across all brands. This endpoint does not require a brand ID."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Username check completed successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid input",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        )
    })
    public ResponseEntity<?> checkUsernameSimple(@Valid @RequestBody SimpleCheckUsernameRequest request) {
        try {
            boolean exists = authService.checkUsernameExists(request);
            return ResponseEntity.ok(Map.of(
                "exists", exists,
                "message", exists ? "Username is already taken" : "Username is available"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }
    
    // GET Endpoint for Username Check (most RESTful approach)
    @GetMapping("/username-exists")
    @Operation(
        summary = "Check if username exists (GET method)", 
        description = "Check if a username is already registered across all brands using a GET request. This is the most RESTful approach for checking username existence."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "Username check completed successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid input",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = Map.class)
            )
        )
    })
    public ResponseEntity<?> usernameExists(
            @Parameter(description = "Username to check", required = true, example = "johndoe")
            @RequestParam String username) {
        try {
            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Username is required",
                    "timestamp", java.time.Instant.now().toString()
                ));
            }
            
            // Validate username format
            if (username.length() < 3 || username.length() > 50) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Username must be between 3 and 50 characters",
                    "timestamp", java.time.Instant.now().toString()
                ));
            }
            
            if (!username.matches("^[a-zA-Z0-9._-]+$")) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Username can only contain letters, numbers, dots, underscores, and hyphens",
                    "timestamp", java.time.Instant.now().toString()
                ));
            }
            
            boolean exists = authService.checkUsernameExists(username);
            return ResponseEntity.ok(Map.of(
                "username", username,
                "exists", exists,
                "message", exists ? "Username is already taken" : "Username is available",
                "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "timestamp", java.time.Instant.now().toString()
            ));
        }
    }

    // Enhanced Forgot Password - Send Verification Code
    @PostMapping("/forgot-password-code")
    @Operation(summary = "Send password reset verification code", description = "Send a verification code to the user's email for password reset")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification code sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<?> sendPasswordResetCode(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            String result = authService.sendPasswordResetCode(request);
            return ResponseEntity.ok(Map.of("message", result));
        } catch (Exception e) {
            // Return a generic response to avoid revealing whether the user exists
            return ResponseEntity.ok(Map.of(
                "message", "If the user ID and email are registered, a verification code will be sent."
            ));
        }
    }

    // Verify Reset Code - Step 2: Only verify the code
    @PostMapping("/verify-reset-code")
    @Operation(summary = "Verify password reset code", 
               description = "Verify the password reset verification code. This step only validates the code - password reset happens in the next step.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Code verified successfully - proceed to password reset"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired code")
    })
    public ResponseEntity<?> verifyResetCode(@Valid @RequestBody VerifyCodeRequest request) {
        try {
            Map result = authService.verifyResetCode(request);
            return ResponseEntity.ok(Map.of(
                "message", result.get("message"),
                "verified", true,
                "userId", result.get("userId"),
                "email", request.email(),
                "code", request.code(),
                "nextStep", "You can now call /auth/set-new-password with the same code to reset your password"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", e.getMessage(),
                "verified", false
            ));
        }
    }

    // Set New Password - Step 3: Reset password after verification
    @PostMapping("/set-new-password")
    @Operation(summary = "Set new password after verification", 
               description = "Set a new password using the verified code. This can only be done after successfully verifying the code.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid, expired, or already used code")
    })
    public ResponseEntity<?> setNewPassword(@Valid @RequestBody SetNewPasswordRequest request) {
        try {
            String result = authService.setNewPassword(request);
            return ResponseEntity.ok(Map.of(
                "message", result,
                "success", true
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", e.getMessage(),
                "success", false
            ));
        }
    }
}