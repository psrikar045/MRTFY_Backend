package com.example.jwtauthenticator.dto;

import com.example.jwtauthenticator.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponseDTO {
    private UUID userId;
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String location;
    private User.Role role;
    private boolean tfaEnabled;
    private String brandId;
    private boolean emailVerified;
    private User.AuthProvider authProvider;
    private String profilePictureUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // You can add more fields here if needed from your User entity

    public static UserResponseDTO fromEntity(User user) {
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .location(user.getLocation())
                .role(user.getRole())
                .tfaEnabled(user.isTfaEnabled())
                .brandId(user.getBrandId())
                .emailVerified(user.isEmailVerified())
                .authProvider(user.getAuthProvider())
                .profilePictureUrl(user.getProfilePictureUrl())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
