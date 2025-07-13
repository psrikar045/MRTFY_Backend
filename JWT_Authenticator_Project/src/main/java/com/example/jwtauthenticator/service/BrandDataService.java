package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.BrandDataResponse;
import com.example.jwtauthenticator.entity.*;
import com.example.jwtauthenticator.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandDataService {
    
    private final BrandRepository brandRepository;
    
    /**
     * Get brand data by ID
     */
    @Transactional(readOnly = true)
    public Optional<BrandDataResponse> getBrandById(Long id) {
        return brandRepository.findById(id)
                .map(this::convertToResponse);
    }
    
    /**
     * Get brand data by website URL
     */
    @Transactional(readOnly = true)
    public Optional<BrandDataResponse> getBrandByWebsite(String website) {
        return brandRepository.findByWebsite(website)
                .map(this::convertToResponse);
    }
    
    /**
     * Get brand data by name (case-insensitive)
     */
    @Transactional(readOnly = true)
    public Optional<BrandDataResponse> getBrandByName(String name) {
        return brandRepository.findByNameIgnoreCase(name)
                .map(this::convertToResponse);
    }
    
    /**
     * Search brands by name, website, description, or industry
     */
    @Transactional(readOnly = true)
    public Page<BrandDataResponse> searchBrands(String searchTerm, Pageable pageable) {
        return brandRepository.searchBrands(searchTerm, pageable)
                .map(this::convertToResponse);
    }
    
    /**
     * Get all brands with pagination
     */
    @Transactional(readOnly = true)
    public Page<BrandDataResponse> getAllBrands(Pageable pageable) {
        return brandRepository.findAllOrderByCreatedAtDesc(pageable)
                .map(this::convertToResponse);
    }
    
    /**
     * Get brands containing name pattern
     */
    @Transactional(readOnly = true)
    public List<BrandDataResponse> getBrandsContainingName(String namePattern) {
        return brandRepository.findByNameContainingIgnoreCase(namePattern)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get brands by domain pattern
     */
    @Transactional(readOnly = true)
    public List<BrandDataResponse> getBrandsByDomain(String domain) {
        return brandRepository.findByWebsiteContaining(domain)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get brand statistics
     */
    @Transactional(readOnly = true)
    public BrandStatistics getBrandStatistics() {
        long totalBrands = brandRepository.count();
        long recentBrands = brandRepository.countBrandsCreatedSince(
                java.time.LocalDateTime.now().minusDays(30)
        );
        
        // Count claimed brands
        long claimedBrands = brandRepository.countByIsBrandClaimedTrue();
        
        return BrandStatistics.builder()
                .totalBrands(totalBrands)
                .brandsCreatedLastMonth(recentBrands)
                .claimedBrands(claimedBrands)
                .build();
    }
    
    /**
     * Convert Brand entity to BrandDataResponse DTO
     */
    private BrandDataResponse convertToResponse(Brand brand) {
        return BrandDataResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .website(brand.getWebsite())
                .description(brand.getDescription())
                .industry(brand.getIndustry())
                .location(brand.getLocation())
                .founded(brand.getFounded())
                .companyType(brand.getCompanyType())
                .employees(brand.getEmployees())
                .extractionTimeSeconds(brand.getExtractionTimeSeconds())
                .lastExtractionTimestamp(brand.getLastExtractionTimestamp())
                .extractionMessage(brand.getExtractionMessage())
                .freshnessScore(brand.getFreshnessScore())
                .needsUpdate(brand.getNeedsUpdate())
                .isBrandClaimed(brand.getIsBrandClaimed())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .assets(convertAssets(brand.getAssets()))
                .colors(convertColors(brand.getColors()))
                .fonts(convertFonts(brand.getFonts()))
                .socialLinks(convertSocialLinks(brand.getSocialLinks()))
                .images(convertImages(brand.getImages()))
                .build();
    }
    
    private List<BrandDataResponse.AssetInfo> convertAssets(List<BrandAsset> assets) {
        return assets.stream()
                .map(asset -> BrandDataResponse.AssetInfo.builder()
                        .id(asset.getId())
                        .assetType(asset.getAssetType().name())
                        .originalUrl(asset.getOriginalUrl())
                        .accessUrl(asset.getAccessUrl())
                        .fileName(asset.getFileName())
                        .fileSize(asset.getFileSize())
                        .mimeType(asset.getMimeType())
                        .downloadStatus(asset.getDownloadStatus().name())
                        .downloadedAt(asset.getDownloadedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    private List<BrandDataResponse.ColorInfo> convertColors(List<BrandColor> colors) {
        return colors.stream()
                .map(color -> BrandDataResponse.ColorInfo.builder()
                        .id(color.getId())
                        .hexCode(color.getHexCode())
                        .rgbValue(color.getRgbValue())
                        .brightness(color.getBrightness())
                        .colorName(color.getColorName())
                        .usageContext(color.getUsageContext())
                        .build())
                .collect(Collectors.toList());
    }
    
    private List<BrandDataResponse.FontInfo> convertFonts(List<BrandFont> fonts) {
        return fonts.stream()
                .map(font -> BrandDataResponse.FontInfo.builder()
                        .id(font.getId())
                        .fontName(font.getFontName())
                        .fontType(font.getFontType())
                        .fontStack(font.getFontStack())
                        .build())
                .collect(Collectors.toList());
    }
    
    private List<BrandDataResponse.SocialLinkInfo> convertSocialLinks(List<BrandSocialLink> socialLinks) {
        return socialLinks.stream()
                .map(link -> BrandDataResponse.SocialLinkInfo.builder()
                        .id(link.getId())
                        .platform(link.getPlatform().name())
                        .url(link.getUrl())
                        .extractionError(link.getExtractionError())
                        .build())
                .collect(Collectors.toList());
    }
    
    private List<BrandDataResponse.ImageInfo> convertImages(List<BrandImage> images) {
        return images.stream()
                .map(image -> BrandDataResponse.ImageInfo.builder()
                        .id(image.getId())
                        .sourceUrl(image.getSourceUrl())
                        .altText(image.getAltText())
                        .accessUrl(image.getAccessUrl())
                        .fileName(image.getFileName())
                        .fileSize(image.getFileSize())
                        .mimeType(image.getMimeType())
                        .downloadStatus(image.getDownloadStatus().name())
                        .downloadedAt(image.getDownloadedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Statistics DTO
     */
    @lombok.Builder
    @lombok.Data
    public static class BrandStatistics {
        private long totalBrands;
        private long brandsCreatedLastMonth;
        private long claimedBrands;
    }
}