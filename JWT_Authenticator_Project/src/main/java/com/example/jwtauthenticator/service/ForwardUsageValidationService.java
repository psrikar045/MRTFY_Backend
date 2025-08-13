package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.enums.UserPlan;
import com.example.jwtauthenticator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 🎯 Usage Validation Service for /forward endpoint
 * 
 * Validates API call limits based on user plans for JWT-authenticated requests
 * to ensure consistent behavior with /rivofeetch endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForwardUsageValidationService {
    
    private final UserRepository userRepository;
    private final StreamlinedUsageTracker usageTracker;
    
    /**
     * Validate API call limit for user based on their plan
     */
    public ValidationResult validateApiCallLimit(String userId, UserPlan plan) {
        try {
            // Get current month's usage across all user's API keys
            String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            int currentUsage = usageTracker.getCurrentMonthUsageForUser(userId, monthYear);
            
            int planLimit = plan.getMonthlyApiCalls();
            
            log.debug("Validating usage for user {} with plan {}: current={}, limit={}", 
                     userId, plan.getDisplayName(), currentUsage, planLimit);
            
            // Check if unlimited (BUSINESS plan)
            if (planLimit == -1) {
                return ValidationResult.allowed();
            }
            
            // Check if limit exceeded
            if (currentUsage >= planLimit) {
                String message = String.format(
                    "API call limit exceeded for %s plan (%d/%d calls used). Upgrade your plan for more calls.", 
                    plan.getDisplayName(), currentUsage, planLimit
                );
                return ValidationResult.denied(message);
            }
            
            return ValidationResult.allowed();
            
        } catch (Exception e) {
            log.error("Error validating API call limit for user {}: {}", userId, e.getMessage());
            // Fail open - allow the request but log the error
            return ValidationResult.allowed();
        }
    }
    
    /**
     * Get current usage for a user (includes both API key and JWT usage)
     */
    public int getCurrentUsage(String userId) {
        try {
            String monthYear = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            // StreamlinedUsageTracker.getCurrentMonthUsageForUser includes both API key and JWT usage
            // because JWT requests are now tracked in api_key_monthly_usage with virtual JWT API keys
            return usageTracker.getCurrentMonthUsageForUser(userId, monthYear);
        } catch (Exception e) {
            log.error("Error getting current usage for user {}: {}", userId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get user plan for a user ID
     */
    public UserPlan getUserPlan(String userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                UserPlan plan = userOpt.get().getPlan();
                return plan != null ? plan : UserPlan.FREE; // Default to FREE
            }
            return UserPlan.FREE; // Default for unknown users
        } catch (Exception e) {
            log.error("Error getting user plan for user {}: {}", userId, e.getMessage());
            return UserPlan.FREE; // Safe default
        }
    }
    
    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean allowed;
        private final String reason;
        
        private ValidationResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static ValidationResult allowed() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult denied(String reason) {
            return new ValidationResult(false, reason);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
    }
}