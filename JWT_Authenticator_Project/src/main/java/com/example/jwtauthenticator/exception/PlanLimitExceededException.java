package com.example.jwtauthenticator.exception;

import com.example.jwtauthenticator.enums.UserPlan;

/**
 * Exception thrown when user exceeds their plan limits
 */
public class PlanLimitExceededException extends RuntimeException {
    
    private final String errorCode;
    private final UserPlan userPlan;
    private final int currentUsage;
    private final int limit;
    
    public PlanLimitExceededException(String message, String errorCode, UserPlan userPlan, int currentUsage, int limit) {
        super(message);
        this.errorCode = errorCode;
        this.userPlan = userPlan;
        this.currentUsage = currentUsage;
        this.limit = limit;
    }
    
    public PlanLimitExceededException(String message) {
        super(message);
        this.errorCode = "PLAN_LIMIT_EXCEEDED";
        this.userPlan = null;
        this.currentUsage = 0;
        this.limit = 0;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public UserPlan getUserPlan() {
        return userPlan;
    }
    
    public int getCurrentUsage() {
        return currentUsage;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public String getUpgradeMessage() {
        if (userPlan == null) {
            return "Please upgrade your plan to continue.";
        }
        
        UserPlan nextPlan = userPlan.getNextPlan();
        if (nextPlan == userPlan) {
            return "You are already on the highest plan.";
        }
        
        return String.format("Upgrade to %s plan (%s) to get higher limits.", 
                nextPlan.getDisplayName(), nextPlan.getFormattedPrice());
    }
}