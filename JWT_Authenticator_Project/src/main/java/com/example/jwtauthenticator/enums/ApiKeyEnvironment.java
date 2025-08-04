package com.example.jwtauthenticator.enums;

import lombok.Getter;

/**
 * Enum representing different API key environments
 * Used for environment-specific domain validation and access control
 */
@Getter
public enum ApiKeyEnvironment {
    PRODUCTION("production", "Production environment for live applications", 
               new String[]{"prod", "production", "api", "www", "app"}),
    
    TESTING("testing", "Testing environment for QA and staging", 
            new String[]{"test", "testing", "qa", "stage", "staging", "uat"}),
    
    DEVELOPMENT("development", "Development environment for local testing", 
                new String[]{"dev", "development", "local", "localhost"});

    private final String value;
    private final String description;
    private final String[] allowedSubdomainPrefixes;

    ApiKeyEnvironment(String value, String description, String[] allowedSubdomainPrefixes) {
        this.value = value;
        this.description = description;
        this.allowedSubdomainPrefixes = allowedSubdomainPrefixes;
    }

    /**
     * Check if a subdomain prefix is valid for this environment
     */
    public boolean isValidSubdomainPrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return true; // Empty prefix is valid for all environments
        }

        String lowerPrefix = prefix.toLowerCase().trim();
        
        for (String allowedPrefix : allowedSubdomainPrefixes) {
            if (lowerPrefix.startsWith(allowedPrefix)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Get suggested subdomain prefixes for this environment
     */
    public String[] getSuggestedPrefixes() {
        return allowedSubdomainPrefixes.clone();
    }

    /**
     * Get environment from string value
     */
    public static ApiKeyEnvironment fromValue(String value) {
        if (value == null) {
            return PRODUCTION; // Default
        }
        
        for (ApiKeyEnvironment env : values()) {
            if (env.value.equalsIgnoreCase(value.trim())) {
                return env;
            }
        }
        
        return PRODUCTION; // Default fallback
    }

    /**
     * Get default environment
     */
    public static ApiKeyEnvironment getDefault() {
        return PRODUCTION;
    }

    /**
     * Check if environment allows flexible domain validation
     */
    public boolean allowsFlexibleValidation() {
        return this == DEVELOPMENT; // Development allows more flexible validation
    }

    /**
     * Get environment-specific validation rules
     */
    public String getValidationRules() {
        return switch (this) {
            case PRODUCTION -> "Strict domain validation, exact matches required";
            case TESTING -> "Moderate validation, staging domains allowed";
            case DEVELOPMENT -> "Flexible validation, localhost and dev domains allowed";
        };
    }

    /**
     * Check if environment supports wildcard subdomains
     */
    public boolean supportsWildcardSubdomains() {
        return true; // All environments support wildcards
    }

    /**
     * Get recommended subdomain pattern for main domain
     */
    public String getRecommendedPattern(String mainDomain) {
        if (mainDomain == null || mainDomain.trim().isEmpty()) {
            return null;
        }

        return switch (this) {
            case PRODUCTION -> "*." + mainDomain; // Allow all subdomains
            case TESTING -> "*.staging." + mainDomain + ",*.test." + mainDomain;
            case DEVELOPMENT -> "*.dev." + mainDomain + ",localhost," + mainDomain;
        };
    }
}