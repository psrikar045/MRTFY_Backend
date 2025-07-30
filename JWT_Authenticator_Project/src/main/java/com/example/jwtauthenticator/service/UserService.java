package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.UserProfileUpdateRequestDTO;
import com.example.jwtauthenticator.dto.UserResponseDTO;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserService {

	@Autowired
    private UserRepository userRepository;
    
    @Autowired
    private FileStorageService fileStorageService;

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
    public Optional<UserResponseDTO> updateProfile( UserProfileUpdateRequestDTO updateRequest) {
        // Handle null request
        if (updateRequest == null) {
            return Optional.empty();
        }
        
        // Find the user by userId (assuming userId is passed in the path)
        Optional<User> userOptional = userRepository.findById(updateRequest.getId());

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
    
    /**
     * Update user profile image using FileStorageService
     * 
     * @param userId User ID
     * @param profileImage Profile image file
     * @return Map containing the image URL and updated user info
     */
    @Transactional
    public Map<String, Object> updateProfileImage(String userId, MultipartFile profileImage) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate inputs
            if (profileImage == null || profileImage.isEmpty()) {
                response.put("error", "Profile image file is required");
                return response;
            }
            
            if (userId == null || userId.trim().isEmpty()) {
                response.put("error", "User ID is required");
                return response;
            }
            
            // Find the user
            Optional<User> userOptional = userRepository.findById(userId);
            if (!userOptional.isPresent()) {
                response.put("error", "User not found");
                return response;
            }
            
            User user = userOptional.get();
            
            // Validate file type (only allow image files)
            String contentType = profileImage.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                response.put("error", "Only image files are allowed");
                return response;
            }
            
            // Handle existing profile image backup
            String existingImagePath = user.getProfilePictureUrl();
            if (existingImagePath != null && !existingImagePath.trim().isEmpty()) {
                // Extract relative path from URL for backup
                String relativePath = extractRelativePathFromUrl(existingImagePath);
                if (relativePath != null) {
                    fileStorageService.backupExistingProfileImage(userId, relativePath);
                }
            }
            
            // Store the new profile image using FileStorageService
            byte[] fileContent = profileImage.getBytes();
            String originalFileName = profileImage.getOriginalFilename();
            
            FileStorageService.StorageResult storageResult = fileStorageService.storeUserProfileImage(
                userId, fileContent, originalFileName);
            
            if (!storageResult.isSuccess()) {
                response.put("error", "Failed to store profile image: " + storageResult.getErrorMessage());
                return response;
            }
            
            // Construct the full URL using FileStorageService
            String imageUrl = fileStorageService.getFileUrl(storageResult.getStoredPath());
            
            // Update user's profile picture URL
            user.setProfilePictureUrl(imageUrl);
            User updatedUser = userRepository.save(user);
            
            log.info("Profile image updated successfully for user: {}, URL: {}", userId, imageUrl);
            
            // Prepare response
            response.put("success", true);
            response.put("profilePictureUrl", imageUrl);
            response.put("message", "Profile image updated successfully");
            response.put("fileSize", storageResult.getFileSize());
            response.put("mimeType", storageResult.getMimeType());
            response.put("user", UserResponseDTO.fromEntity(updatedUser));
            
            return response;
            
        } catch (IOException e) {
            log.error("Failed to read profile image file for user: {}", userId, e);
            response.put("error", "Failed to read profile image file: " + e.getMessage());
            return response;
        } catch (Exception e) {
            log.error("Unexpected error while updating profile image for user: {}", userId, e);
            response.put("error", "An unexpected error occurred: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * Extract relative path from full URL for backup purposes
     */
    private String extractRelativePathFromUrl(String fullUrl) {
        if (fullUrl == null || fullUrl.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Look for the pattern that indicates the start of the relative path
            // For user profile images, it should be "users/{userId}/profile/..."
            int usersIndex = fullUrl.indexOf("users/");
            if (usersIndex >= 0) {
                return fullUrl.substring(usersIndex);
            }
            
            // Fallback: try to extract everything after the last occurrence of "images/"
            int imagesIndex = fullUrl.lastIndexOf("images/");
            if (imagesIndex >= 0) {
                return fullUrl.substring(imagesIndex + "images/".length());
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract relative path from URL: {}", fullUrl, e);
            return null;
        }
    }
}
