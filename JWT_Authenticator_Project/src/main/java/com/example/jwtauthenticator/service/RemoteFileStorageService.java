package com.example.jwtauthenticator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

/**
 * Service for uploading files to a remote server
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemoteFileStorageService {

    private final WebClient.Builder webClientBuilder;
    
    @Value("${app.file-storage.remote.upload-url:http://202.65.155.125:8080/images/upload}")
    private String remoteUploadUrl;
    
    @Value("${app.file-storage.remote.auth-token:}")
    private String authToken;
    
    @Value("${app.file-storage.remote.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Value("${app.file-storage.local.base-path:./Brand_Assets}")
    private String localBasePath;
    
    /**
     * Upload a file to the remote server using WebClient (reactive)
     * 
     * @param fileContent The file content as byte array
     * @param fileName The name of the file
     * @param targetPath The target path on the remote server
     * @return The URL of the uploaded file
     * @throws IOException If an I/O error occurs
     */
    public String uploadFileWebClient(byte[] fileContent, String fileName, String targetPath) throws IOException {
        try {
            log.info("Uploading file to remote server: {}, size: {} bytes", fileName, fileContent.length);
            
            // Create WebClient
            WebClient webClient = webClientBuilder.build();
            
            // Create multipart form data
            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            
            // Add file part
            ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            formData.add("file", fileResource);
            
            // Add path information
            formData.add("path", targetPath);
            
            // Send request
            String response = webClient.post()
                    .uri(remoteUploadUrl)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .headers(headers -> {
                        if (authToken != null && !authToken.isEmpty()) {
                            headers.setBearerAuth(authToken);
                        }
                    })
                    .body(BodyInserters.fromMultipartData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .block();
            
            log.info("File uploaded successfully: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("Error uploading file to remote server: {}", fileName, e);
            
            // Fall back to local storage if remote upload fails
            log.info("Falling back to local storage for file: {}", fileName);
            return storeFileLocally(fileContent, targetPath);
        }
    }
    
    /**
     * Upload a file to the remote server using RestTemplate (blocking)
     * 
     * @param fileContent The file content as byte array
     * @param fileName The name of the file
     * @param targetPath The target path on the remote server
     * @return The URL of the uploaded file
     * @throws IOException If an I/O error occurs
     */
    public String uploadFileRestTemplate(byte[] fileContent, String fileName, String targetPath) throws IOException {
        try {
            log.info("Uploading file to remote server using RestTemplate: {}, size: {} bytes", fileName, fileContent.length);
            
            // Create RestTemplate
            RestTemplate restTemplate = new RestTemplate();
            
            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (authToken != null && !authToken.isEmpty()) {
                headers.setBearerAuth(authToken);
            }
            
            // Create multipart form data
            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            
            // Add file part
            ByteArrayResource fileResource = new ByteArrayResource(fileContent) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            formData.add("file", fileResource);
            
            // Add path information
            formData.add("path", targetPath);
            
            // Create request entity
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(formData, headers);
            
            // Send request
            ResponseEntity<String> response = restTemplate.postForEntity(
                    remoteUploadUrl,
                    requestEntity,
                    String.class
            );
            
            log.info("File uploaded successfully: {}", response.getBody());
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Error uploading file to remote server: {}", fileName, e);
            
            // Fall back to local storage if remote upload fails
            log.info("Falling back to local storage for file: {}", fileName);
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