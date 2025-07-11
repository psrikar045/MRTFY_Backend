package com.example.jwtauthenticator.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email-based login request")
public record EmailLoginRequest(
    @Schema(description = "Email for authentication", example = "john.doe@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    String email,
    
    @Schema(description = "Password for authentication", example = "SecurePassword123!")
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    String password
) {
}