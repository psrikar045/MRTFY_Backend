package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.Brand;
import com.example.jwtauthenticator.entity.BrandAsset;
import com.example.jwtauthenticator.entity.BrandImage;
import com.example.jwtauthenticator.repository.BrandAssetRepository;
import com.example.jwtauthenticator.repository.BrandImageRepository;
import com.example.jwtauthenticator.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing brand data lifecycle, updates, and maintenance tasks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrandManagementService {
    
    private final BrandRepository brandRepository;
    private final BrandAssetRepository brandAssetRepository;
    private final BrandImageRepository brandImageRepository;
    private final FileStorageService fileStorageService;
    
    /**
     * Mark brands as needing update based on age
     */
    @Transactional
    public void markStaleDataForUpdate(int daysOld) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysOld);
        List<Brand> staleBrands = brandRepository.findBrandsOlderThan(cutoffTime);
        
        staleBrands.forEach(brand -> {
            brand.setNeedsUpdate(true);
            brand.setFreshnessScore(Math.max(0, brand.getFreshnessScore() - 20)); // Reduce freshness score
        });
        
        brandRepository.saveAll(staleBrands);
        log.info("Marked {} brands as needing update (older than {} days)", staleBrands.size(), daysOld);
    }
    
    /**
     * Get brands that need updating
     */
    public List<Brand> getBrandsNeedingUpdate(int limit) {
        return brandRepository.findBrandsNeedingUpdate(PageRequest.of(0, limit));
    }
    
    /**
     * Retry failed asset downloads
     */
    @Async
    public CompletableFuture<Void> retryFailedDownloads() {
        log.info("Starting retry of failed asset downloads");
        
        // Retry failed assets
        List<BrandAsset> failedAssets = brandAssetRepository.findFailedAssetsForRetry(3);
        for (BrandAsset asset : failedAssets) {
            try {
                fileStorageService.downloadAsset(asset);
            } catch (Exception e) {
                log.error("Retry failed for asset: {}", asset.getOriginalUrl(), e);
            }
        }
        
        // Retry failed images
        List<BrandImage> failedImages = brandImageRepository.findFailedImagesForRetry(3);
        for (BrandImage image : failedImages) {
            try {
                fileStorageService.downloadImage(image);
            } catch (Exception e) {
                log.error("Retry failed for image: {}", image.getSourceUrl(), e);
            }
        }
        
        log.info("Completed retry of failed downloads. Assets: {}, Images: {}", 
                failedAssets.size(), failedImages.size());
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Calculate and update freshness scores for all brands
     */
    @Transactional
    public void updateFreshnessScores() {
        List<Brand> allBrands = brandRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        
        allBrands.forEach(brand -> {
            int newScore = calculateFreshnessScore(brand, now);
            brand.setFreshnessScore(newScore);
            
            // Mark for update if score is too low
            if (newScore < 30) {
                brand.setNeedsUpdate(true);
            }
        });
        
        brandRepository.saveAll(allBrands);
        log.info("Updated freshness scores for {} brands", allBrands.size());
    }
    
    /**
     * Calculate freshness score based on various factors
     */
    private int calculateFreshnessScore(Brand brand, LocalDateTime now) {
        int score = 100;
        
        // Age factor
        if (brand.getLastExtractionTimestamp() != null) {
            long daysOld = java.time.Duration.between(brand.getLastExtractionTimestamp(), now).toDays();
            score -= Math.min(50, (int) (daysOld * 2)); // Reduce 2 points per day, max 50 points
        }
        
        // Asset download success rate
        long totalAssets = brand.getAssets().size() + brand.getImages().size();
        if (totalAssets > 0) {
            long completedAssets = brandAssetRepository.countCompletedAssetsByBrandId(brand.getId()) +
                                 brandImageRepository.countCompletedImagesByBrandId(brand.getId());
            double successRate = (double) completedAssets / totalAssets;
            score += (int) (successRate * 20); // Up to 20 bonus points for complete downloads
        }
        
        // Data completeness factor
        int completenessBonus = 0;
        if (brand.getDescription() != null && !brand.getDescription().trim().isEmpty()) completenessBonus += 5;
        if (brand.getIndustry() != null && !brand.getIndustry().trim().isEmpty()) completenessBonus += 5;
        if (brand.getLocation() != null && !brand.getLocation().trim().isEmpty()) completenessBonus += 5;
        if (!brand.getSocialLinks().isEmpty()) completenessBonus += 5;
        
        score += completenessBonus;
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Claim a brand by setting isBrandClaimed to true
     * 
     * @param brandId The ID of the brand to claim
     * @return true if the brand was successfully claimed, false if it was already claimed or not found
     */
    @Transactional
    public boolean claimBrand(Long brandId) {
        return brandRepository.findById(brandId)
                .map(brand -> {
                    // Only allow claiming if not already claimed
                    if (brand.getIsBrandClaimed() == null || !brand.getIsBrandClaimed()) {
                        brand.setIsBrandClaimed(true);
                        brandRepository.save(brand);
                        log.info("Brand with ID {} has been claimed", brandId);
                        return true;
                    } else {
                        log.info("Brand with ID {} is already claimed", brandId);
                        return false;
                    }
                })
                .orElseGet(() -> {
                    log.warn("Attempted to claim non-existent brand with ID {}", brandId);
                    return false;
                });
    }
    
    /**
     * Get brand statistics for monitoring
     */
    public BrandStatistics getDetailedStatistics() {
        long totalBrands = brandRepository.count();
        long needingUpdate = brandRepository.findBrandsNeedingUpdate(PageRequest.of(0, Integer.MAX_VALUE)).size();
        long pendingAssets = brandAssetRepository.findByDownloadStatus(BrandAsset.DownloadStatus.PENDING).size();
        long failedAssets = brandAssetRepository.findByDownloadStatus(BrandAsset.DownloadStatus.FAILED).size();
        long pendingImages = brandImageRepository.findByDownloadStatus(BrandImage.DownloadStatus.PENDING).size();
        long failedImages = brandImageRepository.findByDownloadStatus(BrandImage.DownloadStatus.FAILED).size();
        long claimedBrands = brandRepository.countByIsBrandClaimedTrue();
        
        return BrandStatistics.builder()
                .totalBrands(totalBrands)
                .brandsNeedingUpdate(needingUpdate)
                .pendingAssetDownloads(pendingAssets)
                .failedAssetDownloads(failedAssets)
                .pendingImageDownloads(pendingImages)
                .failedImageDownloads(failedImages)
                .claimedBrands(claimedBrands)
                .build();
    }
    
    @lombok.Builder
    @lombok.Data
    public static class BrandStatistics {
        private long totalBrands;
        private long brandsNeedingUpdate;
        private long pendingAssetDownloads;
        private long failedAssetDownloads;
        private long pendingImageDownloads;
        private long failedImageDownloads;
        private long claimedBrands;
    }
}