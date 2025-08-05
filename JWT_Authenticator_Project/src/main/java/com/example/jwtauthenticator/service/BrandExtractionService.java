package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.dto.BrandExtractionResponse;
import com.example.jwtauthenticator.entity.*;
import com.example.jwtauthenticator.repository.BrandRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandExtractionService {
    
    private final BrandRepository brandRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final BrandCategoryResolutionService brandCategoryResolutionService;
    
    /**
     * Extract and store brand data from the API response
     */
    @Transactional
    public Brand extractAndStoreBrandData(String url, String apiResponse) {
        try {
            log.info("Starting brand data extraction for URL: {}", url);
            
            // Parse the API response
            BrandExtractionResponse extractionResponse = objectMapper.readValue(apiResponse, BrandExtractionResponse.class);
            
            // Check if brand already exists
            Optional<Brand> existingBrand = findExistingBrand(url, extractionResponse);
            
            Brand brand;
            if (existingBrand.isPresent()) {
                brand = updateExistingBrand(existingBrand.get(), extractionResponse);
                log.info("Updated existing brand: {} (ID: {})", brand.getName(), brand.getId());
            } else {
                brand = createNewBrand(url, extractionResponse);
                log.info("Created new brand: {} (ID: {})", brand.getName(), brand.getId());
            }
            
            // Schedule asynchronous asset downloads after transaction commits
            scheduleAsyncDownload(brand);
            
            return brand;
            
        } catch (Exception e) {
            log.error("Failed to extract brand data for URL: {}", url, e);
            throw new RuntimeException("Brand data extraction failed: " + e.getMessage(), e);
        }
    }
    
    private Optional<Brand> findExistingBrand(String url, BrandExtractionResponse response) {
        // First try to find by website URL
        Optional<Brand> brandByUrl = brandRepository.findByWebsite(url);
        if (brandByUrl.isPresent()) {
            return brandByUrl;
        }
        
        // Then try by company website from response
        if (response.getCompany() != null && StringUtils.hasText(response.getCompany().getWebsite())) {
            Optional<Brand> brandByCompanyUrl = brandRepository.findByWebsite(response.getCompany().getWebsite());
            if (brandByCompanyUrl.isPresent()) {
                return brandByCompanyUrl;
            }
        }
        
        // Finally try by company name
        if (response.getCompany() != null && StringUtils.hasText(response.getCompany().getName())) {
            return brandRepository.findByNameIgnoreCase(response.getCompany().getName());
        }
        
        return Optional.empty();
    }
    
    private Brand createNewBrand(String url, BrandExtractionResponse response) {
        Brand brand = Brand.builder()
                .website(url)
                .lastExtractionTimestamp(LocalDateTime.now())
                .extractionMessage(response.getMessage())
                .freshnessScore(100)
                .needsUpdate(false)
                .build();
        
        // Set company data with cleaning and new field handling
        if (response.getCompany() != null) {
            BrandExtractionResponse.CompanyData company = response.getCompany();
            brand.setName(StringUtils.hasText(company.getName()) ? company.getName() : extractDomainFromUrl(url));
            brand.setDescription(cleanDescription(company.getDescription()));
            brand.setIndustry(company.getIndustry());
            
            // Handle location with preference for Headquarters
            String location = StringUtils.hasText(company.getHeadquarters()) ? 
                company.getHeadquarters() : company.getLocation();
            brand.setLocation(location);
            
            brand.setFounded(company.getFounded());
            
            // Handle company type with preference for Type field
            String companyType = StringUtils.hasText(company.getType()) ? 
                company.getType() : company.getCompanyType();
            brand.setCompanyType(companyType);
            
            // Handle employees with preference for CompanySize
            String employees = StringUtils.hasText(company.getCompanySize()) ? 
                company.getCompanySize() : company.getEmployees();
            brand.setEmployees(employees);
            
            // Use cleaned company website if available, otherwise use the original URL
            if (StringUtils.hasText(company.getWebsite())) {
                brand.setWebsite(cleanWebsiteUrl(company.getWebsite()));
            }
            
            // Handle new fields - store as JSON strings for performance
            try {
                if (company.getSpecialties() != null && !company.getSpecialties().isEmpty()) {
                    brand.setSpecialties(objectMapper.writeValueAsString(company.getSpecialties()));
                }
                if (company.getLocations() != null && !company.getLocations().isEmpty()) {
                    brand.setLocations(objectMapper.writeValueAsString(company.getLocations()));
                }
            } catch (Exception e) {
                log.warn("Failed to serialize specialties/locations for brand: {}", brand.getName(), e);
            }
        } else {
            brand.setName(extractDomainFromUrl(url));
        }
        
        // Set performance data
        if (response.getPerformance() != null) {
            brand.setExtractionTimeSeconds(response.getPerformance().getExtractionTimeSeconds());
        }
        
        // Resolve and set category IDs based on industry
        try {
            brandCategoryResolutionService.setCategoryIds(brand);
            log.info("Category resolution completed for brand: '{}' - categoryId: {}, subCategoryId: {}", 
                    brand.getName(), brand.getCategoryId(), brand.getSubCategoryId());
        } catch (Exception e) {
            log.error("Failed to resolve category IDs for brand: '{}' with industry: '{}'", 
                    brand.getName(), brand.getIndustry(), e);
            // Continue processing even if category resolution fails
        }
        
        // Save brand first to get ID
        brand = brandRepository.save(brand);
        
        // Add related data
        addBrandAssets(brand, response);
        addBrandColors(brand, response);
        addBrandFonts(brand, response);
        addBrandSocialLinks(brand, response);
        addBrandImages(brand, response);
        
        return brandRepository.save(brand);
    }
    
    private Brand updateExistingBrand(Brand existingBrand, BrandExtractionResponse response) {
        // Update basic information
        existingBrand.setLastExtractionTimestamp(LocalDateTime.now());
        existingBrand.setExtractionMessage(response.getMessage());
        existingBrand.setFreshnessScore(100); // Reset freshness score
        existingBrand.setNeedsUpdate(false);
        
        // Update company data if available with cleaning and new field handling
        if (response.getCompany() != null) {
            BrandExtractionResponse.CompanyData company = response.getCompany();
            if (StringUtils.hasText(company.getName())) {
                existingBrand.setName(company.getName());
            }
            if (StringUtils.hasText(company.getDescription())) {
                existingBrand.setDescription(cleanDescription(company.getDescription()));
            }
            if (StringUtils.hasText(company.getIndustry())) {
                existingBrand.setIndustry(company.getIndustry());
            }
            
            // Handle location with preference for Headquarters
            String location = StringUtils.hasText(company.getHeadquarters()) ? 
                company.getHeadquarters() : company.getLocation();
            if (StringUtils.hasText(location)) {
                existingBrand.setLocation(location);
            }
            
            if (StringUtils.hasText(company.getFounded())) {
                existingBrand.setFounded(company.getFounded());
            }
            
            // Handle company type with preference for Type field
            String companyType = StringUtils.hasText(company.getType()) ? 
                company.getType() : company.getCompanyType();
            if (StringUtils.hasText(companyType)) {
                existingBrand.setCompanyType(companyType);
            }
            
            // Handle employees with preference for CompanySize
            String employees = StringUtils.hasText(company.getCompanySize()) ? 
                company.getCompanySize() : company.getEmployees();
            if (StringUtils.hasText(employees)) {
                existingBrand.setEmployees(employees);
            }
            
            // Update website if available and cleaned
            if (StringUtils.hasText(company.getWebsite())) {
                String cleanedWebsite = cleanWebsiteUrl(company.getWebsite());
                if (StringUtils.hasText(cleanedWebsite)) {
                    existingBrand.setWebsite(cleanedWebsite);
                }
            }
            
            // Handle new fields - store as JSON strings for performance
            try {
                if (company.getSpecialties() != null && !company.getSpecialties().isEmpty()) {
                    existingBrand.setSpecialties(objectMapper.writeValueAsString(company.getSpecialties()));
                }
                if (company.getLocations() != null && !company.getLocations().isEmpty()) {
                    existingBrand.setLocations(objectMapper.writeValueAsString(company.getLocations()));
                }
            } catch (Exception e) {
                log.warn("Failed to serialize specialties/locations for brand: {}", existingBrand.getName(), e);
            }
        }
        
        // Update performance data
        if (response.getPerformance() != null) {
            existingBrand.setExtractionTimeSeconds(response.getPerformance().getExtractionTimeSeconds());
        }
        
        // Re-resolve category IDs in case industry was updated
        try {
            brandCategoryResolutionService.setCategoryIds(existingBrand);
            log.info("Category resolution updated for existing brand: '{}' - categoryId: {}, subCategoryId: {}", 
                    existingBrand.getName(), existingBrand.getCategoryId(), existingBrand.getSubCategoryId());
        } catch (Exception e) {
            log.error("Failed to resolve category IDs for existing brand: '{}' with industry: '{}'", 
                    existingBrand.getName(), existingBrand.getIndustry(), e);
            // Continue processing even if category resolution fails
        }
        
        // Clear existing related data and add new data
        // Note: Due to cascade = CascadeType.ALL and orphanRemoval = true, 
        // clearing the collections will delete the old records
        // We need to be careful with assets that might be downloading
        clearExistingDataSafely(existingBrand);
        
        // Add new related data
        addBrandAssets(existingBrand, response);
        addBrandColors(existingBrand, response);
        addBrandFonts(existingBrand, response);
        addBrandSocialLinks(existingBrand, response);
        addBrandImages(existingBrand, response);
        
        return existingBrand;
    }
    
    private void addBrandAssets(Brand brand, BrandExtractionResponse response) {
        if (response.getLogo() != null) {
            BrandExtractionResponse.LogoData logo = response.getLogo();
            
            if (StringUtils.hasText(logo.getLogo())) {
                brand.addAsset(createBrandAsset(logo.getLogo(), BrandAsset.AssetType.LOGO));
            }
            if (StringUtils.hasText(logo.getSymbol())) {
                brand.addAsset(createBrandAsset(logo.getSymbol(), BrandAsset.AssetType.SYMBOL));
            }
            if (StringUtils.hasText(logo.getIcon())) {
                brand.addAsset(createBrandAsset(logo.getIcon(), BrandAsset.AssetType.ICON));
            }
            if (StringUtils.hasText(logo.getBanner())) {
                brand.addAsset(createBrandAsset(logo.getBanner(), BrandAsset.AssetType.BANNER));
            }
            if (StringUtils.hasText(logo.getLinkedInBanner())) {
                brand.addAsset(createBrandAsset(logo.getLinkedInBanner(), BrandAsset.AssetType.LINKEDIN_BANNER));
            }
            if (StringUtils.hasText(logo.getLinkedInLogo())) {
                brand.addAsset(createBrandAsset(logo.getLinkedInLogo(), BrandAsset.AssetType.LINKEDIN_LOGO));
            }
        }
    }
    
    private BrandAsset createBrandAsset(String url, BrandAsset.AssetType type) {
        return BrandAsset.builder()
                .assetType(type)
                .originalUrl(url)
                .fileName(extractFileNameFromUrl(url))
                .downloadStatus(BrandAsset.DownloadStatus.PENDING)
                .downloadAttempts(0)
                .build();
    }
    
    private void addBrandColors(Brand brand, BrandExtractionResponse response) {
        if (response.getColors() != null) {
            // Separate direct colors from image-based colors
            List<BrandExtractionResponse.ColorData> directColors = new ArrayList<>();
            List<BrandExtractionResponse.ColorData> imageBasedColors = new ArrayList<>();
            
            response.getColors().forEach(colorData -> {
                if (colorData.getHex() != null) {
                    directColors.add(colorData);
                } else if (colorData.getWidth() != null && colorData.getHeight() != null) {
                    imageBasedColors.add(colorData);
                }
            });
            
            // Process direct colors (with hex codes)
            directColors.forEach(colorData -> {
                BrandColor color = BrandColor.builder()
                        .hexCode(colorData.getHex())
                        .rgbValue(colorData.getRgb())
                        .brightness(colorData.getBrightness())
                        .colorName(colorData.getName())
                        .usageContext(colorData.getName())
                        .build();
                brand.addColor(color);
            });
            
            // Process image-based colors (logo and banner) - create single objects
            if (!imageBasedColors.isEmpty()) {
                // Find the object with maximum width (banner colors)
                BrandExtractionResponse.ColorData bannerColorData = imageBasedColors.stream()
                        .max((c1, c2) -> Integer.compare(c1.getWidth(), c2.getWidth()))
                        .orElse(null);
                
                imageBasedColors.forEach(colorData -> {
                    if (colorData.getColors() != null && !colorData.getColors().isEmpty()) {
                        String usageContext = (colorData == bannerColorData) ? "Banner" : "Logo";
                        
                        // Concatenate all hex colors with comma separator
                        String concatenatedHex = String.join(",", colorData.getColors().stream()
                                .map(color -> color.startsWith("#") ? color : "#" + color)
                                .toArray(String[]::new));
                        
                        // Calculate average brightness from all colors
                        int averageBrightness = calculateAverageBrightness(colorData.getColors());
                        
                        // Create RGB string for the first color (representative)
                        String firstColor = colorData.getColors().get(0);
                        if (!firstColor.startsWith("#")) {
                            firstColor = "#" + firstColor;
                        }
                        String rgbValue = hexToRgb(firstColor);
                        
                        BrandColor color = BrandColor.builder()
                                .hexCode(concatenatedHex)
                                .rgbValue(rgbValue)
                                .brightness(averageBrightness)
                                .colorName(usageContext + " Colors")
                                .usageContext(usageContext)
                                .build();
                        brand.addColor(color);
                    }
                });
            }
        }
    }
    
    private String hexToRgb(String hex) {
        try {
            // Remove # if present
            hex = hex.replace("#", "");
            
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            
            return String.format("rgb(%d,%d,%d)", r, g, b);
        } catch (Exception e) {
            return "rgb(0,0,0)"; // Default to black if conversion fails
        }
    }
    
    private int calculateAverageBrightness(List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return 0;
        }
        
        int totalBrightness = 0;
        int validColors = 0;
        
        for (String color : colors) {
            try {
                String hex = color.startsWith("#") ? color.substring(1) : color;
                
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                
                // Calculate brightness using luminance formula
                int brightness = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                totalBrightness += brightness;
                validColors++;
            } catch (Exception e) {
                // Skip invalid colors
            }
        }
        
        return validColors > 0 ? totalBrightness / validColors : 0;
    }
    
    private void addBrandFonts(Brand brand, BrandExtractionResponse response) {
        if (response.getFonts() != null) {
            response.getFonts().forEach(fontData -> {
                BrandFont font = BrandFont.builder()
                        .fontName(fontData.getName())
                        .fontType(fontData.getType())
                        .fontStack(fontData.getStack())
                        .build();
                brand.addFont(font);
            });
        }
    }
    
    private void addBrandSocialLinks(Brand brand, BrandExtractionResponse response) {
        if (response.getCompany() != null && response.getCompany().getSocialLinks() != null) {
            Map<String, String> socialLinks = response.getCompany().getSocialLinks();
            
            socialLinks.forEach((platform, url) -> {
                if (StringUtils.hasText(url)) {
                    BrandSocialLink.Platform platformEnum = mapToPlatformEnum(platform);
                    BrandSocialLink socialLink = BrandSocialLink.builder()
                            .platform(platformEnum)
                            .url(url)
                            .build();
                    brand.addSocialLink(socialLink);
                }
            });
            
            // Handle LinkedIn error if present
            if (StringUtils.hasText(response.getCompany().getLinkedInError())) {
                BrandSocialLink linkedInLink = BrandSocialLink.builder()
                        .platform(BrandSocialLink.Platform.LINKEDIN)
                        .url("") // Empty URL since extraction failed
                        .extractionError(response.getCompany().getLinkedInError())
                        .build();
                brand.addSocialLink(linkedInLink);
            }
        }
    }
    
    private void addBrandImages(Brand brand, BrandExtractionResponse response) {
        if (response.getImages() != null) {
            response.getImages().forEach(imageData -> {
                BrandImage image = BrandImage.builder()
                        .sourceUrl(imageData.getSrc())
                        .altText(imageData.getAlt())
                        .fileName(extractFileNameFromUrl(imageData.getSrc()))
                        .downloadStatus(BrandImage.DownloadStatus.PENDING)
                        .downloadAttempts(0)
                        .build();
                brand.addImage(image);
            });
        }
    }
    
    private BrandSocialLink.Platform mapToPlatformEnum(String platform) {
        return switch (platform.toLowerCase()) {
            case "twitter" -> BrandSocialLink.Platform.TWITTER;
            case "linkedin" -> BrandSocialLink.Platform.LINKEDIN;
            case "facebook" -> BrandSocialLink.Platform.FACEBOOK;
            case "youtube" -> BrandSocialLink.Platform.YOUTUBE;
            case "instagram" -> BrandSocialLink.Platform.INSTAGRAM;
            case "tiktok" -> BrandSocialLink.Platform.TIKTOK;
            default -> BrandSocialLink.Platform.OTHER;
        };
    }
    
    private String extractDomainFromUrl(String url) {
        try {
            // Simple domain extraction
            String domain = url.replaceAll("^https?://", "").replaceAll("^www\\.", "").split("/")[0];
            return domain.substring(0, 1).toUpperCase() + domain.substring(1);
        } catch (Exception e) {
            return "Unknown Brand";
        }
    }
    
    private String extractFileNameFromUrl(String url) {
        try {
            String[] parts = url.split("/");
            return parts[parts.length - 1];
        } catch (Exception e) {
            return "unknown_file";
        }
    }
    
    /**
     * Clean website URL by removing extra text and formatting
     */
    private String cleanWebsiteUrl(String website) {
        if (website == null || website.trim().isEmpty()) {
            return null;
        }
        
        // Remove extra text after newlines and clean up
        String cleaned = website.split("\n")[0].trim();
        
        // Remove common suffixes that appear in LinkedIn data
        cleaned = cleaned.replaceAll("\\s*External link.*$", "");
        cleaned = cleaned.replaceAll("\\s*for\\s+.*$", "");
        
        return cleaned.trim();
    }
    
    /**
     * Clean description by removing excessive whitespace and formatting
     */
    private String cleanDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }
        
        return description
            .replaceAll("\\n\\s*\\n", "\n\n") // Replace multiple newlines with double newline
            .replaceAll("\\n\\s+", "\n") // Remove spaces after newlines
            .replaceAll("\\s+", " ") // Replace multiple spaces with single space
            .replaceAll("\\s*Website\\s*", "") // Remove standalone "Website" text
            .replaceAll("\\s*External link.*?\\s*", "") // Remove external link references
            .trim();
    }
    
    /**
     * Safely clear existing brand data, avoiding race conditions with async downloads
     */
    private void clearExistingDataSafely(Brand brand) {
        // For assets and images, we need to be careful about ongoing downloads
        // Mark downloading assets as failed to stop the download process
        brand.getAssets().forEach(asset -> {
            if (asset.getDownloadStatus() == BrandAsset.DownloadStatus.DOWNLOADING ||
                asset.getDownloadStatus() == BrandAsset.DownloadStatus.PENDING) {
                asset.setDownloadStatus(BrandAsset.DownloadStatus.FAILED);
                asset.setDownloadError("Brand data updated - download cancelled");
                log.info("Cancelled download for asset: {} (Brand update)", asset.getOriginalUrl());
            }
        });
        
        brand.getImages().forEach(image -> {
            if (image.getDownloadStatus() == BrandImage.DownloadStatus.DOWNLOADING ||
                image.getDownloadStatus() == BrandImage.DownloadStatus.PENDING) {
                image.setDownloadStatus(BrandImage.DownloadStatus.FAILED);
                image.setDownloadError("Brand data updated - download cancelled");
                log.info("Cancelled download for image: {} (Brand update)", image.getSourceUrl());
            }
        });
        
        // Now safely clear all collections
        brand.getAssets().clear();
        brand.getColors().clear();
        brand.getFonts().clear();
        brand.getSocialLinks().clear();
        brand.getImages().clear();
    }
    
    /**
     * Schedule async download after transaction commits to ensure all entities are persisted
     */
    private void scheduleAsyncDownload(Brand brand) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("Transaction committed, starting async download for brand: {} (ID: {})", 
                            brand.getName(), brand.getId());
                    fileStorageService.downloadBrandAssetsAsync(brand);
                }
            });
        } else {
            // No active transaction, start download immediately
            log.info("No active transaction, starting async download immediately for brand: {} (ID: {})", 
                    brand.getName(), brand.getId());
            fileStorageService.downloadBrandAssetsAsync(brand);
        }
    }
}