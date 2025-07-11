package com.example.jwtauthenticator.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response containing JWT tokens and user information")
public record AuthResponse(
    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String token,
    
    @Schema(description = "JWT refresh token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String refreshToken,
    
    @Schema(description = "Brand ID associated with the user", example = "brand1")
    String brandId,
    
    @Schema(description = "Token expiration time in seconds", example = "3600")
    long expirationTime
) {
    public AuthResponse(String token, String refreshToken) {
        this(token, refreshToken, null, 0);
    }
    
    public AuthResponse(String token) {
        this(token, null, null, 0);
    }
}