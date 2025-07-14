package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.Brand;
import com.example.jwtauthenticator.entity.BrandAsset;
import com.example.jwtauthenticator.entity.BrandImage;
import com.example.jwtauthenticator.repository.BrandAssetRepository;
import com.example.jwtauthenticator.repository.BrandImageRepository;
import com.example.jwtauthenticator.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {
    
    private final WebClient.Builder webClientBuilder;
    private final BrandRepository brandRepository;
    private final BrandAssetRepository brandAssetRepository;
    private final BrandImageRepository brandImageRepository;
    private final SftpFileStorageService sftpFileStorageService;
    private final HttpFileStorageService httpFileStorageService;
    
    @Value("${app.file-storage.type:local}")
    private String storageType; // local, http, sftp, s3, gcs
    
    @Value("${app.file-storage.local.base-path:./Brand_Assets}")
    private String localBasePath;
    
    @Value("${app.file-storage.server.base-url:http://202.65.155.125:8080/images/Brand_Assets}")
    private String serverBaseUrl;
    
    @Value("${app.file-storage.download.timeout-seconds:30}")
    private int downloadTimeoutSeconds;
    
    @Value("${app.file-storage.download.max-file-size:10485760}") // 10MB default
    private long maxFileSize;
    
    @Value("${app.file-storage.download.max-attempts:3}")
    private int maxDownloadAttempts;
    
    /**
     * Asynchronously download all brand assets and images
     */
    @Async
    public CompletableFuture<Void> downloadBrandAssetsAsync(Brand brand) {
        log.info("Starting async download of assets for brand: {} (ID: {})", brand.getName(), brand.getId());
        
        try {
            // Download brand assets (logos, icons, etc.)
            for (BrandAsset asset : brand.getAssets()) {
                if (asset.getDownloadStatus() == BrandAsset.DownloadStatus.PENDING) {
                    downloadAsset(asset);
                }
            }
            
            // Download brand images
            for (BrandImage image : brand.getImages()) {
                if (image.getDownloadStatus() == BrandImage.DownloadStatus.PENDING) {
                    downloadImage(image);
                }
            }
            
            log.info("Completed async download of assets for brand: {} (ID: {})", brand.getName(), brand.getId());
            
        } catch (Exception e) {
            log.error("Error during async asset download for brand: {} (ID: {})", brand.getName(), brand.getId(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Download a single brand asset
     */
    @Transactional
    public void downloadAsset(BrandAsset asset) {
        if (asset.getDownloadAttempts() >= maxDownloadAttempts) {
            log.warn("Max download attempts reached for asset: {}", asset.getOriginalUrl());
            return;
        }
        
        // Check if the asset still exists and has a valid brand
        // Only check existence if the asset has an ID (i.e., it was previously saved)
        if (asset.getId() != null) {
            if (!brandAssetRepository.existsById(asset.getId())) {
                log.warn("Asset no longer exists in database, skipping download: {}", asset.getOriginalUrl());
                return;
            }
        }
        
        // Check if brand still exists
        if (asset.getBrand() == null || !brandRepository.existsById(asset.getBrand().getId())) {
            log.warn("Asset's brand no longer exists, skipping download: {}", asset.getOriginalUrl());
            return;
        }
        
        try {
            asset.setDownloadStatus(BrandAsset.DownloadStatus.DOWNLOADING);
            asset.setDownloadAttempts(asset.getDownloadAttempts() + 1);
            brandAssetRepository.save(asset);
            
            log.info("Downloading asset: {} (Attempt: {})", asset.getOriginalUrl(), asset.getDownloadAttempts());
            
            // Download the file
            DownloadResult result = downloadFile(asset.getOriginalUrl(), generateAssetPath(asset));
            
            if (result.isSuccess()) {
                asset.setStoredPath(result.getStoredPath());
                asset.setFileSize(result.getFileSize());
                asset.setMimeType(result.getMimeType());
                asset.setDownloadStatus(BrandAsset.DownloadStatus.COMPLETED);
                asset.setDownloadedAt(LocalDateTime.now());
                asset.setDownloadError(null);
                
                String serverUrl = getFileUrl(result.getStoredPath());
                log.info("Successfully downloaded asset: {} -> {} (Server URL: {})", 
                        asset.getOriginalUrl(), result.getStoredPath(), serverUrl);
            } else {
                asset.setDownloadStatus(BrandAsset.DownloadStatus.FAILED);
                asset.setDownloadError(result.getErrorMessage());
                
                log.error("Failed to download asset: {} - {}", asset.getOriginalUrl(), result.getErrorMessage());
            }
            
        } catch (Exception e) {
            asset.setDownloadStatus(BrandAsset.DownloadStatus.FAILED);
            asset.setDownloadError("Unexpected error: " + e.getMessage());
            log.error("Unexpected error downloading asset: {}", asset.getOriginalUrl(), e);
        } finally {
            brandAssetRepository.save(asset);
        }
    }
    
    /**
     * Download a single brand image
     */
    @Transactional
    public void downloadImage(BrandImage image) {
        if (image.getDownloadAttempts() >= maxDownloadAttempts) {
            log.warn("Max download attempts reached for image: {}", image.getSourceUrl());
            return;
        }
        
        // Check if the image still exists and has a valid brand
        // Only check existence if the image has an ID (i.e., it was previously saved)
        if (image.getId() != null) {
            if (!brandImageRepository.existsById(image.getId())) {
                log.warn("Image no longer exists in database, skipping download: {}", image.getSourceUrl());
                return;
            }
        }
        
        // Check if brand still exists
        if (image.getBrand() == null || !brandRepository.existsById(image.getBrand().getId())) {
            log.warn("Image's brand no longer exists, skipping download: {}", image.getSourceUrl());
            return;
        }
        
        try {
            image.setDownloadStatus(BrandImage.DownloadStatus.DOWNLOADING);
            image.setDownloadAttempts(image.getDownloadAttempts() + 1);
            // Note: You'll need to create BrandImageRepository similar to BrandAssetRepository
            
            log.info("Downloading image: {} (Attempt: {})", image.getSourceUrl(), image.getDownloadAttempts());
            
            // Download the file
            DownloadResult result = downloadFile(image.getSourceUrl(), generateImagePath(image));
            
            if (result.isSuccess()) {
                image.setStoredPath(result.getStoredPath());
                image.setFileSize(result.getFileSize());
                image.setMimeType(result.getMimeType());
                image.setDownloadStatus(BrandImage.DownloadStatus.COMPLETED);
                image.setDownloadedAt(LocalDateTime.now());
                image.setDownloadError(null);
                
                String serverUrl = getFileUrl(result.getStoredPath());
                log.info("Successfully downloaded image: {} -> {} (Server URL: {})", 
                        image.getSourceUrl(), result.getStoredPath(), serverUrl);
            } else {
                image.setDownloadStatus(BrandImage.DownloadStatus.FAILED);
                image.setDownloadError(result.getErrorMessage());
                
                log.error("Failed to download image: {} - {}", image.getSourceUrl(), result.getErrorMessage());
            }
            
        } catch (Exception e) {
            image.setDownloadStatus(BrandImage.DownloadStatus.FAILED);
            image.setDownloadError("Unexpected error: " + e.getMessage());
            log.error("Unexpected error downloading image: {}", image.getSourceUrl(), e);
        } finally {
            brandImageRepository.save(image);
        }
    }
    
    /**
     * Download file from URL and store it
     */
    private DownloadResult downloadFile(String url, String targetPath) {
        try {
            WebClient webClient = webClientBuilder.build();
            
            // Download file content
            byte[] fileContent = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(java.time.Duration.ofSeconds(downloadTimeoutSeconds))
                    .block();
            
            if (fileContent == null || fileContent.length == 0) {
                return DownloadResult.failure("Empty file content");
            }
            
            if (fileContent.length > maxFileSize) {
                return DownloadResult.failure("File size exceeds maximum allowed size: " + maxFileSize);
            }
            
            // Store file based on storage type
            String storedPath = storeFile(fileContent, targetPath);
            String mimeType = detectMimeType(url, fileContent);
            
            return DownloadResult.success(storedPath, fileContent.length, mimeType);
            
        } catch (Exception e) {
            return DownloadResult.failure("Download failed: " + e.getMessage());
        }
    }
    
    /**
     * Store file based on configured storage type
     */
    private String storeFile(byte[] content, String targetPath) throws IOException {
        switch (storageType.toLowerCase()) {
            case "local":
                return storeFileLocally(content, targetPath);
            case "http":
                return storeFileViaHttp(content, targetPath);
            case "sftp":
                return storeFileViaSftp(content, targetPath);
            case "s3":
                return storeFileInS3(content, targetPath);
            case "gcs":
                return storeFileInGCS(content, targetPath);
            default:
                throw new IllegalArgumentException("Unsupported storage type: " + storageType);
        }
    }
    
    /**
     * Store file in local filesystem
     */
    private String storeFileLocally(byte[] content, String targetPath) throws IOException {
        Path fullPath = Paths.get(localBasePath, targetPath);
        
        // Create directories if they don't exist
        Files.createDirectories(fullPath.getParent());
        
        // Check if file already exists
        boolean fileExists = Files.exists(fullPath);
        if (fileExists) {
            log.info("File already exists, overwriting: {}", fullPath);
        }
        
        // Write file (this will overwrite if it exists)
        Files.write(fullPath, content);
        
        log.info("Successfully stored file locally: {} (Size: {} bytes)", fullPath, content.length);
        
        // Return relative path for database storage
        return targetPath;
    }
    
    /**
     * Store file on remote server via HTTP
     */
    private String storeFileViaHttp(byte[] content, String targetPath) throws IOException {
        try {
            // Extract filename from path
            String fileName = targetPath.substring(targetPath.lastIndexOf('/') + 1);
            
            // Upload to remote server via HTTP
            String storedPath = httpFileStorageService.uploadFile(content, fileName, targetPath);
            
            log.info("Successfully stored file via HTTP: {} (Size: {} bytes)", targetPath, content.length);
            
            // Return relative path for database storage
            return storedPath;
        } catch (Exception e) {
            log.error("Failed to store file via HTTP, falling back to local storage: {}", e.getMessage());
            // Fall back to local storage
            return storeFileLocally(content, targetPath);
        }
    }
    
    /**
     * Store file on remote server via SFTP
     */
    private String storeFileViaSftp(byte[] content, String targetPath) throws IOException {
        try {
            // Upload to remote server via SFTP
            String storedPath = sftpFileStorageService.uploadFile(content, targetPath);
            
            log.info("Successfully stored file via SFTP: {} (Size: {} bytes)", targetPath, content.length);
            
            // Return relative path for database storage
            return storedPath;
        } catch (Exception e) {
            log.error("Failed to store file via SFTP, falling back to local storage: {}", e.getMessage());
            // Fall back to local storage
            return storeFileLocally(content, targetPath);
        }
    }
    
    /**
     * Store file in AWS S3 (placeholder for future implementation)
     */
    private String storeFileInS3(byte[] content, String targetPath) {
        // TODO: Implement S3 storage
        // This will be implemented when AWS S3 configuration is added
        throw new UnsupportedOperationException("S3 storage not yet implemented");
    }
    
    /**
     * Store file in Google Cloud Storage (placeholder for future implementation)
     */
    private String storeFileInGCS(byte[] content, String targetPath) {
        // TODO: Implement GCS storage
        // This will be implemented when Google Cloud Storage configuration is added
        throw new UnsupportedOperationException("GCS storage not yet implemented");
    }
    
    /**
     * Generate storage path for brand asset
     */
    private String generateAssetPath(BrandAsset asset) {
        return String.format("brands/%d/assets/%s_%s", 
                asset.getBrand().getId(), 
                asset.getAssetType().name().toLowerCase(),
                asset.getFileName());
    }
    
    /**
     * Generate storage path for brand image
     */
    private String generateImagePath(BrandImage image) {
        return String.format("brands/%d/images/%s", 
                image.getBrand().getId(), 
                image.getFileName());
    }
    
    /**
     * Detect MIME type from URL and content
     */
    private String detectMimeType(String url, byte[] content) {
        try {
            // Try to detect from URL extension
            String extension = url.substring(url.lastIndexOf('.') + 1).toLowerCase();
            return switch (extension) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "svg" -> "image/svg+xml";
                case "webp" -> "image/webp";
                default -> "application/octet-stream";
            };
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
    
    /**
     * Get file as Resource for serving
     */
    public Resource getFileAsResource(String filePath) throws IOException {
        // Convert relative path to full local path for file access
        Path fullPath = Paths.get(localBasePath, filePath);
        Resource resource = new UrlResource(fullPath.toUri());
        
        if (resource.exists() && resource.isReadable()) {
            return resource;
        } else {
            throw new IOException("File not found or not readable: " + fullPath);
        }
    }
    
    /**
     * Construct full server URL from relative path
     */
    public String getFileUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        
        // Ensure proper URL formatting
        String baseUrl = serverBaseUrl.endsWith("/") ? serverBaseUrl : serverBaseUrl + "/";
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        
        return baseUrl + path;
    }
    
    /**
     * Get full server URL for a brand asset
     */
    public String getAssetUrl(BrandAsset asset) {
        return getFileUrl(asset.getStoredPath());
    }
    
    /**
     * Get full server URL for a brand image
     */
    public String getImageUrl(BrandImage image) {
        return getFileUrl(image.getStoredPath());
    }
    
    /**
     * Result class for download operations
     */
    private static class DownloadResult {
        private final boolean success;
        private final String storedPath;
        private final long fileSize;
        private final String mimeType;
        private final String errorMessage;
        
        private DownloadResult(boolean success, String storedPath, long fileSize, String mimeType, String errorMessage) {
            this.success = success;
            this.storedPath = storedPath;
            this.fileSize = fileSize;
            this.mimeType = mimeType;
            this.errorMessage = errorMessage;
        }
        
        public static DownloadResult success(String storedPath, long fileSize, String mimeType) {
            return new DownloadResult(true, storedPath, fileSize, mimeType, null);
        }
        
        public static DownloadResult failure(String errorMessage) {
            return new DownloadResult(false, null, 0, null, errorMessage);
        }
        
        public boolean isSuccess() { return success; }
        public String getStoredPath() { return storedPath; }
        public long getFileSize() { return fileSize; }
        public String getMimeType() { return mimeType; }
        public String getErrorMessage() { return errorMessage; }
    }
}