package com.example.jwtauthenticator.dto;

import com.example.jwtauthenticator.entity.User;
import lombok.AllArgsConstructor; // Add this
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; // Add this

import java.time.LocalDateTime;
import java.util.Date; // New import for futureT fields
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor // Added for proper JSON deserialization if this DTO is ever used as @RequestBody
@AllArgsConstructor // Added for proper JSON deserialization if this DTO is ever used as @RequestBody
public class UserResponseDTO {
    private UUID userId;
    private String id; // Primary key
    private String userCode; // Added
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

    // --- Added Future Fields ---
    private String futureV1;
    private String futureV2;
    private String futureV3;
    private String futureV4;
    private String futureV5;

    private String futureI1;
    private String futureI2;
    private String futureI3;
    private String futureI4;
    private String futureI5;

    private Date futureT1;
    private Date futureT2;
    private Date futureT3;
    // --- End Future Fields ---

    public static UserResponseDTO fromEntity(User user) {
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .id(user.getId())
                .userCode(user.getUserCode()) // Added
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
                // --- Mapping Future Fields ---
                .futureV1(user.getFutureV1())
                .futureV2(user.getFutureV2())
                .futureV3(user.getFutureV3())
                .futureV4(user.getFutureV4())
                .futureV5(user.getFutureV5())
                .futureI1(user.getFutureI1())
                .futureI2(user.getFutureI2())
                .futureI3(user.getFutureI3())
                .futureI4(user.getFutureI4())
                .futureI5(user.getFutureI5())
                .futureT1(user.getFutureT1())
                .futureT2(user.getFutureT2())
                .futureT3(user.getFutureT3())
                // --- End Mapping Future Fields ---
                .build();
    }
}