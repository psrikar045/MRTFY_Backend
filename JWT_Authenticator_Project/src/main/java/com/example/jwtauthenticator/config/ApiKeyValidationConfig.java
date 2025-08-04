package com.example.jwtauthenticator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration for API Key validation strategies and fallback options.
 * This allows flexible handling of different scenarios where domain headers might be missing.
 */
@Configuration
@ConfigurationProperties(prefix = "app.api-key.validation")
@Data
public class ApiKeyValidationConfig {

    /**
     * Enable/disable domain validation entirely
     */
    private boolean domainValidationEnabled = true;

    /**
     * Fallback strategies when domain headers are missing
     */
    private FallbackStrategies fallback = new FallbackStrategies();

    /**
     * Development/testing specific settings
     */
    private DevelopmentSettings development = new DevelopmentSettings();

    /**
     * Server-to-server API key settings
     */
    private ServerToServerSettings serverToServer = new ServerToServerSettings();

    @Data
    public static class FallbackStrategies {
        /**
         * Enable IP-based validation when domain headers are missing
         */
        private boolean enableIpFallback = true;

        /**
         * Enable server-to-server bypass for appropriate API keys
         */
        private boolean enableServerToServerBypass = true;

        /**
         * Enable special scope bypass (FULL_ACCESS, ADMIN_ACCESS, etc.)
         */
        private boolean enableSpecialScopesBypass = true;

        /**
         * Enable development environment bypass for testing
         */
        private boolean enableDevelopmentBypass = true;

        /**
         * Strict mode - if true, no fallbacks are allowed
         */
        private boolean strictMode = false;
    }

    @Data
    public static class DevelopmentSettings {
        /**
         * Allow domain-less access in development environments
         */
        private boolean allowDomainlessAccess = true;

        /**
         * Profiles that are considered development environments
         */
        private String[] developmentProfiles = {"dev", "test", "local", "development"};

        /**
         * API key name patterns that indicate testing keys
         */
        private String[] testingKeyPatterns = {"test", "dev", "demo", "local"};
    }

    @Data
    public static class ServerToServerSettings {
        /**
         * Enable automatic detection of server-to-server API keys
         */
        private boolean enableAutoDetection = true;

        /**
         * Scope patterns that indicate server-to-server usage
         */
        private String[] serverScopes = {"SERVER", "S2S", "BACKEND", "SERVICE", "SYSTEM"};

        /**
         * Name patterns that indicate server-to-server usage
         */
        private String[] serverNamePatterns = {"server", "backend", "service", "cron", "job", "worker"};

        /**
         * Prefix patterns that indicate server-to-server usage
         */
        private String[] serverPrefixes = {"srv", "svc", "s2s", "sys"};
    }

    /**
     * Check if current environment is development
     */
    public boolean isDevelopmentEnvironment() {
        String activeProfile = System.getProperty("spring.profiles.active", "");
        String env = System.getenv("ENVIRONMENT");
        
        for (String devProfile : development.developmentProfiles) {
            if (activeProfile.contains(devProfile) || 
                (env != null && env.toLowerCase().contains(devProfile))) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if API key name indicates testing usage
     */
    public boolean isTestingApiKey(String keyName, String prefix) {
        if (keyName == null && prefix == null) {
            return false;
        }
        
        String lowerKeyName = keyName != null ? keyName.toLowerCase() : "";
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";
        
        for (String pattern : development.testingKeyPatterns) {
            if (lowerKeyName.contains(pattern) || lowerPrefix.contains(pattern)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if API key indicates server-to-server usage
     */
    public boolean isServerToServerApiKey(String keyName, String prefix, java.util.List<String> scopes) {
        if (!serverToServer.enableAutoDetection) {
            return false;
        }
        
        // Check scopes
        if (scopes != null) {
            for (String scope : scopes) {
                for (String serverScope : serverToServer.serverScopes) {
                    if (scope.toUpperCase().contains(serverScope)) {
                        return true;
                    }
                }
            }
        }
        
        // Check name patterns
        if (keyName != null) {
            String lowerKeyName = keyName.toLowerCase();
            for (String pattern : serverToServer.serverNamePatterns) {
                if (lowerKeyName.contains(pattern)) {
                    return true;
                }
            }
        }
        
        // Check prefix patterns
        if (prefix != null) {
            String lowerPrefix = prefix.toLowerCase();
            for (String pattern : serverToServer.serverPrefixes) {
                if (lowerPrefix.contains(pattern)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}