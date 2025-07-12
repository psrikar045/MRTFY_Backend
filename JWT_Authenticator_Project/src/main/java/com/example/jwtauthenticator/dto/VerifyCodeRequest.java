package com.example.jwtauthenticator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Verify reset code request")
public record VerifyCodeRequest(
    @Schema(description = "User ID", example = "MRTFY000001")
    @NotBlank(message = "User ID is required")
    String userId,
    
    @Schema(description = "Email address", example = "user@example.com")
    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is required")
    String email,
    
    @Schema(description = "Verification code", example = "123456")
    @NotBlank(message = "Verification code is required")
    String code
) {}