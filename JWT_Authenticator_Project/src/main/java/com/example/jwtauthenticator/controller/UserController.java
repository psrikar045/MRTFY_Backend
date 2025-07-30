package com.example.jwtauthenticator.controller;

import com.example.jwtauthenticator.dto.BrandDataResponse;
import com.example.jwtauthenticator.dto.UserIdDTO;
import com.example.jwtauthenticator.dto.UserProfileUpdateRequestDTO;
import com.example.jwtauthenticator.dto.UserResponseDTO;
import com.example.jwtauthenticator.service.CategoryService;
import com.example.jwtauthenticator.service.UserService;
import com.example.jwtauthenticator.service.FileStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Data", description = "Endpoints for retrieving stored User information")
public class UserController {
 
	@Autowired
    private  UserService userService;
    
    @Autowired
    private FileStorageService fileStorageService;


    @PostMapping("/get-by-id") 
    @Operation(
            summary = "Get profile by ID",
            description = "Retrieve complete user information",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
        )
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found", 
                        content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
        })
    public ResponseEntity<UserResponseDTO> getUserByIdPost(@Valid @RequestBody UserIdDTO requestDTO) {
        return userService.getUserInfoByUserId(requestDTO.getId())
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    @PutMapping("/profile")
    public ResponseEntity<UserResponseDTO> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequestDTO updateRequest) {

        return userService.updateProfile( updateRequest)
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
	
    @GetMapping("/userId/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable String id) {
        return userService.getUserInfoByUserId(id)
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // Example of fetching by username (you might need to secure this endpoint appropriately)
    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponseDTO> getUserByUsername(@PathVariable String username) {
        return userService.getUserInfoByUsername(username)
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // Example of fetching by email (you might need to secure this endpoint appropriately)
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@PathVariable String email) {
        return userService.getUserInfoByEmail(email)
                .map(userResponseDTO -> new ResponseEntity<>(userResponseDTO, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    
    /**
     * Upload/Update user profile image
     * 
     * @param profileImage The profile image file
     * @param userId The user ID
     * @return Response containing the image URL and updated user info
     */
    @PutMapping(value = "/profile/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Update user profile image",
            description = "Upload and update user profile image. If a profile image already exists, it will be backed up with a timestamp.",
            security = { @SecurityRequirement(name = "Bearer Authentication") }
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile image updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request - missing file or user ID"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> updateProfileImage(
            @RequestParam("profileImage") MultipartFile profileImage,
            @RequestParam("userId") String userId) {
        
        log.info("Profile image upload request received for user: {}", userId);
        
        try {
            Map<String, Object> response = userService.updateProfileImage(userId, profileImage);
            
            if (response.containsKey("error")) {
                log.warn("Profile image upload failed for user {}: {}", userId, response.get("error"));
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("Profile image upload successful for user: {}", userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Unexpected error during profile image upload for user: {}", userId, e);
            Map<String, Object> errorResponse = Map.of(
                "error", "An unexpected error occurred: " + e.getMessage(),
                "success", false
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Serve user profile image
     * 
     * @param userId The user ID
     * @param filename The image filename
     * @return The profile image file
     */
    @GetMapping("/profile/{userId}/image/{filename}")
    @Operation(
            summary = "Serve user profile image",
            description = "Serve a user profile image file by user ID and filename"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile image served"),
            @ApiResponse(responseCode = "404", description = "Profile image not found"),
            @ApiResponse(responseCode = "500", description = "Error serving file")
    })
    public ResponseEntity<Resource> serveProfileImage(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId,
            @Parameter(description = "Image filename", required = true)
            @PathVariable String filename) {
        
        try {
            // Construct the relative path for the profile image
            String relativePath = String.format("users/%s/profile/%s", userId, filename);
            
            // Get the file as resource using FileStorageService
            Resource resource = fileStorageService.getFileAsResource(relativePath);
            
            // Determine content type
            String contentType = "image/jpeg"; // Default
            if (filename.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (filename.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (filename.toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "inline; filename=\"" + filename + "\"")
                    .body(resource);
            
        } catch (IOException e) {
            log.error("Error serving profile image for user {}, filename {}: {}", userId, filename, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Unexpected error serving profile image for user {}, filename {}: {}", userId, filename, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
