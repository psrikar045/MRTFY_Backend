package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.exception.PlanLimitExceededException;
import com.example.jwtauthenticator.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for validating plan limits and restrictions
 * Enforces API key limits, domain limits, and usage quotas based on user plans
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlanValidationService {
    
    private final ApiKeyRepository apiKeyRepository;
    
    /**
     * Validate if user can create a new API key based on their plan
     */
    public void validateApiKeyCreation(User user) {
        UserPlan plan = user.getPlan();
        int currentKeyCount = getCurrentApiKeyCount(user.getId());
        
        log.debug("Validating API key creation for user '{}' with plan '{}'. Current keys: {}, Max allowed: {}", 
                user.getId(), plan, currentKeyCount, plan.getMaxApiKeys());
        
        if (plan.getMaxApiKeys() != -1 && currentKeyCount >= plan.getMaxApiKeys()) {
            throw new PlanLimitExceededException(
                String.format("API key limit exceeded for %s plan. Current: %d, Maximum allowed: %d. " +
                             "Upgrade your plan to create more API keys.",
                             plan.getDisplayName(), currentKeyCount, plan.getMaxApiKeys()),
                "API_KEY_LIMIT_EXCEEDED",
                plan,
                currentKeyCount,
                plan.getMaxApiKeys()
            );
        }
        
        log.info("API key creation validated for user '{}'. Keys: {}/{}", 
                user.getId(), currentKeyCount, plan.getMaxApiKeys() == -1 ? "unlimited" : plan.getMaxApiKeys());
    }
    
    /**
     * Validate if user can claim a new domain based on their plan
     */
    public void validateDomainClaim(User user, String domain) {
        UserPlan plan = user.getPlan();
        int currentDomainCount = getCurrentDomainCount(user.getId());
        
        log.debug("Validating domain claim for user '{}' with plan '{}'. Domain: '{}', Current domains: {}, Max allowed: {}", 
                user.getId(), plan, domain, currentDomainCount, plan.getMaxDomains());
        
        if (plan.getMaxDomains() != -1 && currentDomainCount >= plan.getMaxDomains()) {
            throw new PlanLimitExceededException(
                String.format("Domain limit exceeded for %s plan. Current: %d, Maximum allowed: %d. " +
                             "Upgrade your plan to claim more domains.",
                             plan.getDisplayName(), currentDomainCount, plan.getMaxDomains()),
                "DOMAIN_LIMIT_EXCEEDED",
                plan,
                currentDomainCount,
                plan.getMaxDomains()
            );
        }
        
        log.info("Domain claim validated for user '{}'. Domains: {}/{}", 
                user.getId(), currentDomainCount, plan.getMaxDomains() == -1 ? "unlimited" : plan.getMaxDomains());
    }
    
    /**
     * Validate if user can make API calls based on their monthly quota
     */
    public void validateApiCallQuota(User user, int currentMonthlyUsage) {
        UserPlan plan = user.getPlan();
        
        log.debug("Validating API call quota for user '{}' with plan '{}'. Current usage: {}, Monthly limit: {}", 
                user.getId(), plan, currentMonthlyUsage, plan.getMonthlyApiCalls());
        
        if (plan.getMonthlyApiCalls() != -1 && currentMonthlyUsage >= plan.getMonthlyApiCalls()) {
            throw new PlanLimitExceededException(
                String.format("Monthly API call quota exceeded for %s plan. Current usage: %d, Monthly limit: %d. " +
                             "Upgrade your plan or wait for next month's reset.",
                             plan.getDisplayName(), currentMonthlyUsage, plan.getMonthlyApiCalls()),
                "API_QUOTA_EXCEEDED",
                plan,
                currentMonthlyUsage,
                plan.getMonthlyApiCalls()
            );
        }
        
        log.debug("API call quota validated for user '{}'. Usage: {}/{}", 
                user.getId(), currentMonthlyUsage, plan.getMonthlyApiCalls() == -1 ? "unlimited" : plan.getMonthlyApiCalls());
    }
    
    /**
     * Validate if user can make API calls with grace period
     */
    public void validateApiCallQuotaWithGrace(User user, int currentMonthlyUsage) {
        UserPlan plan = user.getPlan();
        int graceLimit = plan.getGraceLimit("api_calls");
        
        log.debug("Validating API call quota with grace for user '{}'. Current usage: {}, Grace limit: {}", 
                user.getId(), currentMonthlyUsage, graceLimit);
        
        if (graceLimit != -1 && currentMonthlyUsage >= graceLimit) {
            throw new PlanLimitExceededException(
                String.format("Monthly API call quota exceeded (including grace period) for %s plan. " +
                             "Current usage: %d, Grace limit: %d. Upgrade your plan to continue.",
                             plan.getDisplayName(), currentMonthlyUsage, graceLimit),
                "API_GRACE_QUOTA_EXCEEDED",
                plan,
                currentMonthlyUsage,
                graceLimit
            );
        }
    }
    
    /**
     * Check if user is approaching their limits (warning threshold)
     */
    public PlanUsageWarnings checkUsageWarnings(User user) {
        UserPlan plan = user.getPlan();
        PlanUsageWarnings warnings = new PlanUsageWarnings();
        
        // Check API key usage (warn at 80%)
        int currentKeys = getCurrentApiKeyCount(user.getId());
        if (plan.getMaxApiKeys() != -1) {
            double keyUsagePercent = (double) currentKeys / plan.getMaxApiKeys() * 100;
            if (keyUsagePercent >= 80) {
                warnings.addApiKeyWarning(currentKeys, plan.getMaxApiKeys(), keyUsagePercent);
            }
        }
        
        // Check domain usage (warn at 80%)
        int currentDomains = getCurrentDomainCount(user.getId());
        if (plan.getMaxDomains() != -1) {
            double domainUsagePercent = (double) currentDomains / plan.getMaxDomains() * 100;
            if (domainUsagePercent >= 80) {
                warnings.addDomainWarning(currentDomains, plan.getMaxDomains(), domainUsagePercent);
            }
        }
        
        return warnings;
    }
    
    /**
     * Get current API key count for user
     */
    private int getCurrentApiKeyCount(String userId) {
        return apiKeyRepository.countByUserFkId(userId);
    }
    
    /**
     * Get current domain count for user (count unique main domains)
     */
    private int getCurrentDomainCount(String userId) {
        List<String> domains = apiKeyRepository.findByUserFkId(userId)
                .stream()
                .map(key -> key.getMainDomain() != null ? key.getMainDomain() : key.getRegisteredDomain())
                .filter(domain -> domain != null)
                .distinct()
                .collect(Collectors.toList());
        
        return domains.size();
    }
    
    /**
     * Get plan upgrade suggestions based on current usage
     */
    public PlanUpgradeSuggestion getUpgradeSuggestion(User user) {
        UserPlan currentPlan = user.getPlan();
        int currentKeys = getCurrentApiKeyCount(user.getId());
        int currentDomains = getCurrentDomainCount(user.getId());
        
        PlanUpgradeSuggestion suggestion = new PlanUpgradeSuggestion();
        suggestion.setCurrentPlan(currentPlan);
        suggestion.setCurrentApiKeys(currentKeys);
        suggestion.setCurrentDomains(currentDomains);
        
        // Check if user needs upgrade
        boolean needsUpgrade = false;
        String reason = "";
        
        if (currentPlan.getMaxApiKeys() != -1 && currentKeys >= currentPlan.getMaxApiKeys()) {
            needsUpgrade = true;
            reason += "API key limit reached. ";
        }
        
        if (currentPlan.getMaxDomains() != -1 && currentDomains >= currentPlan.getMaxDomains()) {
            needsUpgrade = true;
            reason += "Domain limit reached. ";
        }
        
        if (needsUpgrade) {
            UserPlan suggestedPlan = currentPlan.getNextPlan();
            suggestion.setSuggestedPlan(suggestedPlan);
            suggestion.setReason(reason.trim());
            suggestion.setNeedsUpgrade(true);
        }
        
        return suggestion;
    }
    
    /**
     * Class for holding usage warnings
     */
    public static class PlanUsageWarnings {
        private boolean hasWarnings = false;
        private String apiKeyWarning;
        private String domainWarning;
        private String quotaWarning;
        
        public void addApiKeyWarning(int current, int max, double percentage) {
            this.hasWarnings = true;
            this.apiKeyWarning = String.format("API key usage at %.1f%% (%d/%d). Consider upgrading soon.", 
                    percentage, current, max);
        }
        
        public void addDomainWarning(int current, int max, double percentage) {
            this.hasWarnings = true;
            this.domainWarning = String.format("Domain usage at %.1f%% (%d/%d). Consider upgrading soon.", 
                    percentage, current, max);
        }
        
        public void addQuotaWarning(int current, int max, double percentage) {
            this.hasWarnings = true;
            this.quotaWarning = String.format("API quota usage at %.1f%% (%d/%d). Consider upgrading soon.", 
                    percentage, current, max);
        }
        
        // Getters
        public boolean hasWarnings() { return hasWarnings; }
        public String getApiKeyWarning() { return apiKeyWarning; }
        public String getDomainWarning() { return domainWarning; }
        public String getQuotaWarning() { return quotaWarning; }
    }
    
    /**
     * Class for holding upgrade suggestions
     */
    public static class PlanUpgradeSuggestion {
        private UserPlan currentPlan;
        private UserPlan suggestedPlan;
        private boolean needsUpgrade;
        private String reason;
        private int currentApiKeys;
        private int currentDomains;
        
        // Getters and setters
        public UserPlan getCurrentPlan() { return currentPlan; }
        public void setCurrentPlan(UserPlan currentPlan) { this.currentPlan = currentPlan; }
        
        public UserPlan getSuggestedPlan() { return suggestedPlan; }
        public void setSuggestedPlan(UserPlan suggestedPlan) { this.suggestedPlan = suggestedPlan; }
        
        public boolean isNeedsUpgrade() { return needsUpgrade; }
        public void setNeedsUpgrade(boolean needsUpgrade) { this.needsUpgrade = needsUpgrade; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public int getCurrentApiKeys() { return currentApiKeys; }
        public void setCurrentApiKeys(int currentApiKeys) { this.currentApiKeys = currentApiKeys; }
        
        public int getCurrentDomains() { return currentDomains; }
        public void setCurrentDomains(int currentDomains) { this.currentDomains = currentDomains; }
    }
}