package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.Brand;
import com.example.jwtauthenticator.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandCategoryUpdateService {

    private final BrandRepository brandRepository;
    private final BrandCategoryResolutionService brandCategoryResolutionService;

    /**
     * Update category IDs for a specific brand by ID
     * 
     * @param brandId The ID of the brand to update
     * @return true if brand was found and updated, false otherwise
     */
    @Transactional
    public boolean updateBrandCategoryIds(Long brandId) {
        return brandRepository.findById(brandId)
                .map(brand -> {
                    brandCategoryResolutionService.setCategoryIds(brand);
                    brandRepository.save(brand);
                    log.info("Updated category IDs for brand: {} (ID: {})", brand.getName(), brand.getId());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Update category IDs for all brands in the database
     * This method processes brands in batches to avoid memory issues
     * 
     * @param batchSize The number of brands to process in each batch (default: 100)
     * @return The total number of brands updated
     */
    @Transactional
    public long updateAllBrandCategoryIds(int batchSize) {
        if (batchSize <= 0) {
            batchSize = 100;
        }

        long totalUpdated = 0;
        int pageNumber = 0;
        Page<Brand> brandPage;

        do {
            Pageable pageable = PageRequest.of(pageNumber, batchSize);
            brandPage = brandRepository.findAll(pageable);
            
            List<Brand> brands = brandPage.getContent();
            for (Brand brand : brands) {
                brandCategoryResolutionService.setCategoryIds(brand);
                totalUpdated++;
            }
            
            // Save all brands in the current batch
            brandRepository.saveAll(brands);
            
            log.info("Updated category IDs for batch {} ({} brands)", pageNumber + 1, brands.size());
            pageNumber++;
            
        } while (brandPage.hasNext());

        log.info("Completed updating category IDs for {} brands", totalUpdated);
        return totalUpdated;
    }

    /**
     * Update category IDs for all brands in the database with default batch size
     * 
     * @return The total number of brands updated
     */
    @Transactional
    public long updateAllBrandCategoryIds() {
        return updateAllBrandCategoryIds(100);
    }

    /**
     * Update category IDs for brands with a specific industry
     * 
     * @param industry The industry to filter by
     * @return The number of brands updated
     */
    @Transactional
    public long updateBrandCategoryIdsByIndustry(String industry) {
        List<Brand> brands = brandRepository.findByIndustryIgnoreCase(industry);
        
        for (Brand brand : brands) {
            brandCategoryResolutionService.setCategoryIds(brand);
        }
        
        brandRepository.saveAll(brands);
        
        log.info("Updated category IDs for {} brands with industry: {}", brands.size(), industry);
        return brands.size();
    }

    /**
     * Get statistics about category ID resolution
     * 
     * @return CategoryResolutionStats containing counts of different scenarios
     */
    public CategoryResolutionStats getCategoryResolutionStats() {
        long totalBrands = brandRepository.count();
        long brandsWithCategoryId = brandRepository.countByCategoryIdIsNotNull();
        long brandsWithSubCategoryId = brandRepository.countBySubCategoryIdIsNotNull();
        long brandsWithBothIds = brandRepository.countByCategoryIdIsNotNullAndSubCategoryIdIsNotNull();
        long brandsWithNeitherIds = brandRepository.countByCategoryIdIsNullAndSubCategoryIdIsNull();

        return new CategoryResolutionStats(
                totalBrands,
                brandsWithCategoryId,
                brandsWithSubCategoryId,
                brandsWithBothIds,
                brandsWithNeitherIds
        );
    }

    /**
     * Statistics class for category resolution
     */
    public static class CategoryResolutionStats {
        private final long totalBrands;
        private final long brandsWithCategoryId;
        private final long brandsWithSubCategoryId;
        private final long brandsWithBothIds;
        private final long brandsWithNeitherIds;

        public CategoryResolutionStats(long totalBrands, long brandsWithCategoryId, 
                                     long brandsWithSubCategoryId, long brandsWithBothIds, 
                                     long brandsWithNeitherIds) {
            this.totalBrands = totalBrands;
            this.brandsWithCategoryId = brandsWithCategoryId;
            this.brandsWithSubCategoryId = brandsWithSubCategoryId;
            this.brandsWithBothIds = brandsWithBothIds;
            this.brandsWithNeitherIds = brandsWithNeitherIds;
        }

        public long getTotalBrands() { return totalBrands; }
        public long getBrandsWithCategoryId() { return brandsWithCategoryId; }
        public long getBrandsWithSubCategoryId() { return brandsWithSubCategoryId; }
        public long getBrandsWithBothIds() { return brandsWithBothIds; }
        public long getBrandsWithNeitherIds() { return brandsWithNeitherIds; }

        @Override
        public String toString() {
            return String.format(
                "CategoryResolutionStats{totalBrands=%d, withCategoryId=%d, withSubCategoryId=%d, withBothIds=%d, withNeitherIds=%d}",
                totalBrands, brandsWithCategoryId, brandsWithSubCategoryId, brandsWithBothIds, brandsWithNeitherIds
            );
        }
    }
}