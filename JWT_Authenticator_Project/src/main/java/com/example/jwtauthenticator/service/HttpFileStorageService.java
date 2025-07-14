package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Service for storing files on a remote server using HTTP uploads
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HttpFileStorageService {

    private final WebClient.Builder webClientBuilder;
    
    @Value("${app.file-storage.remote.upload-url:http://202.65.155.125:8080/images/upload}")
    private String remoteUploadUrl;
    
    @Value("${app.file-storage.remote.auth-token:}")
    private String remoteAuthToken;
    
    @Value("${app.file-storage.remote.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Value("${app.file-storage.local.base-path:./Brand_Assets}")
    private String localBasePath;
    
    /**
     * Upload a file to the remote server using HTTP multipart upload
     * 
     * @param fileContent The file content as byte array
     * @param fileName The file name
     * @param targetPath The target path on the remote server
     * @return The path where the file was stored
     * @throws IOException If an I/O error occurs
     */
    public String uploadFile(byte[] fileContent, String fileName, String targetPath) throws IOException {
        log.info("Uploading file to remote server via HTTP: {}, size: {} bytes", targetPath, fileContent.length);
        
        try {
            // Create multipart request
            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            
            // Add file part
            bodyBuilder.part("file", new ByteArrayResource(fileContent))
                .filename(fileName)
                .header("Content-Disposition", "form-data; name=\"file\"; filename=\"" + fileName + "\"");
            
            // Add target path part
            bodyBuilder.part("path", targetPath);
            
            // Create WebClient with timeout
            WebClient webClient = webClientBuilder.build();
            
            // Send request
            String response = webClient.post()
                .uri(remoteUploadUrl)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> {
                    if (remoteAuthToken != null && !remoteAuthToken.isEmpty()) {
                        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + remoteAuthToken);
                    }
                })
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
            
            log.info("Successfully uploaded file to remote server: {}, response: {}", targetPath, response);
            return targetPath;
            
        } catch (WebClientResponseException e) {
            log.error("Error uploading file to remote server: {} - Status: {}, Response: {}", 
                    e.getMessage(), e.getStatusCode(), e.getResponseBodyAsString());
            
            // Fall back to local storage
            log.info("Falling back to local storage for file: {}", targetPath);
            return storeFileLocally(fileContent, targetPath);
        } catch (Exception e) {
            log.error("Error uploading file to remote server: {}", e.getMessage());
            
            // Fall back to local storage
            log.info("Falling back to local storage for file: {}", targetPath);
            return storeFileLocally(fileContent, targetPath);
        }
    }
    
    /**
     * Store file in local filesystem as fallback
     */
    private String storeFileLocally(byte[] content, String targetPath) throws IOException {
        Path fullPath = Paths.get(localBasePath, targetPath);
        
        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());
        
        // Check if file already exists
        boolean fileExists = Files.exists(fullPath);
        if (fileExists) {
            log.info("File already exists locally, overwriting: {}", fullPath);
        }
        
        // Write file (this will overwrite if it exists)
        Files.write(fullPath, content);
        
        log.info("Successfully stored file locally: {} (Size: {} bytes)", fullPath, content.length);
        
        // Return relative path for database storage
        return targetPath;
    }
}