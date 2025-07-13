package com.example.jwtauthenticator.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record PublicForwardRequest(
        @NotBlank(message = "URL is required")
        @URL(regexp = "^(https?://).+", message = "Invalid URL")
        String url
) {}