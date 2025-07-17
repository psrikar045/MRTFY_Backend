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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter that intercepts all requests to validate JWT tokens.
 * This filter ensures that valid JWT tokens are present in the Authorization header
 * for protected endpoints.
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
        // No longer requiring X-Brand-Id header
        final String brandId = "default"; // Use a default brand ID
        final String requestPath = request.getRequestURI();
        final String contextPath = request.getContextPath();
        
        // Log request details for debugging
        System.out.println("Request URI: " + requestPath);
        System.out.println("Context Path: " + contextPath);
        System.out.println("Servlet Path: " + request.getServletPath());
        
        // Skip authentication for public endpoints
        if (isPublicEndpoint(requestPath)) {
            System.out.println("Public endpoint detected, skipping authentication");
            chain.doFilter(request, response);
            return;
        }

        // For protected endpoints, check if Authorization header is present and valid
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            sendErrorResponse(request, response, HttpStatus.UNAUTHORIZED, 
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
            sendErrorResponse(request, response, HttpStatus.UNAUTHORIZED, 
                "Invalid JWT token", 
                "The provided JWT token is malformed or invalid");
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // No longer validating X-Brand-Id header

            try {
                UserDetails userDetails = this.jwtUserDetailsService.loadUserByUsernameAndBrandId(username, brandId);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    sendErrorResponse(request, response, HttpStatus.UNAUTHORIZED, 
                        "Invalid or expired JWT token", 
                        "The provided JWT token is invalid or has expired. Please obtain a new token.");
                    return;
                }
            } catch (UsernameNotFoundException e) {
                sendErrorResponse(request, response, HttpStatus.UNAUTHORIZED, 
                    "User not found or not associated with this brand", 
                    "The user in the JWT token is not found or not associated with the provided brand ID.");
                return;
            } catch (Exception e) {
                sendErrorResponse(request, response, HttpStatus.INTERNAL_SERVER_ERROR, 
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
        // Create a list of public endpoint patterns
        String[] publicEndpoints = {
            "/auth/login", 
            "/auth/token", 
            "/auth/login/email",
            "/auth/login/username",
            "/auth/register", 
            "/auth/forgot-password", 
            "/auth/reset-password",
            "/auth/refresh",
            "/auth/forward",
            "/auth/forgot-password-code", 
            "/auth/verify-reset-code",
            "/auth/check-email",
            "/auth/set-new-password",
            "/auth/google",
            "/auth/verify-email",
            "/auth/tfa",
            "/test",
            "/swagger-ui",
            "/swagger-ui.html",
            "/public-forward",
            "/v3/api-docs",
            "/api/id-generator/user-id/init-sequence",
            "/actuator/health",
            "/hello",
            "/api/brands/all",
            "/api/category/hierarchy"
        };
        
        // Log the incoming path for debugging
        System.out.println("Checking path: " + path);
        
        // Check if the path ends with any of the public endpoints
        for (String endpoint : publicEndpoints) {
            // Check if the path ends with the endpoint or if it's a subpath
            if (path.endsWith(endpoint) || 
                path.contains(endpoint + "/") || 
                path.matches(".*" + endpoint + "(\\.html)?$")) {
                System.out.println("Matched public endpoint: " + endpoint);
                return true;
            }
        }
        
        // Additional checks for specific patterns
        if (path.contains("/swagger-ui/") || 
            path.contains("/v3/api-docs/") || 
            path.contains("/webjars/") ||
            path.endsWith("/") || 
            path.endsWith("/myapp")) {
            System.out.println("Matched special pattern");
            return true;
        }
        
        System.out.println("Not a public endpoint");
        return false;
    }
    
    /**
     * Sends a standardized error response with detailed information
     */
    private void sendErrorResponse(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String error, String message) 
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        
        // Get the actual request path from the request attributes
        String requestPath = (String) request.getAttribute("javax.servlet.forward.request_uri");
        if (requestPath == null) {
            requestPath = request.getRequestURI();
        }
        
        // Log detailed information for debugging
        System.out.println("Sending error response for path: " + requestPath);
        System.out.println("Error: " + error);
        System.out.println("Message: " + message);
        
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", Instant.now().toString());
        errorDetails.put("status", status.value());
        errorDetails.put("error", error);
        errorDetails.put("message", message);
        errorDetails.put("path", requestPath);
        
        // Add request details for debugging
        Map<String, String> requestDetails = new HashMap<>();
        requestDetails.put("uri", request.getRequestURI());
        requestDetails.put("contextPath", request.getContextPath());
        requestDetails.put("servletPath", request.getServletPath());
        requestDetails.put("method", request.getMethod());
        errorDetails.put("requestDetails", requestDetails);
        
        // Add information about required headers for authentication
        if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNAUTHORIZED) {
            Map<String, String> requiredHeaders = new HashMap<>();
            requiredHeaders.put("Authorization", "Bearer {jwt_token}");
            // X-Brand-Id is no longer required
            errorDetails.put("requiredHeaders", requiredHeaders);
        }
        
        // Add information about public endpoints
        errorDetails.put("publicEndpoints", Arrays.asList(
            "/auth/login", "/auth/token", "/auth/register", 
            "/auth/forgot-password", "/swagger-ui", "/v3/api-docs"
        ));
        
        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}