package com.example.jwtauthenticator.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.example.jwtauthenticator.entity.ApiKey;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.RateLimitTier;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.UserRepository;
import com.example.jwtauthenticator.util.DomainExtractionUtil;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced API key validation service that integrates plan-based restrictions,
 * domain validation, quota checking, and usage tracking
 */
@Service
@Slf4j
public class EnhancedApiKeyValidationService {
    
    private final MonthlyUsageTrackingService monthlyUsageService;
    private final PlanValidationService planValidationService;
    private final DomainManagementService domainManagementService;
    private final DomainExtractionUtil domainExtractionUtil;
    private final UserRepository userRepository;
    
    // Optional services for logging and stats
    private final UsageStatsService usageStatsService;
    private final RequestLoggingService requestLoggingService;
    
    public EnhancedApiKeyValidationService(
            MonthlyUsageTrackingService monthlyUsageService,
            PlanValidationService planValidationService,
            DomainManagementService domainManagementService,
            DomainExtractionUtil domainExtractionUtil,
            UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UsageStatsService usageStatsService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) RequestLoggingService requestLoggingService) {
        this.monthlyUsageService = monthlyUsageService;
        this.planValidationService = planValidationService;
        this.domainManagementService = domainManagementService;
        this.domainExtractionUtil = domainExtractionUtil;
        this.userRepository = userRepository;
        this.usageStatsService = usageStatsService;
        this.requestLoggingService = requestLoggingService;
    }
    
    /**
     * Comprehensive API key validation for secure endpoints
     * Validates: API key, domain, plan limits, quotas, and rate limits
     */
    public ApiKeyValidationResult validateApiKeyForRequest(ApiKey apiKey, User user, 
                                                          HttpServletRequest request, 
                                                          String endpoint) {
        long startTime = System.currentTimeMillis();
        String requestDomain = extractRequestDomain(request);
        
        log.info("Starting comprehensive API key validation for key '{}', user '{}', domain '{}', endpoint '{}'", 
                apiKey.getId(), user.getId(), requestDomain, endpoint);
        
        ApiKeyValidationResult result = new ApiKeyValidationResult();
        result.setApiKey(apiKey);
        result.setUser(user);
        result.setRequestDomain(requestDomain);
        result.setEndpoint(endpoint);
        result.setValidationStartTime(startTime);
        
        try {
            // Step 1: Basic API key validation
            if (!validateBasicApiKey(apiKey, result)) {
                return result;
            }
            
            // Step 2: Domain validation
            if (!validateDomainAccess(apiKey, requestDomain, request, result)) {
                return result;
            }
            
            // Step 3: Plan-based quota validation
            if (!validatePlanQuota(apiKey, user, result)) {
                return result;
            }
            
            // Step 4: Rate limit validation (separate from quota)
            if (!validateRateLimit(apiKey, user, result)) {
                return result;
            }
            
            // Step 5: Environment-specific validation
            if (!validateEnvironmentAccess(apiKey, request, result)) {
                return result;
            }
            
            // All validations passed
            result.setSuccess(true);
            result.setMessage("API key validation successful");
            
            // Record successful validation
            recordSuccessfulValidation(apiKey, user, requestDomain, endpoint);
            
            long validationTime = System.currentTimeMillis() - startTime;
            log.info("✅ API key validation successful for key '{}' - Domain: '{}', Time: {}ms", 
                    apiKey.getId(), requestDomain, validationTime);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ API key validation error for key '{}': {}", apiKey.getId(), e.getMessage(), e);
            
            result.setSuccess(false);
            result.setErrorCode("VALIDATION_ERROR");
            result.setMessage("Internal validation error: " + e.getMessage());
            
            return result;
        }
    }
    
    /**
     * Validate basic API key properties
     */
    private boolean validateBasicApiKey(ApiKey apiKey, ApiKeyValidationResult result) {
        // Check if API key is active
        if (!apiKey.getIsActive()) {
            result.setSuccess(false);
            result.setErrorCode("API_KEY_INACTIVE");
            result.setMessage("API key is inactive");
            log.warn("API key validation failed: key '{}' is inactive", apiKey.getId());
            return false;
        }
        
        // Check expiration
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
            result.setSuccess(false);
            result.setErrorCode("API_KEY_EXPIRED");
            result.setMessage("API key has expired");
            log.warn("API key validation failed: key '{}' expired at {}", apiKey.getId(), apiKey.getExpiresAt());
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate domain access with enhanced domain management
     */
    private boolean validateDomainAccess(ApiKey apiKey, String requestDomain, 
                                       HttpServletRequest request, ApiKeyValidationResult result) {
        
        if (requestDomain == null) {
            // Try IP fallback
            String clientIp = extractClientIp(request);
            if (clientIp != null && validateIpAccess(apiKey, clientIp)) {
                result.setRequestDomain("IP:" + clientIp);
                result.setMatchedDomain(clientIp);
                result.setValidationType("IP_FALLBACK");
                return true;
            }
            
            result.setSuccess(false);
            result.setErrorCode("NO_DOMAIN_HEADER");
            result.setMessage("No domain header found and IP not in allowed list");
            return false;
        }
        
        // Use enhanced domain validation
        if (!apiKey.isValidDomain(requestDomain)) {
            result.setSuccess(false);
            result.setErrorCode("DOMAIN_NOT_ALLOWED");
            result.setMessage(String.format("Domain '%s' is not allowed for this API key", requestDomain));
            result.setRequestDomain(requestDomain);
            
            log.warn("Domain validation failed for API key '{}': domain '{}' not in allowed list", 
                    apiKey.getId(), requestDomain);
            return false;
        }
        
        result.setMatchedDomain(requestDomain);
        result.setValidationType("DOMAIN_MATCH");
        return true;
    }
    
    /**
     * Validate plan-based monthly quota
     */
    private boolean validatePlanQuota(ApiKey apiKey, User user, ApiKeyValidationResult result) {
        try {
            // Check if user has exceeded grace period
            if (monthlyUsageService.isGraceExceeded(apiKey.getId(), user)) {
                result.setSuccess(false);
                result.setErrorCode("QUOTA_EXCEEDED_GRACE");
                result.setMessage(String.format(
                    "API quota exceeded (including grace period) for your %s plan. " +
                    "Current usage exceeds the allowed limit. Upgrade your plan to continue.",
                    user.getPlan().getDisplayName()
                ));
                result.setUpgradeRequired(true);
                result.setSuggestedPlan(user.getPlan().getNextPlan());
                
                // Record quota exceeded call
                monthlyUsageService.recordQuotaExceededCall(apiKey.getId(), user.getId());
                
                log.warn("Grace quota exceeded for API key '{}', user plan: {}", 
                        apiKey.getId(), user.getPlan());
                return false;
            }
            
            // Check regular quota (will show warning if exceeded but allow with grace)
            if (monthlyUsageService.isQuotaExceeded(apiKey.getId(), user)) {
                int remainingGrace = monthlyUsageService.getRemainingGraceCalls(apiKey.getId(), user.getId());
                
                result.setQuotaWarning(String.format(
                    "Monthly quota exceeded for %s plan. %d grace calls remaining. " +
                    "Consider upgrading to avoid service interruption.",
                    user.getPlan().getDisplayName(), remainingGrace
                ));
                
                log.info("Quota exceeded but within grace period for API key '{}': {} grace calls remaining", 
                        apiKey.getId(), remainingGrace);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error validating plan quota for API key '{}': {}", apiKey.getId(), e.getMessage(), e);
            // Allow request to proceed if quota check fails
            return true;
        }
    }
    
    /**
     * Validate rate limits (separate from monthly quotas)
     */
    private boolean validateRateLimit(ApiKey apiKey, User user, ApiKeyValidationResult result) {
        // Rate limit validation would be handled by ProfessionalRateLimitService
        // This is a placeholder for additional rate limit logic if needed
        
        RateLimitTier rateLimitTier = RateLimitTier.fromUserPlan(user.getPlan());
        result.setRateLimitTier(rateLimitTier);
        
        return true; // Let ProfessionalRateLimitService handle the actual rate limiting
    }
    
    /**
     * Validate environment-specific access
     */
    private boolean validateEnvironmentAccess(ApiKey apiKey, HttpServletRequest request, 
                                            ApiKeyValidationResult result) {
        
        // Check if subdomain matches environment expectations
        if (apiKey.getSubdomainPattern() != null && result.getRequestDomain() != null) {
            String requestDomain = result.getRequestDomain();
            
            if (requestDomain.startsWith("IP:")) {
                return true; // Skip environment validation for IP access
            }
            
            // Extract subdomain prefix
            String mainDomain = apiKey.getMainDomain();
            if (mainDomain != null && domainExtractionUtil.isSubdomainOf(requestDomain, mainDomain)) {
                String subdomainPrefix = domainExtractionUtil.getSubdomainPrefix(requestDomain, mainDomain);
                
                if (subdomainPrefix != null && 
                    !apiKey.getEnvironment().isValidSubdomainPrefix(subdomainPrefix)) {
                    
                    result.setEnvironmentWarning(String.format(
                        "Subdomain prefix '%s' may not be suitable for %s environment", 
                        subdomainPrefix, apiKey.getEnvironment()
                    ));
                    
                    log.info("Environment mismatch warning for API key '{}': subdomain '{}' in {} environment", 
                            apiKey.getId(), subdomainPrefix, apiKey.getEnvironment());
                }
            }
        }
        
        return true;
    }
    
    /**
     * Record successful API call for usage tracking
     */
    public void recordApiCall(ApiKey apiKey, User user, boolean successful, String endpoint) {
        try {
            // Record in monthly usage tracking
            monthlyUsageService.recordApiCall(apiKey.getId(), user.getId(), successful);
            
            // Record in usage stats if service is available
            if (usageStatsService != null) {
                // usageStatsService.recordApiCall(apiKey.getId(), successful);
                log.debug("Usage stats service available - would record API call for key '{}'", apiKey.getId());
            }
            
            // Record detailed request log if service is available
            if (requestLoggingService != null) {
                // requestLoggingService.logApiRequest(apiKey.getId(), endpoint, successful);
                log.debug("Request logging service available - would log API request for key '{}'", apiKey.getId());
            }
            
        } catch (Exception e) {
            log.error("Error recording API call for key '{}': {}", apiKey.getId(), e.getMessage(), e);
            // Don't fail the request if logging fails
        }
    }
    
    /**
     * Record successful validation (for analytics)
     */
    private void recordSuccessfulValidation(ApiKey apiKey, User user, String domain, String endpoint) {
        try {
            if (usageStatsService != null) {
                // usageStatsService.recordValidation(apiKey.getId(), domain, endpoint);
                log.debug("Usage stats service available - would record validation for key '{}'", apiKey.getId());
            }
        } catch (Exception e) {
            log.error("Error recording validation for key '{}': {}", apiKey.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Extract request domain from HTTP request
     */
    private String extractRequestDomain(HttpServletRequest request) {
        // Try Origin header first
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.trim().isEmpty()) {
            return extractDomainFromUrl(origin);
        }
        
        // Try Referer header
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.trim().isEmpty()) {
            return extractDomainFromUrl(referer);
        }
        
        // Try Host header
        String host = request.getHeader("Host");
        if (host != null && !host.trim().isEmpty()) {
            return host.split(":")[0]; // Remove port if present
        }
        
        return null;
    }
    
    /**
     * Extract domain from URL
     */
    private String extractDomainFromUrl(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                url = url.substring(url.indexOf("://") + 3);
            }
            
            int slashIndex = url.indexOf('/');
            if (slashIndex > 0) {
                url = url.substring(0, slashIndex);
            }
            
            int colonIndex = url.indexOf(':');
            if (colonIndex > 0) {
                url = url.substring(0, colonIndex);
            }
            
            return url.toLowerCase();
        } catch (Exception e) {
            log.warn("Error extracting domain from URL '{}': {}", url, e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract client IP from request
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp.trim();
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Validate IP access for API key
     */
    private boolean validateIpAccess(ApiKey apiKey, String clientIp) {
        if (apiKey.getAllowedIps() == null || apiKey.getAllowedIps().trim().isEmpty()) {
            return false;
        }
        
        return apiKey.getAllowedIpsAsList().contains(clientIp);
    }
    
    /**
     * Result class for API key validation
     */
    public static class ApiKeyValidationResult {
        private boolean success;
        private String message;
        private String errorCode;
        private ApiKey apiKey;
        private User user;
        private String requestDomain;
        private String matchedDomain;
        private String endpoint;
        private String validationType;
        private RateLimitTier rateLimitTier;
        private boolean upgradeRequired;
        private UserPlan suggestedPlan;
        private String quotaWarning;
        private String environmentWarning;
        private long validationStartTime;
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        
        public ApiKey getApiKey() { return apiKey; }
        public void setApiKey(ApiKey apiKey) { this.apiKey = apiKey; }
        
        public User getUser() { return user; }
        public void setUser(User user) { this.user = user; }
        
        public String getRequestDomain() { return requestDomain; }
        public void setRequestDomain(String requestDomain) { this.requestDomain = requestDomain; }
        
        public String getMatchedDomain() { return matchedDomain; }
        public void setMatchedDomain(String matchedDomain) { this.matchedDomain = matchedDomain; }
        
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        
        public String getValidationType() { return validationType; }
        public void setValidationType(String validationType) { this.validationType = validationType; }
        
        public RateLimitTier getRateLimitTier() { return rateLimitTier; }
        public void setRateLimitTier(RateLimitTier rateLimitTier) { this.rateLimitTier = rateLimitTier; }
        
        public boolean isUpgradeRequired() { return upgradeRequired; }
        public void setUpgradeRequired(boolean upgradeRequired) { this.upgradeRequired = upgradeRequired; }
        
        public UserPlan getSuggestedPlan() { return suggestedPlan; }
        public void setSuggestedPlan(UserPlan suggestedPlan) { this.suggestedPlan = suggestedPlan; }
        
        public String getQuotaWarning() { return quotaWarning; }
        public void setQuotaWarning(String quotaWarning) { this.quotaWarning = quotaWarning; }
        
        public String getEnvironmentWarning() { return environmentWarning; }
        public void setEnvironmentWarning(String environmentWarning) { this.environmentWarning = environmentWarning; }
        
        public long getValidationStartTime() { return validationStartTime; }
        public void setValidationStartTime(long validationStartTime) { this.validationStartTime = validationStartTime; }
        
        public long getValidationDuration() {
            return System.currentTimeMillis() - validationStartTime;
        }
        
        public boolean hasWarnings() {
            return quotaWarning != null || environmentWarning != null;
        }
    }
}