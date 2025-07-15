package com.example.jwtauthenticator.service;

import com.example.jwtauthenticator.entity.BrandCategory;
import com.example.jwtauthenticator.entity.BrandSubCategory;
import com.example.jwtauthenticator.repository.BrandCategoryRepository;
import com.example.jwtauthenticator.repository.BrandSubCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrandCategoryResolutionService {

    private final BrandCategoryRepository brandCategoryRepository;
    private final BrandSubCategoryRepository brandSubCategoryRepository;

    /**
     * DTO to hold the resolved category and subcategory IDs
     */
    public static class CategoryResolutionResult {
        private final Long categoryId;
        private final Long subCategoryId;

        public CategoryResolutionResult(Long categoryId, Long subCategoryId) {
            this.categoryId = categoryId;
            this.subCategoryId = subCategoryId;
        }

        public Long getCategoryId() {
            return categoryId;
        }

        public Long getSubCategoryId() {
            return subCategoryId;
        }
    }

    /**
     * Resolves category and subcategory IDs based on the brand's industry.
     * 
     * Logic:
     * 1. First, check if industry matches any category name (case-insensitive) in brandcategories
     * 2. If found, return categoryId and null for subCategoryId
     * 3. If not found, check if industry matches any subcategory name (case-insensitive) in brandsubcategories
     * 4. If found, return both categoryId and subCategoryId
     * 5. If no match found in either table, return null for both
     * 
     * @param industry The brand's industry string
     * @return CategoryResolutionResult containing the resolved IDs
     */
    public CategoryResolutionResult resolveCategoryIds(String industry) {
        // Return null values if industry is empty or null
        if (!StringUtils.hasText(industry)) {
            log.debug("Industry is null or empty, returning null category IDs");
            return new CategoryResolutionResult(null, null);
        }

        log.debug("Resolving category IDs for industry: {}", industry);

        // Step 1: Check if industry matches any category name in brandcategories
        Optional<BrandCategory> categoryMatch = brandCategoryRepository
                .findByCategoryNameIgnoreCaseAndIsActive(industry.trim(), true);

        if (categoryMatch.isPresent()) {
            BrandCategory category = categoryMatch.get();
            log.debug("Found matching category: {} with ID: {}", category.getCategoryName(), category.getId());
            return new CategoryResolutionResult(category.getId(), null);
        }

        // Step 2: Check if industry matches any subcategory name in brandsubcategories
        Optional<BrandSubCategory> subCategoryMatch = brandSubCategoryRepository
                .findBySubCategoryNameIgnoreCaseAndIsActive(industry.trim(), true);

        if (subCategoryMatch.isPresent()) {
            BrandSubCategory subCategory = subCategoryMatch.get();
            log.debug("Found matching subcategory: {} with ID: {} and categoryId: {}", 
                    subCategory.getSubCategoryName(), subCategory.getId(), subCategory.getCategoryId());
            return new CategoryResolutionResult(subCategory.getCategoryId(), subCategory.getId());
        }

        // Step 3: No match found in either table
        log.debug("No matching category or subcategory found for industry: {}", industry);
        return new CategoryResolutionResult(null, null);
    }

    /**
     * Convenience method to set category IDs on a brand entity based on its industry
     * 
     * @param brand The brand entity to update
     */
    public void setCategoryIds(com.example.jwtauthenticator.entity.Brand brand) {
        if (brand == null) {
            return;
        }

        CategoryResolutionResult result = resolveCategoryIds(brand.getIndustry());
        brand.setCategoryId(result.getCategoryId());
        brand.setSubCategoryId(result.getSubCategoryId());
        
        // Always set future columns to null as per requirements
        brand.setFutureColumn1(null);
        brand.setFutureColumn2(null);

        log.debug("Set categoryId: {} and subCategoryId: {} for brand: {}", 
                result.getCategoryId(), result.getSubCategoryId(), brand.getName());
    }
}