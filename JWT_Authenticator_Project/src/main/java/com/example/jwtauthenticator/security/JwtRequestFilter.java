package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter that intercepts all requests to validate JWT tokens and brand ID headers.
 * This filter ensures that:
 * 1. Valid JWT tokens are present in the Authorization header
 * 2. X-Brand-Id header is included for multi-tenant support
 */
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtUserDetailsService jwtUserDetailsService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtRequestFilter(JwtUtil jwtUtil, JwtUserDetailsService jwtUserDetailsService) {
        this.jwtUtil = jwtUtil;
        this.jwtUserDetailsService = jwtUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        final String brandId = request.getHeader("X-Brand-Id"); // Extract brandId
        final String requestPath = request.getRequestURI();

        // Skip authentication for public endpoints
        if (isPublicEndpoint(requestPath)) {
            chain.doFilter(request, response);
            return;
        }

        // Check if Authorization header is present and valid
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, 
                "Authorization header is missing or invalid", 
                "Please include a valid Bearer token in the Authorization header");
            return;
        }

        String username = null;
        String jwt = null;

        try {
            jwt = authorizationHeader.substring(7);
            username = jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, 
                "Invalid JWT token", 
                "The provided JWT token is malformed or invalid");
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Validate X-Brand-Id header is present
            if (brandId == null || brandId.isEmpty()) {
                sendErrorResponse(response, HttpStatus.BAD_REQUEST, 
                    "X-Brand-Id header is missing", 
                    "Please include X-Brand-Id header in your request. This header is required for multi-tenant support.");
                return;
            }

            try {
                UserDetails userDetails = this.jwtUserDetailsService.loadUserByUsernameAndBrandId(username, brandId);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    sendErrorResponse(response, HttpStatus.UNAUTHORIZED, 
                        "Invalid or expired JWT token", 
                        "The provided JWT token is invalid or has expired. Please obtain a new token.");
                    return;
                }
            } catch (UsernameNotFoundException e) {
                sendErrorResponse(response, HttpStatus.UNAUTHORIZED, 
                    "User not found or not associated with this brand", 
                    "The user in the JWT token is not found or not associated with the provided brand ID.");
                return;
            } catch (Exception e) {
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Authentication error", 
                    "An error occurred during authentication: " + e.getMessage());
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
    
    /**
     * Determines if the requested path is a public endpoint that doesn't require authentication
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/login") || 
               path.startsWith("/auth/token") || 
               path.startsWith("/auth/register") || 
               path.startsWith("/auth/google") ||
               path.startsWith("/auth/verify-email") ||
               path.startsWith("/auth/forgot-password") ||
               path.startsWith("/auth/check-email") ||
               path.startsWith("/test/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/hello");
    }
    
    /**
     * Sends a standardized error response with detailed information
     */
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String error, String message) 
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("status", status.value());
        errorDetails.put("error", error);
        errorDetails.put("message", message);
        errorDetails.put("path", "/api/protected"); // This would ideally be dynamic
        
        // Add information about required headers for authentication
        if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNAUTHORIZED) {
            Map<String, String> requiredHeaders = new HashMap<>();
            requiredHeaders.put("Authorization", "Bearer {jwt_token}");
            requiredHeaders.put("X-Brand-Id", "{brand_id}");
            errorDetails.put("requiredHeaders", requiredHeaders);
        }
        
        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}