package com.example.jwtauthenticator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Protected Resources", description = "Endpoints that require authentication")
public class ProtectedController {

    @GetMapping("/protected")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(
        summary = "Access protected resource", 
        description = "This endpoint requires a valid JWT token and X-Brand-Id header",
        security = { @SecurityRequirement(name = "Bearer Authentication") },
        parameters = {
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
        @ApiResponse(responseCode = "200", description = "Successfully accessed protected resource"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token"),
        @ApiResponse(responseCode = "400", description = "Bad Request - Missing X-Brand-Id header"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
    })
    public String protectedEndpoint() {
        return "This is a protected endpoint!";
    }
}
