package com.example.jwtauthenticator.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for handling file uploads
 * This should be deployed on the remote server (202.65.155.125)
 */
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    @Value("${app.file-storage.remote.base-path:/var/www/html/images/Brand_Assets}")
    private String uploadBasePath;
    
    @Value("${app.file-storage.server.base-url:http://202.65.155.125:8080/images/Brand_Assets}")
    private String serverBaseUrl;
    
    /**
     * Upload a file to the server
     * 
     * @param file The file to upload
     * @param path The target path (optional)
     * @return The URL of the uploaded file
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "path", required = false) String path) {
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }
            
            // Determine target path
            String targetPath;
            if (path != null && !path.isEmpty()) {
                targetPath = path;
            } else {
                // Generate a random path if none provided
                String extension = getFileExtension(file.getOriginalFilename());
                targetPath = "uploads/" + UUID.randomUUID() + (extension != null ? "." + extension : "");
            }
            
            // Create full path
            Path fullPath = Paths.get(uploadBasePath, targetPath);
            
            // Create parent directories if they don't exist
            Files.createDirectories(fullPath.getParent());
            
            // Save the file
            Files.copy(file.getInputStream(), fullPath, StandardCopyOption.REPLACE_EXISTING);
            
            log.info("File uploaded successfully: {}", fullPath);
            
            // Construct the URL
            String fileUrl = constructFileUrl(targetPath);
            
            // Return the URL
            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("path", targetPath);
            response.put("size", String.valueOf(file.getSize()));
            
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
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
     * Construct file URL from path
     */
    private String constructFileUrl(String path) {
        String baseUrl = serverBaseUrl.endsWith("/") ? serverBaseUrl : serverBaseUrl + "/";
        String filePath = path.startsWith("/") ? path.substring(1) : path;
        return baseUrl + filePath;
    }
}