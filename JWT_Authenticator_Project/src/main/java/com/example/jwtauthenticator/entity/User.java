package com.example.jwtauthenticator.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

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

    @Column(name = "verification_token")
    private String verificationToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider")
    private AuthProvider authProvider;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @PrePersist
    protected void onCreate() {
        if (userId == null) {
            userId = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
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
