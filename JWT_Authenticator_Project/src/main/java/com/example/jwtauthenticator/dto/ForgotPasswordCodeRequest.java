package com.example.jwtauthenticator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Forgot password request using userId and email")
public record ForgotPasswordCodeRequest(
    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @NotBlank(message = "User ID is required")
    String userId,

    @Schema(description = "Email address", example = "user@example.com")
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email
) {}
