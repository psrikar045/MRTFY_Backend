package com.example.jwtauthenticator.security;

import com.example.jwtauthenticator.annotation.RequireApiKeyScope;
import com.example.jwtauthenticator.enums.ApiKeyScope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

/**
 * AOP interceptor for handling @RequireApiKeyScope annotations.
 * This intercepts method calls and checks if the current authentication has the required scopes.
 */
@Aspect
@Component
@Order(1) // Execute before other security aspects
@Slf4j
public class ApiKeyScopeMethodInterceptor {
    
    @Around("@annotation(com.example.jwtauthenticator.annotation.RequireApiKeyScope) || " +
            "@within(com.example.jwtauthenticator.annotation.RequireApiKeyScope)")
    public Object checkApiKeyScope(ProceedingJoinPoint joinPoint) throws Throwable {
        
        // Get the method being called
        Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
        
        // Check for method-level annotation first, then class-level
        RequireApiKeyScope scopeAnnotation = method.getAnnotation(RequireApiKeyScope.class);
        if (scopeAnnotation == null) {
            scopeAnnotation = method.getDeclaringClass().getAnnotation(RequireApiKeyScope.class);
        }
        
        if (scopeAnnotation == null) {
            // No scope annotation found, proceed normally
            return joinPoint.proceed();
        }
        
        // Get current authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated request to scope-protected method: {}.{}", 
                    method.getDeclaringClass().getSimpleName(), method.getName());
            throw new AccessDeniedException("Authentication required");
        }
        
        // Check if authentication is API key based
        if (!(authentication instanceof ApiKeyAuthentication)) {
            // For non-API key authentication (e.g., JWT), we might want to allow based on roles
            log.debug("Non-API key authentication accessing scope-protected method: {}.{}", 
                     method.getDeclaringClass().getSimpleName(), method.getName());
            
            // Check if user has admin role (JWT-based authentication)
            boolean hasAdminRole = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
                
            if (hasAdminRole) {
                log.debug("Admin user accessing scope-protected method - allowed");
                return joinPoint.proceed();
            }
            
            // Non-admin JWT users don't have scope access
            throw new AccessDeniedException("API key required for this operation");
        }
        
        ApiKeyAuthentication apiKeyAuth = (ApiKeyAuthentication) authentication;
        ApiKeyScope[] requiredScopes = scopeAnnotation.value();
        boolean requireAll = scopeAnnotation.requireAll();
        String customMessage = scopeAnnotation.message();
        
        // Check scope permissions
        boolean hasPermission;
        if (requireAll) {
            hasPermission = apiKeyAuth.hasAllScopes(requiredScopes);
        } else {
            hasPermission = apiKeyAuth.hasAnyScope(requiredScopes);
        }
        
        if (!hasPermission) {
            log.warn("API key '{}' (user: {}) attempted to access method {}.{} without required scopes. " +
                    "Required: {}, Has: {}", 
                    apiKeyAuth.getName(), 
                    apiKeyAuth.getUserId(),
                    method.getDeclaringClass().getSimpleName(), 
                    method.getName(),
                    java.util.Arrays.toString(requiredScopes),
                    java.util.Arrays.toString(apiKeyAuth.getScopes()));
                    
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, customMessage);
        }
        
        log.debug("API key '{}' authorized to access method {}.{}", 
                 apiKeyAuth.getName(), 
                 method.getDeclaringClass().getSimpleName(), 
                 method.getName());
        
        return joinPoint.proceed();
    }
}