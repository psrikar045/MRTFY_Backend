package com.example.jwtauthenticator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "brand_assets", indexes = {
    @Index(name = "idx_asset_type", columnList = "assetType"),
    @Index(name = "idx_asset_brand", columnList = "brand_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandAsset {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;
    
    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;
    
    @Column(name = "stored_path", columnDefinition = "TEXT")
    private String storedPath;
    
    @Column(name = "file_name")
    private String fileName;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "mime_type")
    private String mimeType;
    
    @Column(name = "download_status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DownloadStatus downloadStatus = DownloadStatus.PENDING;
    
    @Column(name = "download_error", columnDefinition = "TEXT")
    private String downloadError;
    
    @Column(name = "download_attempts")
    @Builder.Default
    private Integer downloadAttempts = 0;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "downloaded_at")
    private LocalDateTime downloadedAt;
    
    public enum AssetType {
        LOGO,
        SYMBOL, 
        ICON,
        BANNER,
        IMAGE
    }
    
    public enum DownloadStatus {
        PENDING,
        DOWNLOADING,
        COMPLETED,
        FAILED,
        SKIPPED
    }
    
    // Helper method to get full URL for serving assets
    public String getAccessUrl() {
        if (storedPath != null) {
            // This will be configurable based on storage type (local/S3/GCS)
            return "/api/brand-assets/" + id;
        }
        return originalUrl; // Fallback to original URL
    }
}