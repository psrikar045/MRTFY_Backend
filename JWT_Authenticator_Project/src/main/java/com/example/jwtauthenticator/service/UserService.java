package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.UserProfileUpdateRequestDTO;
import com.example.jwtauthenticator.dto.UserResponseDTO;
import com.example.jwtauthenticator.entity.User;
import com.example.jwtauthenticator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserService {

	@Autowired
    private UserRepository userRepository;
    
    @Value("${app.file-storage.profile.local.base-path:./User_Profile_Images}")
    private String profileImageBasePath;
    
    @Value("${app.file-storage.profile.server.base-url:http://202.65.155.125:8080/images}")
    private String serverBaseUrl;

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
     * Update user profile image
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
            
            // Create user-specific directory path
            String userFolderPath = "User_Profile_Images/" + userId;
            Path userDir = Paths.get(profileImageBasePath, userId);
            
            // Create directory if it doesn't exist
            Files.createDirectories(userDir);
            
            // Handle existing profile image - rename with timestamp if exists
            String existingImagePath = user.getProfilePictureUrl();
            if (existingImagePath != null && !existingImagePath.trim().isEmpty()) {
                try {
                    // Extract filename from existing path
                    String existingFileName = extractFileNameFromUrl(existingImagePath);
                    if (existingFileName != null) {
                        Path existingFile = userDir.resolve(existingFileName);
                        if (Files.exists(existingFile)) {
                            // Create backup filename with timestamp
                            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            String fileExtension = getFileExtension(existingFileName);
                            String backupFileName = "backup_" + timestamp + 
                                (fileExtension != null ? "." + fileExtension : "");
                            Path backupFile = userDir.resolve(backupFileName);
                            Files.move(existingFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
                            log.info("Existing profile image backed up as: {}", backupFile);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to backup existing profile image: {}", e.getMessage());
                    // Continue with upload even if backup fails
                }
            }
            
            // Generate new filename
            String originalFileName = profileImage.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String newFileName = "profile_" + System.currentTimeMillis() + 
                (fileExtension != null ? "." + fileExtension : "");
            
            // Save the new image
            Path newImagePath = userDir.resolve(newFileName);
            Files.copy(profileImage.getInputStream(), newImagePath, StandardCopyOption.REPLACE_EXISTING);
            
            // Construct the URL
            String imageUrl = constructImageUrl(userFolderPath + "/" + newFileName);
            
            // Update user's profile picture URL
            user.setProfilePictureUrl(imageUrl);
            User updatedUser = userRepository.save(user);
            
            log.info("Profile image updated successfully for user: {}, URL: {}", userId, imageUrl);
            
            // Prepare response
            response.put("success", true);
            response.put("profilePictureUrl", imageUrl);
            response.put("message", "Profile image updated successfully");
            response.put("user", UserResponseDTO.fromEntity(updatedUser));
            
            return response;
            
        } catch (IOException e) {
            log.error("Failed to upload profile image for user: {}", userId, e);
            response.put("error", "Failed to upload profile image: " + e.getMessage());
            return response;
        } catch (Exception e) {
            log.error("Unexpected error while updating profile image for user: {}", userId, e);
            response.put("error", "An unexpected error occurred: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * Extract filename from URL
     */
    private String extractFileNameFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < url.length() - 1) {
            return url.substring(lastSlashIndex + 1);
        }
        return url; // Return as is if no slash found
    }
    
    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
    
    /**
     * Construct image URL from path
     */
    private String constructImageUrl(String path) {
        String baseUrl = serverBaseUrl.endsWith("/") ? serverBaseUrl : serverBaseUrl + "/";
        String imagePath = path.startsWith("/") ? path.substring(1) : path;
        return baseUrl + imagePath;
    }
}
