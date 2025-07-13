package com.example.jwtauthenticator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class SwaggerUiConfig {

    @Value("${server.servlet.context-path:/}")
    private String contextPath;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public-api")
                .pathsToMatch("/auth/**", "/api/**", "/test/**", "/actuator/**")
                .build();
    }

    @Bean
    public OpenAPI extendedOpenAPI(SwaggerConfig swaggerConfig) {
        OpenAPI openAPI = swaggerConfig.customOpenAPI();
        
        // Add servers with context path - use the baseUrl from configuration
        openAPI.setServers(Arrays.asList(
            new Server().url(baseUrl).description("Current Environment"),
            new Server().url("http://202.65.155.125:8080/myapp").description("Production Server")
        ));
        
        return openAPI;
    }
}