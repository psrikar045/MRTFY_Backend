package com.example.jwtauthenticator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Autowired
    private AppConfig appConfig;

    @Bean
    @Primary
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JWT Authenticator & Brand Data API")
                        .version("2.0.0")
                        .description("A comprehensive JWT-based authentication system with brand data extraction, " +
                                "multi-tenant support, email verification, two-factor authentication, audit logging, " +
                                "and advanced API key management. Features include: rate-limited API keys, " +
                                "scope-based authorization, IP/domain restrictions, and comprehensive admin controls. " +
                                "Supports both JWT (for web UI) and API key authentication (for external integrations).")
                        .contact(new Contact()
                                .name("JWT Authenticator Team")
                                .email("support@jwtauthenticator.com")
                                .url("https://github.com/example/jwt-authenticator"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(Arrays.asList(
                        new Server()
                                .url(appConfig.getApiBaseUrl())
                                .description(appConfig.isLocalDevelopment() ? "Development Server" : "Production Server"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Fallback"),
                        new Server()
                                .url("http://202.65.155.125:8080/myapp")
                                .description("Tomcat Deployment")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication")
                        .addList("API Key")
                        .addList("X-Brand-Id"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createJWTScheme())
                        .addSecuritySchemes("API Key", createApiKeyScheme())
                        .addSecuritySchemes("X-Brand-Id", createBrandIdScheme()));
    }

    private SecurityScheme createJWTScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer")
                .description("Enter JWT Bearer token in the format: Bearer {token}");
    }
    
    private SecurityScheme createApiKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-KEY")
                .description("API Key for external integrations (e.g., sk-abc123..., biz-def456..., admin-xyz789...)");
    }
    
    private SecurityScheme createBrandIdScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-Brand-Id")
                .description("Brand identifier for multi-tenant support (e.g., brand1, brand2)");
    }
}