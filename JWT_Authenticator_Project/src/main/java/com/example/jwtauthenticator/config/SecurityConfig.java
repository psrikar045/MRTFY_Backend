package com.example.jwtauthenticator.config;

import com.example.jwtauthenticator.security.JwtRequestFilter;
import com.example.jwtauthenticator.security.JwtUserDetailsService;
import com.example.jwtauthenticator.security.ApiKeyAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtUserDetailsService jwtUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtRequestFilter jwtRequestFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    
    @Autowired
    private AppConfig appConfig;

    public SecurityConfig(JwtUserDetailsService jwtUserDetailsService, PasswordEncoder passwordEncoder, 
                         JwtRequestFilter jwtRequestFilter, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.jwtUserDetailsService = jwtUserDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtRequestFilter = jwtRequestFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(jwtUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                    .frameOptions(frameOptions -> frameOptions.deny())
                    .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000))
                )
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers(
                        // Authentication endpoints
                        "/auth/register", 
                        "/auth/token", 
                        "/auth/login", 
                        "/auth/login/**",  // Includes /login/username and /login/email
                        "/auth/forward", 
                        "/auth/forgot-password", 
                        "/auth/reset-password", 
                        "/auth/refresh", 
                        "/auth/verify-email", 
                        "/auth/google", 
                        "/auth/check-email", 
                        "/auth/forgot-password-code", 
                        "/auth/verify-reset-code", 
                        "/auth/set-new-password",
                        "/auth/public-forward",
                        "/auth/check-username",
                        "/auth/check-username/simple",
                        "/auth/username-exists",
                        "/auth/brand-info",
                        
                        // 2FA endpoints
                        "/auth/tfa/**", 
                        
                        // Other public endpoints
                        "/test/**", 
                        "/api/id-generator/user-id/init-sequence",
                        
                        // Brand asset serving (public access for images/logos)
                        "/api/brands/assets/**",
                        "/api/brands/images/**",
                        
                        // Documentation
                        "/v3/api-docs/**", 
                        "/swagger-ui/**", 
                        "/swagger-ui.html", 
                        
                        // Health check
                        "/actuator/health",
                        "/api/category/hierarchy",
                        "/api/brands/all",
                        
                        // External API endpoints (will be protected by API key filter)
                        "/api/external/**"
                        
                    ).permitAll()
                    .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtRequestFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow specific origins for Google Sign-In and frontend
        configuration.setAllowedOrigins(Arrays.asList(
            appConfig.getFrontendBaseUrl(), // Centralized frontend URL
            "http://localhost:3000", // Development fallback
            "http://localhost:3001", // Development fallback
            "http://localhost:4200", // Angular application
            "https://accounts.google.com", // Google Sign-In
            "http://202.65.155.117"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Forward-URL", "X-Forwarded-For", "X-API-KEY"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
