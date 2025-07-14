package com.example.jwtauthenticator.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login request")
public record LoginRequest(
    @Schema(description = "Username or email for authentication", example = "john_doe or john.doe@example.com")
    @NotBlank(message = "Username or email is required")
    String username,
    
    @Schema(description = "Password for authentication", example = "SecurePassword123!")
    @NotBlank(message = "Password is required")
    String password
) {}