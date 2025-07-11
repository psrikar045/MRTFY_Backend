package com.example.jwtauthenticator.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Username-based login request")
public record UsernameLoginRequest(
    @Schema(description = "Username for authentication", example = "john_doe")
    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,20}$", message = "Username must be 3-20 characters and can only contain letters, numbers, underscores, and hyphens")
    String username,
    
    @Schema(description = "Password for authentication", example = "SecurePassword123!")
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    String password
) {
}