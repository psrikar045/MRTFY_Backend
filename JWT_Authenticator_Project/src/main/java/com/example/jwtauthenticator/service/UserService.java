package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.UserProfileUpdateRequestDTO;
import com.example.jwtauthenticator.dto.UserResponseDTO;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

	@Autowired
    private UserRepository userRepository;

    public Optional<UserResponseDTO> getUserInfoByUserId(String userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        return userOptional.map(UserResponseDTO::fromEntity);
    }

    // You might also want to add methods to fetch by username, email, etc.
    public Optional<UserResponseDTO> getUserInfoByUsername(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        return userOptional.map(UserResponseDTO::fromEntity);
    }

    public Optional<UserResponseDTO> getUserInfoByEmail(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        return userOptional.map(UserResponseDTO::fromEntity);
    }
    
    
    @Transactional
    public Optional<UserResponseDTO> updateProfile(UUID userId, UserProfileUpdateRequestDTO updateRequest) {
        // Find the user by userId (assuming userId is passed in the path)
        Optional<User> userOptional = userRepository.findByUserId(userId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Update fields from DTO, only if they are present in the request
            // (i.e., not null, implying a partial update)

            if (updateRequest.getFirstName() != null) {
                user.setFirstName(updateRequest.getFirstName());
            }
            if (updateRequest.getSurname() != null) { // surname from frontend maps to lastName
                user.setLastName(updateRequest.getSurname());
            }
            if (updateRequest.getNationalCode() != null) {
                user.setFutureI1(updateRequest.getNationalCode());
            }
         // --- IMPORTANT: DOB (futureT1) update logic ---
            // If the DTO contains a parsed Date for dob, update futureT1
            if (updateRequest.getDob() != null) {
                user.setFutureT1(updateRequest.getDob());
            } else {
                // Consideration: What if frontend sends "" for dob?
                // With @JsonFormat, "" will cause a deserialization error.
                // If you want to allow clearing the DOB, frontend should send null.
                // If updateRequest.getDob() is null (because frontend sent null or omitted),
                // the field will NOT be updated, retaining its old value.
                // If you explicitly want to set it to NULL in DB when frontend sends empty:
                // user.setFutureT1(null); // This would set to null if dob is null in DTO
            }
            // --- End DOB update logic ---
            if (updateRequest.getEducationLevel() != null) {
                user.setFutureV1(updateRequest.getEducationLevel());
            }
            // Removed email update logic:
            // if (updateRequest.getEmail() != null) { user.setEmail(updateRequest.getEmail()); }

            if (updateRequest.getPhoneCountry() != null) {
                user.setFutureV2(updateRequest.getPhoneCountry());
            }
            if (updateRequest.getPhoneNumber() != null) {
                user.setPhoneNumber(updateRequest.getPhoneNumber());
            }
            if (updateRequest.getCountry() != null) {
                user.setFutureV3(updateRequest.getCountry());
            }
            if (updateRequest.getCity() != null) {
                user.setFutureV4(updateRequest.getCity());
            }
            
            // Removed updatedAt update logic:
            // (Note: @PreUpdate in User entity will still handle `updatedAt = LocalDateTime.now();` automatically)
            // if (updateRequest.getUpdatedAt() != null) { user.setUpdatedAt(updateRequest.getUpdatedAt()); }
            
            if (updateRequest.getUsername() != null) {
                // IMPORTANT: If username is unique, you might still need to handle conflicts
                // if (!user.getUsername().equals(updateRequest.getUsername()) && userRepository.existsByUsername(updateRequest.getUsername())) {
                //    throw new DuplicateUsernameException("Username already taken.");
                // }
                user.setUsername(updateRequest.getUsername());
            }
            // Removed emailVerified update logic:
            // if (updateRequest.getEmailVerified() != null) { user.setEmailVerified(updateRequest.getEmailVerified()); }
            // Removed authProvider update logic:
            // if (updateRequest.getAuthProvider() != null) { user.setAuthProvider(updateRequest.getAuthProvider()); }

            // Save the updated user (this will trigger @PreUpdate for updatedAt)
            User updatedUser = userRepository.save(user);
            return Optional.of(UserResponseDTO.fromEntity(updatedUser));
        }
        return Optional.empty(); // User not found
    }
}
