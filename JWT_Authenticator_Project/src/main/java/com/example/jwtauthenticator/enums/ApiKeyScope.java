package com.example.jwtauthenticator.enums;

/**
 * Defines the available scopes (permissions) that can be assigned to API keys.
 * Each scope represents a specific permission level for accessing API endpoints.
 */
public enum ApiKeyScope {
    
    // ===== READ PERMISSIONS =====
    READ_USERS("user.read", "Read user information and profiles"),
    READ_BRANDS("brand.read", "Read brand information and assets"),
    READ_CATEGORIES("category.read", "Read category hierarchy and information"),
    READ_API_KEYS("apikey.read", "Read own API key information"),
    
    // ===== WRITE PERMISSIONS =====
    WRITE_USERS("user.write", "Create and update user information"),
    WRITE_BRANDS("brand.write", "Create and update brand information"),
    WRITE_CATEGORIES("category.write", "Create and update categories"),
    
    // ===== DELETE PERMISSIONS =====
    DELETE_USERS("user.delete", "Delete user accounts"),
    DELETE_BRANDS("brand.delete", "Delete brands and assets"),
    DELETE_CATEGORIES("category.delete", "Delete categories"),
    
    // ===== API KEY MANAGEMENT =====
    MANAGE_API_KEYS("apikey.manage", "Full API key management capabilities"),
    REVOKE_API_KEYS("apikey.revoke", "Revoke and deactivate API keys"),
    
    // ===== ADMIN PERMISSIONS =====
    ADMIN_ACCESS("admin.access", "Access administrative functions"),
    SYSTEM_MONITOR("system.monitor", "Monitor system health and metrics"),
    
    // ===== SPECIAL PERMISSIONS =====
    FULL_ACCESS("*", "Complete system access (super admin equivalent)"),
    
    // ===== BUSINESS API PERMISSIONS =====
    BUSINESS_READ("business.read", "Read business-related data via API"),
    BUSINESS_WRITE("business.write", "Write business-related data via API");
    
    private final String permission;
    private final String description;
    
    ApiKeyScope(String permission, String description) {
        this.permission = permission;
        this.description = description;
    }
    
    public String getPermission() {
        return permission;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Convert a comma-separated string of scopes to an array of ApiKeyScope enums.
     * @param scopesString Comma-separated scope string (e.g., "READ_USERS,WRITE_BRANDS")
     * @return Array of ApiKeyScope enums
     */
    public static ApiKeyScope[] fromString(String scopesString) {
        if (scopesString == null || scopesString.trim().isEmpty()) {
            return new ApiKeyScope[0];
        }
        
        String[] scopeNames = scopesString.split(",");
        ApiKeyScope[] scopes = new ApiKeyScope[scopeNames.length];
        
        for (int i = 0; i < scopeNames.length; i++) {
            try {
                scopes[i] = ApiKeyScope.valueOf(scopeNames[i].trim());
            } catch (IllegalArgumentException e) {
                // Handle invalid scope names gracefully
                throw new IllegalArgumentException("Invalid API key scope: " + scopeNames[i]);
            }
        }
        
        return scopes;
    }
    
    /**
     * Convert an array of ApiKeyScope enums to a comma-separated string.
     * @param scopes Array of ApiKeyScope enums
     * @return Comma-separated scope string
     */
    public static String toString(ApiKeyScope[] scopes) {
        if (scopes == null || scopes.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scopes.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(scopes[i].name());
        }
        
        return sb.toString();
    }
    
    /**
     * Check if a scope has permission for a specific operation.
     * @param targetScope The scope to check against
     * @return true if this scope includes the target scope permission
     */
    public boolean hasPermission(ApiKeyScope targetScope) {
        // FULL_ACCESS has all permissions
        if (this == FULL_ACCESS) {
            return true;
        }
        
        // ADMIN_ACCESS has most permissions except FULL_ACCESS
        if (this == ADMIN_ACCESS && targetScope != FULL_ACCESS) {
            return true;
        }
        
        // Exact match
        return this == targetScope;
    }
}