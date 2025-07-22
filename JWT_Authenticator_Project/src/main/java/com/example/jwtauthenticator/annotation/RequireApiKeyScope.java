package com.example.jwtauthenticator.annotation;

import com.example.jwtauthenticator.enums.ApiKeyScope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify required API key scopes for accessing an endpoint.
 * This can be used at class level (applies to all methods) or method level (overrides class level).
 * 
 * Usage examples:
 * 
 * @RequireApiKeyScope(ApiKeyScope.READ_USERS)
 * public ResponseEntity<List<User>> getUsers() { ... }
 * 
 * @RequireApiKeyScope({ApiKeyScope.WRITE_USERS, ApiKeyScope.ADMIN_ACCESS})
 * public ResponseEntity<User> createUser(@RequestBody User user) { ... }
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireApiKeyScope {
    
    /**
     * Required scopes. The API key must have at least one of these scopes.
     * @return Array of required scopes
     */
    ApiKeyScope[] value();
    
    /**
     * Whether ALL specified scopes are required (true) or just ANY one of them (false).
     * Default is false (ANY - at least one scope required).
     * @return true if all scopes are required, false if any scope is sufficient
     */
    boolean requireAll() default false;
    
    /**
     * Custom error message when scope check fails.
     * @return Custom error message
     */
    String message() default "Insufficient API key permissions";
}