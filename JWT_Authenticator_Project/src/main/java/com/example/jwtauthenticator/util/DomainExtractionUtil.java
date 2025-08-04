package com.example.jwtauthenticator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for domain extraction and validation
 * Handles main domain extraction from subdomains and various domain scenarios
 */
@Component
@Slf4j
public class DomainExtractionUtil {

    // Common TLDs and their patterns
    private static final List<String> COMMON_TLDS = Arrays.asList(
        "com", "org", "net", "edu", "gov", "mil", "int",
        "co.uk", "co.in", "co.za", "com.au", "com.br",
        "io", "ai", "tech", "dev", "app", "cloud"
    );

    // Development domains that should not be processed
    private static final List<String> DEVELOPMENT_DOMAINS = Arrays.asList(
        "localhost", "127.0.0.1", "::1", "localhost.com", "local.dev"
    );

    // Pattern for valid domain format
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+" +
        "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$"
    );

    /**
     * Extract main domain from any domain (including subdomains)
     * Examples:
     * - dev.xamplyfy.com -> xamplyfy.com
     * - api.staging.xamplyfy.com -> xamplyfy.com
     * - xamplyfy.com -> xamplyfy.com
     * - localhost -> localhost (development domain)
     */
    public String extractMainDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException("Domain cannot be null or empty");
        }

        domain = domain.trim().toLowerCase();
        
        // Handle development domains
        if (isDevelopmentDomain(domain)) {
            log.debug("Development domain detected: {}", domain);
            return domain;
        }

        // Handle IP addresses
        if (isIpAddress(domain)) {
            log.debug("IP address detected: {}", domain);
            return domain;
        }

        // Validate domain format
        if (!isValidDomainFormat(domain)) {
            throw new IllegalArgumentException("Invalid domain format: " + domain);
        }

        // Extract main domain
        String mainDomain = extractMainDomainLogic(domain);
        log.debug("Extracted main domain: {} from: {}", mainDomain, domain);
        
        return mainDomain;
    }

    /**
     * Check if the given domain is a subdomain of the main domain
     */
    public boolean isSubdomainOf(String subdomain, String mainDomain) {
        if (subdomain == null || mainDomain == null) {
            return false;
        }

        subdomain = subdomain.trim().toLowerCase();
        mainDomain = mainDomain.trim().toLowerCase();

        // Same domain
        if (subdomain.equals(mainDomain)) {
            return false; // Not a subdomain, it's the same domain
        }

        // Check if subdomain ends with .mainDomain
        return subdomain.endsWith("." + mainDomain) && 
               subdomain.length() > mainDomain.length() + 1;
    }

    /**
     * Get subdomain prefix from full domain
     * Examples:
     * - dev.xamplyfy.com -> dev
     * - api.staging.xamplyfy.com -> api.staging
     */
    public String getSubdomainPrefix(String fullDomain, String mainDomain) {
        if (!isSubdomainOf(fullDomain, mainDomain)) {
            return null;
        }

        return fullDomain.substring(0, fullDomain.length() - mainDomain.length() - 1);
    }

    /**
     * Validate if domain format is correct
     */
    public boolean isValidDomainFormat(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }

        domain = domain.trim().toLowerCase();

        // Allow development domains
        if (isDevelopmentDomain(domain)) {
            return true;
        }

        // Allow IP addresses
        if (isIpAddress(domain)) {
            return true;
        }

        // Check against domain pattern
        return DOMAIN_PATTERN.matcher(domain).matches();
    }

    /**
     * Check if domain is a development domain
     */
    public boolean isDevelopmentDomain(String domain) {
        if (domain == null) return false;
        
        domain = domain.trim().toLowerCase();
        
        return DEVELOPMENT_DOMAINS.contains(domain) ||
               domain.startsWith("localhost") ||
               domain.endsWith(".local") ||
               domain.endsWith(".dev") ||
               domain.contains("127.0.0.1") ||
               domain.contains("::1");
    }

    /**
     * Check if domain is an IP address
     */
    public boolean isIpAddress(String domain) {
        if (domain == null) return false;
        
        // IPv4 pattern
        Pattern ipv4Pattern = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
            "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        );
        
        // IPv6 pattern (simplified)
        Pattern ipv6Pattern = Pattern.compile(
            "^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$"
        );
        
        return ipv4Pattern.matcher(domain).matches() || 
               ipv6Pattern.matcher(domain).matches();
    }

    /**
     * Generate subdomain pattern for API key
     * Examples:
     * - xamplyfy.com -> *.xamplyfy.com
     * - localhost -> localhost (no pattern for dev domains)
     */
    public String generateSubdomainPattern(String mainDomain) {
        if (isDevelopmentDomain(mainDomain) || isIpAddress(mainDomain)) {
            return mainDomain; // No wildcard for dev domains or IPs
        }
        
        return "*." + mainDomain;
    }

    /**
     * Validate subdomain against environment
     */
    public boolean isValidSubdomainForEnvironment(String subdomain, String environment) {
        if (subdomain == null || environment == null) {
            return false;
        }

        String subdomainPrefix = subdomain.toLowerCase();
        String env = environment.toLowerCase();

        return switch (env) {
            case "development" -> subdomainPrefix.matches("(dev|development|local|localhost).*");
            case "testing" -> subdomainPrefix.matches("(test|testing|qa|stage|staging).*");
            case "production" -> subdomainPrefix.matches("(prod|production|api|www|app)?.*");
            default -> true; // Allow any subdomain for unknown environments
        };
    }

    /**
     * Core logic for extracting main domain
     */
    private String extractMainDomainLogic(String domain) {
        String[] parts = domain.split("\\.");
        
        if (parts.length < 2) {
            return domain; // Single word domain (like localhost)
        }

        // Handle special TLDs (like co.uk, com.au)
        for (String tld : COMMON_TLDS) {
            if (domain.endsWith("." + tld)) {
                String[] tldParts = tld.split("\\.");
                int mainDomainParts = tldParts.length + 1; // domain + TLD parts
                
                if (parts.length >= mainDomainParts) {
                    StringBuilder mainDomain = new StringBuilder();
                    for (int i = parts.length - mainDomainParts; i < parts.length; i++) {
                        if (mainDomain.length() > 0) {
                            mainDomain.append(".");
                        }
                        mainDomain.append(parts[i]);
                    }
                    return mainDomain.toString();
                }
            }
        }

        // Default: last two parts (domain.tld)
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }

        return domain;
    }

    /**
     * Get all possible domain variations for validation
     */
    public List<String> getDomainVariations(String domain) {
        String mainDomain = extractMainDomain(domain);
        
        return Arrays.asList(
            domain,                                    // Original domain
            mainDomain,                               // Main domain
            generateSubdomainPattern(mainDomain),     // Wildcard pattern
            "www." + mainDomain,                      // WWW variant
            "api." + mainDomain,                      // API variant
            "app." + mainDomain                       // APP variant
        );
    }
}