package com.example.jwtauthenticator.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import com.example.jwtauthenticator.enums.UserPlan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", length = 11)
    private String id; // Primary key using DOMBR format (e.g., DOMBR000001)
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "user_code", unique = true)
    private String userCode; // Keeping for backward compatibility

    @NotBlank
    @Column(unique = true)
    private String username;

    @NotBlank
    private String password;

    @Email
    @NotBlank
    @Column(unique = true)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String location;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "tfa_secret")
    private String tfaSecret;

    @Column(name = "tfa_enabled")
    private boolean tfaEnabled;

    @Column(name = "brand_id")
    private String brandId;

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true; // Default to active

    @Column(name = "verification_token")
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider")
    private AuthProvider authProvider;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    // Plan-related fields
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false)
    private UserPlan plan = UserPlan.FREE; // Default to FREE plan
    
    @Column(name = "plan_started_at")
    private LocalDateTime planStartedAt;
    
    @Column(name = "monthly_reset_date")
    private LocalDate monthlyResetDate; // Date when monthly quotas reset (based on signup date)
    
    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt; // For trial periods or temporary upgrades

    @PrePersist
    protected void onCreate() {
        if (userId == null) {
            userId = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Initialize plan-related fields
        if (plan == null) {
            plan = UserPlan.FREE;
        }
        if (planStartedAt == null) {
            planStartedAt = LocalDateTime.now();
        }
        if (monthlyResetDate == null) {
            monthlyResetDate = LocalDate.now(); // Reset on signup date each month
        }
        
        // Initialize isActive field
        if (isActive == null) {
            isActive = true; // Default to active
        }
        
        // Only set emailVerified to false if it hasn't been explicitly set
        // This allows Google users to have emailVerified = true
        if (authProvider == null) {
            authProvider = AuthProvider.LOCAL; // Default to local auth
            emailVerified = false; // Default to false only for local auth
        }
        // For Google users, emailVerified should already be set to true by the builder
    }
    @Column(name = "futureV1")
    private String futureV1;
    @Column(name = "futureV2")
    private String futureV2;
    @Column(name = "futureV3")
    private String futureV3;
    @Column(name = "futureV4")
    private String futureV4;
    @Column(name = "futureV5")
    private String futureV5;
  
    @Column(name = "futureI1")
    private String futureI1;
    @Column(name = "futureI2")
    private String futureI2;
    @Column(name = "futureI3")
    private String futureI3;
    @Column(name = "futureI4")
    private String futureI4;
    @Column(name = "futureI5")
    private String futureI5;
    
    @Column(name = "futureT1")
    private Date futureT1;
    @Column(name = "futureT2")
    private Date futureT2;
    @Column(name = "futureT3")
    private Date futureT3;
    
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
          if (isActive == null) {
            isActive = true; // Default to active
        }
    }

    // Plan-related helper methods
    public boolean canCreateApiKey() {
        return plan.getMaxApiKeys() == -1 || getCurrentApiKeyCount() < plan.getMaxApiKeys();
    }
    
    public boolean canClaimDomain() {
        return plan.getMaxDomains() == -1 || getCurrentDomainCount() < plan.getMaxDomains();
    }
    
    public int getRemainingApiKeys() {
        if (plan.getMaxApiKeys() == -1) return Integer.MAX_VALUE;
        return Math.max(0, plan.getMaxApiKeys() - getCurrentApiKeyCount());
    }
    
    public int getRemainingDomains() {
        if (plan.getMaxDomains() == -1) return Integer.MAX_VALUE;
        return Math.max(0, plan.getMaxDomains() - getCurrentDomainCount());
    }
    
    public boolean isPlanExpired() {
        return planExpiresAt != null && planExpiresAt.isBefore(LocalDateTime.now());
    }
    
    public boolean needsMonthlyReset() {
        if (monthlyResetDate == null) return true;
        LocalDate now = LocalDate.now();
        LocalDate nextResetDate = monthlyResetDate.withMonth(now.getMonth().getValue()).withYear(now.getYear());
        
        // If we're past the reset date for this month
        return now.isAfter(nextResetDate) || now.isEqual(nextResetDate);
    }
    
    public LocalDate getNextResetDate() {
        if (monthlyResetDate == null) return LocalDate.now();
        
        LocalDate now = LocalDate.now();
        LocalDate nextReset = monthlyResetDate.withMonth(now.getMonth().getValue()).withYear(now.getYear());
        
        // If we've already passed this month's reset date, move to next month
        if (now.isAfter(nextReset)) {
            nextReset = nextReset.plusMonths(1);
        }
        
        return nextReset;
    }
    
    // These would be populated by services when needed
    private transient int currentApiKeyCount = 0;
    private transient int currentDomainCount = 0;
    
    public int getCurrentApiKeyCount() {
        return currentApiKeyCount;
    }
    
    public void setCurrentApiKeyCount(int count) {
        this.currentApiKeyCount = count;
    }
    
    public int getCurrentDomainCount() {
        return currentDomainCount;
    }
    
    public void setCurrentDomainCount(int count) {
        this.currentDomainCount = count;
    }

    public enum Role {
        USER,
        ADMIN
    }

    public enum AuthProvider {
        LOCAL,
        GOOGLE
    }
}
