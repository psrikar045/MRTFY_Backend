package com.example.jwtauthenticator.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for returning brand data to API consumers
 */
@Data
@Builder
public class BrandDataResponse {
    
    private Long id;
    private String name;
    private String website;
    private String description;
    private String industry;
    private String location;
    private String founded;
    private String companyType;
    private String employees;
    private Long categoryId;
    private Long subCategoryId;
    
    // Metadata
    private Double extractionTimeSeconds;
    private LocalDateTime lastExtractionTimestamp;
    private String extractionMessage;
    private Integer freshnessScore;
    private Boolean needsUpdate;
    private Boolean isBrandClaimed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Related data
    private List<AssetInfo> assets;
    private List<ColorInfo> colors;
    private List<FontInfo> fonts;
    private List<SocialLinkInfo> socialLinks;
    private List<ImageInfo> images;
    
    @Data
    @Builder
    public static class AssetInfo {
        private Long id;
        private String assetType;
        private String originalUrl;
        private String accessUrl;
        private String fileName;
        private Long fileSize;
        private String mimeType;
        private String downloadStatus;
        private LocalDateTime downloadedAt;
    }
    
    @Data
    @Builder
    public static class ColorInfo {
        private Long id;
        private String hexCode;
        private String rgbValue;
        private Integer brightness;
        private String colorName;
        private String usageContext;
    }
    
    @Data
    @Builder
    public static class FontInfo {
        private Long id;
        private String fontName;
        private String fontType;
        private String fontStack;
    }
    
    @Data
    @Builder
    public static class SocialLinkInfo {
        private Long id;
        private String platform;
        private String url;
        private String extractionError;
    }
    
    @Data
    @Builder
    public static class ImageInfo {
        private Long id;
        private String sourceUrl;
        private String altText;
        private String accessUrl;
        private String fileName;
        private Long fileSize;
        private String mimeType;
        private String downloadStatus;
        private LocalDateTime downloadedAt;
    }
}